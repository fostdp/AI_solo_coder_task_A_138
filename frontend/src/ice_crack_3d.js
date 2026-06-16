import * as THREE from 'three';
import { OrbitControls } from 'three/examples/jsm/controls/OrbitControls.js';

const WATER_VERTEX_SHADER = `
  varying vec2 vUv;
  varying vec3 vWorldPos;
  varying vec3 vNormal;
  varying vec3 vViewDir;
  varying float vWaveHeight;
  uniform float uTime;
  uniform float uWaveStrength;
  uniform float uPavementLength;
  uniform float uPavementWidth;
  uniform vec3 uCameraPos;
  uniform int uQuality;

  void main() {
    vUv = uv;
    vec3 pos = position;

    float wx = pos.x * 1.8 + uTime * 1.1;
    float wz = pos.z * 1.6 + uTime * 0.9;
    float w1 = sin(wx) * cos(wz);
    float wx2 = pos.x * 3.2 - uTime * 0.7;
    float wz2 = pos.z * 2.8 + uTime * 1.3;
    float w2 = sin(wx2) * cos(wz2) * 0.55;
    float w3 = sin(pos.x * 5.0 + uTime * 1.8) * sin(pos.z * 5.0 + uTime * 1.4) * 0.25;
    float waveSum = (w1 + w2 + w3) * uWaveStrength;
    pos.y += waveSum;
    vWaveHeight = waveSum;

    vec3 worldPos = (modelMatrix * vec4(pos, 1.0)).xyz;
    vWorldPos = worldPos;

    if (uQuality == 1) {
      vec3 dx = vec3(0.01, 0.0, 0.0);
      float hL = sin((pos.x-0.01)*1.8+uTime*1.1)*cos(pos.z*1.6+uTime*0.9)
              + sin((pos.x-0.01)*3.2-uTime*0.7)*cos(pos.z*2.8+uTime*1.3)*0.55;
      float hR = sin((pos.x+0.01)*1.8+uTime*1.1)*cos(pos.z*1.6+uTime*0.9)
              + sin((pos.x+0.01)*3.2-uTime*0.7)*cos(pos.z*2.8+uTime*1.3)*0.55;
      float hD = sin(pos.x*1.8+uTime*1.1)*cos((pos.z-0.01)*1.6+uTime*0.9)
              + sin(pos.x*3.2-uTime*0.7)*cos((pos.z-0.01)*2.8+uTime*1.3)*0.55;
      float hU = sin(pos.x*1.8+uTime*1.1)*cos((pos.z+0.01)*1.6+uTime*0.9)
              + sin(pos.x*3.2-uTime*0.7)*cos((pos.z+0.01)*2.8+uTime*1.3)*0.55;
      vec3 t1 = normalize(vec3(0.02, (hR-hL)*uWaveStrength, 0.0));
      vec3 t2 = normalize(vec3(0.0, (hU-hD)*uWaveStrength, 0.02));
      vec3 perturbed = normalize(normalMatrix * normalize(cross(t2, t1)));
      vNormal = perturbed;
    } else {
      vNormal = normalize(normalMatrix * normal);
    }

    vViewDir = normalize(uCameraPos - worldPos);
    gl_Position = projectionMatrix * modelViewMatrix * vec4(pos, 1.0);
  }
`;

const WATER_FRAGMENT_SHADER = `
  varying vec2 vUv;
  varying vec3 vWorldPos;
  varying vec3 vNormal;
  varying vec3 vViewDir;
  varying float vWaveHeight;
  uniform float uTime;
  uniform vec3 uWaterColor;
  uniform vec3 uSkyTop;
  uniform vec3 uSkyBottom;
  uniform vec3 uPavementColor;
  uniform vec3 uLightDir;
  uniform float uOpacity;
  uniform int uQuality;

  #ifdef GL_FRAGMENT_PRECISION_HIGH
    #define HIGH_PRECISION 1
  #else
    #define HIGH_PRECISION 0
  #endif

  vec3 getSimpleReflection(vec3 R) {
    if (R.y >= 0.0) {
      float t = clamp(R.y * 1.1, 0.0, 1.0);
      vec3 sky = mix(uSkyBottom, uSkyTop, t);
      float sun = pow(max(0.0, dot(normalize(R), normalize(uLightDir))), 128.0);
      sky += vec3(1.0, 0.95, 0.8) * sun * 0.6;
      return sky;
    } else {
      float t = clamp(-R.y * 1.5, 0.0, 1.0);
      vec3 deep = uPavementColor * 0.35;
      return mix(uPavementColor * 0.85, deep, t);
    }
  }

  void main() {
    vec3 N = normalize(vNormal);
    vec3 V = normalize(vViewDir);
    float NdotV = max(0.001, dot(N, V));
    vec3 L = normalize(uLightDir);
    float NdotL = max(0.0, dot(N, L));
    vec3 H = normalize(L + V);
    float NdotH = max(0.0, dot(N, H));

    float refractiveIdx = 1.33;
    float F0 = pow((1.0 - refractiveIdx) / (1.0 + refractiveIdx), 2.0);
    float fresnel = F0 + (1.0 - F0) * pow(1.0 - NdotV, 5.0);

    vec3 baseColor = uWaterColor;
    float depthFactor = 1.0 - exp(-3.5 * (vWaveHeight + 0.05));
    baseColor = mix(uWaterColor * 0.85, uWaterColor * 1.15, clamp(depthFactor, 0.0, 1.0));

    vec3 diffuse = baseColor * (0.35 + 0.65 * NdotL);
    float spec = pow(NdotH, 64.0) * (HIGH_PRECISION || uQuality == 1 ? 1.0 : 0.0);
    vec3 specular = vec3(1.0, 0.98, 0.92) * spec * 0.5;

    vec3 R = reflect(-V, N);
    vec3 reflectionColor = getSimpleReflection(R);

    vec3 finalColor = mix(diffuse, reflectionColor, fresnel * 0.85) + specular;

    float edgeFade = 1.0 - pow(1.0 - NdotV, 3.0);
    float alpha = uOpacity * mix(0.55, 0.75, edgeFade);

    gl_FragColor = vec4(finalColor, alpha);
  }
`;

function detectIsMobile() {
  if (typeof navigator === 'undefined') return false;
  const ua = navigator.userAgent || '';
  return /Android|webOS|iPhone|iPad|iPod|BlackBerry|IEMobile|Opera Mini|Mobile/i.test(ua)
    || (navigator.maxTouchPoints && navigator.maxTouchPoints > 2 && window.innerWidth < 900);
}

export class IceCrackScene {
  constructor(container) {
    this.container = container;
    this.isMobile = detectIsMobile();

    this.scene = new THREE.Scene();
    this.scene.fog = new THREE.Fog(0x0d1117, 15, 50);

    const aspect = container.clientWidth / container.clientHeight;
    this.camera = new THREE.PerspectiveCamera(45, aspect, 0.1, 100);
    this.camera.position.set(8, 10, 8);
    this.camera.lookAt(0, 0, 0);

    this.renderer = new THREE.WebGLRenderer({ antialias: !this.isMobile, alpha: false });
    const pixelRatio = this.isMobile
      ? Math.min(window.devicePixelRatio || 1, 1.5)
      : Math.min(window.devicePixelRatio || 1, 2);
    this.renderer.setPixelRatio(pixelRatio);
    this.renderer.setSize(container.clientWidth, container.clientHeight);
    if (!this.isMobile) {
      this.renderer.shadowMap.enabled = true;
      this.renderer.shadowMap.type = THREE.PCFSoftShadowMap;
    }
    container.appendChild(this.renderer.domElement);

    this.controls = new OrbitControls(this.camera, this.renderer.domElement);
    this.controls.enableDamping = true;
    this.controls.dampingFactor = 0.08;

    const ambientLight = new THREE.AmbientLight(0x5ba88c, this.isMobile ? 0.55 : 0.4);
    this.scene.add(ambientLight);

    const hemiLight = new THREE.HemisphereLight(0x9fb8d4, 0x3a3530, this.isMobile ? 0.45 : 0.35);
    this.scene.add(hemiLight);

    const directionalLight = new THREE.DirectionalLight(0xffffff, this.isMobile ? 0.9 : 0.8);
    directionalLight.position.set(5, 10, 5);
    if (!this.isMobile) {
      directionalLight.castShadow = true;
      directionalLight.shadow.mapSize.width = 1024;
      directionalLight.shadow.mapSize.height = 1024;
      directionalLight.shadow.camera.near = 0.5;
      directionalLight.shadow.camera.far = 50;
      directionalLight.shadow.camera.left = -12;
      directionalLight.shadow.camera.right = 12;
      directionalLight.shadow.camera.top = 12;
      directionalLight.shadow.camera.bottom = -12;
    }
    this.scene.add(directionalLight);
    this.lightDir = new THREE.Vector3(5, 10, 5).normalize();

    const groundGeometry = new THREE.PlaneGeometry(24, 24);
    const groundMaterial = new THREE.MeshStandardMaterial({
      color: 0x3a3530,
      roughness: 0.95,
      metalness: 0
    });
    const ground = new THREE.Mesh(groundGeometry, groundMaterial);
    ground.rotation.x = -Math.PI / 2;
    ground.position.y = -0.5;
    if (!this.isMobile) ground.receiveShadow = true;
    this.scene.add(ground);

    this.crackGroup = new THREE.Group();
    this.waterGroup = new THREE.Group();
    this.pavementGroup = new THREE.Group();
    this.scene.add(this.crackGroup);
    this.scene.add(this.waterGroup);
    this.scene.add(this.pavementGroup);

    this.waterMesh = null;
    this.waterUniforms = null;
    this.waterBaseDepth = null;
    this.pavementLength = 10;
    this.pavementWidth = 10;
    this.crackSegments = [];
    this.crackLines = [];

    this._onResize = this.onResize.bind(this);
    window.addEventListener('resize', this._onResize);

    this.animate();
  }

  initPavement(length, width) {
    this.pavementLength = length;
    this.pavementWidth = width;

    const geometry = new THREE.BoxGeometry(length, 0.3, width);
    const material = new THREE.MeshStandardMaterial({
      color: 0x8a7e6d,
      roughness: this.isMobile ? 1.0 : 0.9,
      metalness: 0
    });
    const pavement = new THREE.Mesh(geometry, material);
    pavement.position.y = -0.15;
    if (!this.isMobile) pavement.receiveShadow = true;
    this.pavementGroup.add(pavement);

    const gridMaterial = new THREE.LineBasicMaterial({ color: 0x6a6055 });
    const halfLength = length / 2;
    const halfWidth = width / 2;
    const gridPoints = [];
    const step = 1;

    for (let x = -halfLength; x <= halfLength + 0.001; x += step) {
      gridPoints.push(new THREE.Vector3(x, 0.001, -halfWidth));
      gridPoints.push(new THREE.Vector3(x, 0.001, halfWidth));
    }
    for (let z = -halfWidth; z <= halfWidth + 0.001; z += step) {
      gridPoints.push(new THREE.Vector3(-halfLength, 0.001, z));
      gridPoints.push(new THREE.Vector3(halfLength, 0.001, z));
    }

    const gridGeometry = new THREE.BufferGeometry().setFromPoints(gridPoints);
    const gridLines = new THREE.LineSegments(gridGeometry, gridMaterial);
    this.pavementGroup.add(gridLines);
  }

  generateIceCracks(pattern) {
    const parsed = typeof pattern === 'string' ? JSON.parse(pattern) : pattern;
    const seed = parsed.seed || 42;
    const segments = parsed.segments || 5;
    const irregularity = parsed.irregularity || 0.5;

    const rng = this._seededRandom(seed);

    const length = this.pavementLength;
    const width = this.pavementWidth;
    const halfLength = length / 2;
    const halfWidth = width / 2;

    this.crackSegments = [];
    this.crackLines = [];

    const numSeedPoints = Math.max(1, Math.floor(segments / 2));

    for (let i = 0; i < numSeedPoints; i++) {
      const cx = (rng() - 0.5) * length;
      const cz = (rng() - 0.5) * width;

      const numCracks = 3 + Math.floor(rng() * 4);

      for (let c = 0; c < numCracks; c++) {
        let angle = rng() * Math.PI * 2;
        let x = cx;
        let z = cz;

        const numSegments = 3 + Math.floor(rng() * 4);

        for (let s = 0; s < numSegments; s++) {
          const segLength = 0.3 + rng() * 1.2;
          const jitter = (rng() - 0.5) * irregularity * 0.5;
          angle += jitter;

          const nx = x + Math.cos(angle) * segLength;
          const nz = z + Math.sin(angle) * segLength;

          const cx1 = Math.max(-halfLength, Math.min(halfLength, x));
          const cz1 = Math.max(-halfWidth, Math.min(halfWidth, z));
          const cx2 = Math.max(-halfLength, Math.min(halfLength, nx));
          const cz2 = Math.max(-halfWidth, Math.min(halfWidth, nz));

          this.crackSegments.push({ x1: cx1, z1: cz1, x2: cx2, z2: cz2 });

          const points = [
            new THREE.Vector3(cx1, 0.01, cz1),
            new THREE.Vector3(cx2, 0.01, cz2)
          ];
          const geometry = new THREE.BufferGeometry().setFromPoints(points);
          const material = new THREE.LineBasicMaterial({ color: 0x2a2520 });
          const line = new THREE.Line(geometry, material);
          this.crackGroup.add(line);
          this.crackLines.push(line);

          if (rng() < 0.3) {
            const branchAngle = angle + (rng() - 0.5) * Math.PI * 0.8;
            const branchLength = 0.3 + rng() * 0.8;
            const bx = nx + Math.cos(branchAngle) * branchLength;
            const bz = nz + Math.sin(branchAngle) * branchLength;

            const bx1 = Math.max(-halfLength, Math.min(halfLength, nx));
            const bz1 = Math.max(-halfWidth, Math.min(halfWidth, nz));
            const bx2 = Math.max(-halfLength, Math.min(halfLength, bx));
            const bz2 = Math.max(-halfWidth, Math.min(halfWidth, bz));

            this.crackSegments.push({ x1: bx1, z1: bz1, x2: bx2, z2: bz2 });

            const branchPoints = [
              new THREE.Vector3(bx1, 0.01, bz1),
              new THREE.Vector3(bx2, 0.01, bz2)
            ];
            const branchGeometry = new THREE.BufferGeometry().setFromPoints(branchPoints);
            const branchMaterial = new THREE.LineBasicMaterial({ color: 0x2a2520 });
            const branchLine = new THREE.Line(branchGeometry, branchMaterial);
            this.crackGroup.add(branchLine);
            this.crackLines.push(branchLine);
          }

          x = nx;
          z = nz;
        }
      }
    }

    return this.crackSegments;
  }

  _createWaterMesh(segments) {
    const seg = this.isMobile ? Math.min(segments, 16) : Math.min(segments, 32);
    const geometry = new THREE.PlaneGeometry(this.pavementLength, this.pavementWidth, seg, seg);
    geometry.rotateX(-Math.PI / 2);

    this.waterUniforms = {
      uTime: { value: 0 },
      uWaveStrength: { value: 0.02 },
      uPavementLength: { value: this.pavementLength },
      uPavementWidth: { value: this.pavementWidth },
      uCameraPos: { value: new THREE.Vector3() },
      uWaterColor: { value: new THREE.Color(0x4696dc) },
      uSkyTop: { value: new THREE.Color(0x8bb8d8) },
      uSkyBottom: { value: new THREE.Color(0xb8c5b0) },
      uPavementColor: { value: new THREE.Color(0x7a6e5d) },
      uLightDir: { value: new THREE.Vector3().copy(this.lightDir) },
      uOpacity: { value: 0.65 },
      uQuality: { value: this.isMobile ? 0 : 1 }
    };

    const material = new THREE.ShaderMaterial({
      vertexShader: WATER_VERTEX_SHADER,
      fragmentShader: WATER_FRAGMENT_SHADER,
      uniforms: this.waterUniforms,
      transparent: true,
      side: THREE.DoubleSide,
      depthWrite: false,
      precision: this.isMobile ? 'mediump' : 'highp'
    });

    this.waterMesh = new THREE.Mesh(geometry, material);
    this.waterGroup.add(this.waterMesh);
  }

  updateWaterSurface(gridData, time) {
    if (gridData) {
      const rows = gridData.length;
      const cols = gridData[0].length;
      const segs = this.isMobile ? 16 : Math.min(32, Math.max(cols, rows));

      if (this.waterMesh) {
        this.waterGroup.remove(this.waterMesh);
        this.waterMesh.geometry.dispose();
        this.waterMesh.material.dispose();
        this.waterMesh = null;
        this.waterUniforms = null;
      }

      this._createWaterMesh(segs);

      const positions = this.waterMesh.geometry.attributes.position;
      const count = positions.count;
      const gw = this.pavementWidth;
      const gl = this.pavementLength;
      this.waterBaseDepth = new Float32Array(count);

      for (let i = 0; i < count; i++) {
        const x = positions.getX(i);
        const z = positions.getZ(i);
        const u = (x + gl / 2) / gl;
        const v = (z + gw / 2) / gw;
        const ri = Math.max(0, Math.min(rows - 1, Math.floor(v * rows)));
        const ci = Math.max(0, Math.min(cols - 1, Math.floor(u * cols)));
        const depth = gridData[ri][ci] * 2.5;
        this.waterBaseDepth[i] = depth;
        positions.setY(i, depth);
      }
      positions.needsUpdate = true;
      this.waterMesh.geometry.computeVertexNormals();

      if (this.waterUniforms) {
        this.waterUniforms.uWaveStrength.value = 0.015;
      }
    } else if (time !== undefined && time !== null) {
      if (!this.waterMesh) {
        this._createWaterMesh(this.isMobile ? 16 : 32);
      }
      if (this.waterUniforms) {
        this.waterUniforms.uWaveStrength.value = 0.02;
      }
    }
  }

  highlightCracks(segmentIndices) {
    for (const line of this.crackLines) {
      line.material.color.set(0x2a2520);
    }
    for (const idx of segmentIndices) {
      if (idx >= 0 && idx < this.crackLines.length) {
        this.crackLines[idx].material.color.set(0x5ba88c);
      }
    }
  }

  animate() {
    this._animationId = requestAnimationFrame(() => this.animate());

    this.controls.update();

    if (this.waterMesh && this.waterUniforms) {
      const now = performance.now() / 1000;
      this.waterUniforms.uTime.value = now;
      this.waterUniforms.uCameraPos.value.copy(this.camera.position);

      if (this.waterBaseDepth) {
        const positions = this.waterMesh.geometry.attributes.position;
        const count = positions.count;
        for (let i = 0; i < count; i++) {
          const x = positions.getX(i);
          const z = positions.getZ(i);
          const wave = 0.02 * (Math.sin(x * 2 + now) * Math.cos(z * 2 + now * 0.7));
          positions.setY(i, this.waterBaseDepth[i] + wave);
        }
        positions.needsUpdate = true;
      }
    }

    this.renderer.render(this.scene, this.camera);
  }

  clearScene() {
    for (let i = this.crackGroup.children.length - 1; i >= 0; i--) {
      const child = this.crackGroup.children[i];
      this.crackGroup.remove(child);
      if (child.geometry) child.geometry.dispose();
      if (child.material) child.material.dispose();
    }
    for (let i = this.waterGroup.children.length - 1; i >= 0; i--) {
      const child = this.waterGroup.children[i];
      this.waterGroup.remove(child);
      if (child.geometry) child.geometry.dispose();
      if (child.material) child.material.dispose();
    }
    for (let i = this.pavementGroup.children.length - 1; i >= 0; i--) {
      const child = this.pavementGroup.children[i];
      this.pavementGroup.remove(child);
      if (child.geometry) child.geometry.dispose();
      if (child.material) child.material.dispose();
    }

    this.waterMesh = null;
    this.waterUniforms = null;
    this.waterBaseDepth = null;
    this.crackSegments = [];
    this.crackLines = [];
  }

  dispose() {
    window.removeEventListener('resize', this._onResize);

    if (this._animationId) {
      cancelAnimationFrame(this._animationId);
    }

    this.clearScene();

    this.renderer.dispose();
    this.container.removeChild(this.renderer.domElement);
  }

  onResize() {
    const width = this.container.clientWidth;
    const height = this.container.clientHeight;
    this.camera.aspect = width / height;
    this.camera.updateProjectionMatrix();
    this.renderer.setSize(width, height);
  }

  _seededRandom(seed) {
    let s = seed | 0;
    return function () {
      s = (s * 16807) % 2147483647;
      return (s - 1) / 2147483646;
    };
  }
}
