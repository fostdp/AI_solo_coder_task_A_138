import * as THREE from 'three';
import { OrbitControls } from 'three/examples/jsm/controls/OrbitControls.js';

export class IceCrackScene {
  constructor(container) {
    this.container = container;

    this.scene = new THREE.Scene();
    this.scene.fog = new THREE.Fog(0x0d1117, 15, 50);

    this.camera = new THREE.PerspectiveCamera(45, container.clientWidth / container.clientHeight, 0.1, 100);
    this.camera.position.set(8, 10, 8);
    this.camera.lookAt(0, 0, 0);

    this.renderer = new THREE.WebGLRenderer({ antialias: true });
    this.renderer.setSize(container.clientWidth, container.clientHeight);
    this.renderer.setPixelRatio(window.devicePixelRatio);
    this.renderer.shadowMap.enabled = true;
    container.appendChild(this.renderer.domElement);

    this.controls = new OrbitControls(this.camera, this.renderer.domElement);

    const ambientLight = new THREE.AmbientLight(0x5ba88c, 0.4);
    this.scene.add(ambientLight);

    const directionalLight = new THREE.DirectionalLight(0xffffff, 0.8);
    directionalLight.position.set(5, 10, 5);
    directionalLight.castShadow = true;
    this.scene.add(directionalLight);

    const groundGeometry = new THREE.PlaneGeometry(20, 20);
    const groundMaterial = new THREE.MeshStandardMaterial({ color: 0x3a3530 });
    const ground = new THREE.Mesh(groundGeometry, groundMaterial);
    ground.rotation.x = -Math.PI / 2;
    ground.receiveShadow = true;
    this.scene.add(ground);

    this.crackGroup = new THREE.Group();
    this.waterGroup = new THREE.Group();
    this.pavementGroup = new THREE.Group();
    this.scene.add(this.crackGroup);
    this.scene.add(this.waterGroup);
    this.scene.add(this.pavementGroup);

    this.waterMesh = null;
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
    const material = new THREE.MeshStandardMaterial({ color: 0x8a7e6d, roughness: 0.9 });
    const pavement = new THREE.Mesh(geometry, material);
    pavement.position.y = -0.15;
    pavement.receiveShadow = true;
    this.pavementGroup.add(pavement);

    const gridMaterial = new THREE.LineBasicMaterial({ color: 0x6a6055 });
    const halfLength = length / 2;
    const halfWidth = width / 2;
    const gridPoints = [];
    const step = 1;

    for (let x = -halfLength; x <= halfLength; x += step) {
      gridPoints.push(new THREE.Vector3(x, 0.001, -halfWidth));
      gridPoints.push(new THREE.Vector3(x, 0.001, halfWidth));
    }
    for (let z = -halfWidth; z <= halfWidth; z += step) {
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
    const type = parsed.type || 'radial';
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
          const material = new THREE.LineBasicMaterial({ color: 0x2a2520, linewidth: 1 });
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
            const branchMaterial = new THREE.LineBasicMaterial({ color: 0x2a2520, linewidth: 1 });
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

  updateWaterSurface(gridData, time) {
    if (gridData) {
      const rows = gridData.length;
      const cols = gridData[0].length;

      if (this.waterMesh) {
        this.waterGroup.remove(this.waterMesh);
        this.waterMesh.geometry.dispose();
        this.waterMesh.material.dispose();
      }

      const geometry = new THREE.PlaneGeometry(this.pavementLength, this.pavementWidth, cols - 1, rows - 1);
      geometry.rotateX(-Math.PI / 2);

      const positions = geometry.attributes.position;
      for (let i = 0; i < positions.count; i++) {
        const row = Math.floor(i / cols);
        const col = i % cols;
        if (row < rows && col < cols) {
          positions.setY(i, gridData[row][col] * 2);
        }
      }
      positions.needsUpdate = true;

      const material = new THREE.MeshPhysicalMaterial({
        color: 0x4696dc,
        transparent: true,
        opacity: 0.6,
        roughness: 0.1,
        metalness: 0.2
      });

      this.waterMesh = new THREE.Mesh(geometry, material);
      this.waterGroup.add(this.waterMesh);
    } else if (time !== undefined && time !== null) {
      if (!this.waterMesh) {
        const geometry = new THREE.PlaneGeometry(this.pavementLength, this.pavementWidth, 32, 32);
        geometry.rotateX(-Math.PI / 2);

        const material = new THREE.MeshPhysicalMaterial({
          color: 0x4696dc,
          transparent: true,
          opacity: 0.6,
          roughness: 0.1,
          metalness: 0.2
        });

        this.waterMesh = new THREE.Mesh(geometry, material);
        this.waterGroup.add(this.waterMesh);
      }

      const positions = this.waterMesh.geometry.attributes.position;
      for (let i = 0; i < positions.count; i++) {
        const x = positions.getX(i);
        const z = positions.getZ(i);
        positions.setY(i, 0.02 * Math.sin(x * 2 + time) * Math.cos(z * 2 + time * 0.7));
      }
      positions.needsUpdate = true;
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

    if (this.waterMesh) {
      const time = performance.now() / 1000;
      const positions = this.waterMesh.geometry.attributes.position;
      for (let i = 0; i < positions.count; i++) {
        const x = positions.getX(i);
        const z = positions.getZ(i);
        positions.setY(i, 0.02 * Math.sin(x * 2 + time) * Math.cos(z * 2 + time * 0.7));
      }
      positions.needsUpdate = true;
    }

    this.renderer.render(this.scene, this.camera);
  }

  clearScene() {
    while (this.crackGroup.children.length > 0) {
      const child = this.crackGroup.children[0];
      this.crackGroup.remove(child);
      if (child.geometry) child.geometry.dispose();
      if (child.material) child.material.dispose();
    }

    while (this.waterGroup.children.length > 0) {
      const child = this.waterGroup.children[0];
      this.waterGroup.remove(child);
      if (child.geometry) child.geometry.dispose();
      if (child.material) child.material.dispose();
    }

    while (this.pavementGroup.children.length > 0) {
      const child = this.pavementGroup.children[0];
      this.pavementGroup.remove(child);
      if (child.geometry) child.geometry.dispose();
      if (child.material) child.material.dispose();
    }

    this.waterMesh = null;
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
    let s = seed;
    return function () {
      s = (s * 16807) % 2147483647;
      return (s - 1) / 2147483646;
    };
  }
}
