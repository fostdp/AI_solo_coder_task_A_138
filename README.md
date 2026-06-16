# 古代园林铺地冰裂纹美学量化与排水性能仿真系统

> 苏州园林冰裂纹铺地综合研究平台 — 融合 **美学量化分析** + **水动力排水仿真** + **物联网传感器监测** 的全栈应用系统。

## 目录

- [系统架构](#系统架构)
- [技术栈](#技术栈)
- [快速部署](#快速部署)
  - [Docker Compose 一键部署](#docker-compose-一键部署)
  - [本地开发环境](#本地开发环境)
- [模块说明](#模块说明)
  - [后端 Java 模块](#后端-java-模块)
  - [前端模块](#前端模块)
  - [模拟器模块](#模拟器模块)
- [传感器模拟器用法](#传感器模拟器用法)
- [监控与运维](#监控与运维)
- [API 接口](#api-接口)
- [配置参数](#配置参数)

---

## 系统架构

```
┌───────────────────────────────────────────────────────────────────────┐
│                         前端 (Nginx + Vue/Three.js)                     │
│  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐  │
│  │  三维渲染    │  │  美学面板    │  │  告警面板    │  │  仿真面板    │  │
│  │ ice_crack_3d│  │aesthetics_p │  │  (WebSocket)│  │   (Canvas)  │  │
│  └──────┬──────┘  └──────┬──────┘  └──────▲──────┘  └──────▲──────┘  │
└─────────┼────────────────┼─────────────────┼──────────────────┼────────┘
          │  HTTP/REST     │  HTTP/REST      │ WebSocket        │
┌─────────▼────────────────▼─────────────────▼──────────────────▼────────┐
│                         SpringBoot 后端                                   │
│                                                                          │
│  ┌──────────────┐   Spring Events     ┌──────────────────┐              │
│  │ dtu_receiver │ ──────────────────→ │    alarm_ws      │              │
│  │ (传感器采集) │   (事件解耦)        │  (告警评估+推送)  │              │
│  └──────┬───────┘                     └──────────────────┘              │
│         │                                                                 │
│         ↓ 触发                                                             │
│  ┌─────────────────┐   Spring Events    ┌──────────────────┐            │
│  │drainage_simulator│ ────────────────→ │ aesthetics_analyzer│          │
│  │  (浅水方程仿真)  │   (事件解耦)       │ (分形维数/视觉熵) │            │
│  └─────────────────┘                    └──────────────────┘            │
│                                                                          │
│  ┌──────────────────────────────────────────────────────────┐            │
│  │  Actuator / Prometheus  (健康检查 + 指标采集)              │            │
│  └──────────────────────────────────────────────────────────┘            │
└────────────────────────────────┬─────────────────────────────────────────┘
                                 │ JPA / JDBC
                    ┌────────────▼────────────┐
                    │     PostgreSQL 16       │
                    │  (JSONB + GIN索引)       │
                    └──────────────────────────┘
                                 ▲
                                 │ MQTT (可选)
                    ┌────────────▼────────────┐
                    │   Eclipse Mosquitto     │
                    │     MQTT Broker          │
                    └──────────────────────────┘
                                 ▲
                                 │
                    ┌────────────────────────┐
                    │  铺地传感器模拟器       │
                    │  (HTTP + MQTT 双通道)   │
                    └─────────────────────────┘
```

### 核心数据流

1. **传感器数据采集**：模拟器/真实设备通过 HTTP 或 MQTT 将读数上报到 `dtu_receiver` 模块
2. **事件发布**：数据入库后发布 `SensorDataReceivedEvent`，其他模块可监听
3. **排水仿真**：`drainage_simulator` 基于浅水方程 (Lax-Friedrichs 有限体积法) 计算积水消退，完成后发布 `SimulationCompletedEvent`
4. **告警评估**：`alarm_ws` 监听仿真完成事件，根据消退时间阈值判断是否触发告警，并通过 WebSocket 推送前端
5. **美学分析**：`aesthetics_analyzer` 计算分形维数（DDA盒计数+周期延拓无偏估计）、信息熵、视觉复杂度

---

## 技术栈

| 层 | 技术 |
|---|---|
| **前端** | Vite + Three.js (WebGL) + Canvas 2D + 自定义 ShaderMaterial (简化SSR) |
| **后端** | Java 17 + SpringBoot 3.2 + Spring Data JPA + Spring WebSocket + Spring Integration (MQTT) |
| **数据库** | PostgreSQL 16 + JSONB + 复合索引 |
| **消息队列** | Eclipse Mosquitto (MQTT 3.1.1) |
| **监控** | Spring Actuator + Micrometer + Prometheus |
| **部署** | Docker 多阶段构建 + Docker Compose + Nginx (gzip/brotli) |
| **算法** | 浅水方程 SWE + Lax-Friedrichs 通量、DDA盒计数分形维 + Brown无偏修正、加权WLS回归 |

---

## 快速部署

### Docker Compose 一键部署

#### 前置要求
- Docker 24+
- Docker Compose v2+
- 至少 4GB 内存

#### 启动命令

```bash
git clone <仓库地址>
cd ice-crack-pavement

# 一键启动全部服务
docker compose up -d

# 查看服务状态
docker compose ps

# 查看日志
docker compose logs -f backend
```

#### 启动后访问

| 服务 | 地址 | 说明 |
|---|---|---|
| 前端界面 | http://localhost/ | 主界面（Nginx 反代 + gzip 压缩） |
| 后端 API | http://localhost:8080/api | 或通过 nginx 的 /api 前缀 |
| Actuator 健康 | http://localhost:8080/actuator/health | 健康检查 |
| Prometheus | http://localhost:9090 | 指标监控 |
| MQTT Broker | localhost:1883 | MQTT 端口 |
| MQTT WS | localhost:9001 | MQTT WebSocket 端口 |

#### 停止服务

```bash
# 停止并保留数据
docker compose down

# 停止并清除所有数据（谨慎！）
docker compose down -v
```

### 本地开发环境

#### 后端

```bash
cd backend

# 编译
mvn clean compile

# 运行（需本地 PostgreSQL）
mvn spring-boot:run
```

#### 前端

```bash
cd frontend

# 安装依赖
npm install

# 开发模式 (热更新)
npm run dev

# 生产构建 (gzip + brotli)
npm run build
```

#### 模拟器

```bash
cd simulator

# 安装依赖
pip install -r requirements.txt

# 干跑（不发请求，只打印数据）
python sensor_simulator.py --dry-run

# 连接本地后端
python sensor_simulator.py --interval 30 --simulate --aesthetic
```

---

## 模块说明

### 后端 Java 模块

采用**模块包结构 + Spring Events 解耦**，每个模块可独立演进。

#### 1. `common/` 公共模块
- [Pavement.java](file:///d:/SOLO-2/AI_solo_coder_task_A_138/backend/src/main/java/com/garden/icecrack/common/entity/Pavement.java) — 铺地主实体
- [PavementRepository.java](file:///d:/SOLO-2/AI_solo_coder_task_A_138/backend/src/main/java/com/garden/icecrack/common/repository/PavementRepository.java)
- [PavementController.java](file:///d:/SOLO-2/AI_solo_coder_task_A_138/backend/src/main/java/com/garden/icecrack/common/controller/PavementController.java)
- **事件类**：
  - [SensorDataReceivedEvent.java](file:///d:/SOLO-2/AI_solo_coder_task_A_138/backend/src/main/java/com/garden/icecrack/common/event/SensorDataReceivedEvent.java) — 传感器数据到达事件
  - [SimulationCompletedEvent.java](file:///d:/SOLO-2/AI_solo_coder_task_A_138/backend/src/main/java/com/garden/icecrack/common/event/SimulationCompletedEvent.java) — 仿真完成事件

#### 2. `dtu_receiver/` 传感器采集模块
- 职责：接收并校验传感器数据，持久化到 PostgreSQL
- 入口：`POST /api/sensor-data`
- 发布事件：`SensorDataReceivedEvent`
- 支持 MQTT 被动接收（`mqtt.enabled=true` 时启用）

#### 3. `drainage_simulator/` 排水仿真模块
- 职责：基于浅水方程 + Lax-Friedrichs 有限体积法的排水仿真
- 入口：`POST /api/simulation/run`
- 配置：`drainage.*` YAML 配置，对应 [DrainageProperties.java](file:///d:/SOLO-2/AI_solo_coder_task_A_138/backend/src/main/java/com/garden/icecrack/drainage_simulator/config/DrainageProperties.java)
- 发布事件：`SimulationCompletedEvent`

#### 4. `aesthetics_analyzer/` 美学量化模块
- 职责：分形维数计算（DDA盒计数 + 周期延拓 + Brown修正 + WLS回归）、信息熵、视觉复杂度
- 入口：`POST /api/aesthetic/analyze/{pavementId}`
- 配置：`aesthetic.*` YAML 配置，对应 [AestheticProperties.java](file:///d:/SOLO-2/AI_solo_coder_task_A_138/backend/src/main/java/com/garden/icecrack/aesthetics_analyzer/config/AestheticProperties.java)

#### 5. `alarm_ws/` 告警 WebSocket 模块
- 职责：监听仿真完成事件，评估是否触发告警，通过 WebSocket 推送
- 入口：WebSocket `/ws/alerts` + REST `/api/alerts/*`
- 监听器：[DrainageAlertListener.java](file:///d:/SOLO-2/AI_solo_coder_task_A_138/backend/src/main/java/com/garden/icecrack/alarm_ws/listener/DrainageAlertListener.java)

### 前端模块

| 文件 | 职责 |
|---|---|
| [ice_crack_3d.js](file:///d:/SOLO-2/AI_solo_coder_task_A_138/frontend/src/ice_crack_3d.js) | Three.js 三维渲染：铺地模型、冰裂纹线条、ShaderMaterial 动态水面（简化SSR）、移动端分级 |
| [aesthetics_panel.js](file:///d:/SOLO-2/AI_solo_coder_task_A_138/frontend/src/aesthetics_panel.js) | 美学面板：8 项指标卡 + 分形图绘制 |
| [main.js](file:///d:/SOLO-2/AI_solo_coder_task_A_138/frontend/src/main.js) | 编排层：DOM 交互、事件绑定、模块协作 |
| [charts.js](file:///d:/SOLO-2/AI_solo_coder_task_A_138/frontend/src/charts.js) | Canvas 2D 折线图 + 柱状图 |
| [api.js](file:///d:/SOLO-2/AI_solo_coder_task_A_138/frontend/src/api.js) | REST API 封装 |
| [websocket.js](file:///d:/SOLO-2/AI_solo_coder_task_A_138/frontend/src/websocket.js) | WebSocket 自动重连 |

### 模拟器模块

[传感器模拟器](file:///d:/SOLO-2/AI_solo_coder_task_A_138/simulator/sensor_simulator.py) 模拟真实铺地传感器的数据采集行为，支持：
- 6 种天气状态机随机转移
- 每铺地独立积水状态追踪
- MQTT + HTTP 双通道上报
- 降雨强度 / 裂缝宽度 参数化注入
- 仿真 + 美学分析触发

---

## 传感器模拟器用法

### 命令行参数

| 参数 | 环境变量 | 默认值 | 说明 |
|---|---|---|---|
| `--interval` | `SIM_INTERVAL` | 60 | 采集间隔（秒） |
| `--pavements` | `PAVEMENTS` | 5 | 模拟铺地数量 |
| `--simulate` | `RUN_SIMULATION` | false | 同时触发排水仿真 |
| `--aesthetic` | `RUN_AESTHETIC` | false | 同时触发美学分析（每 10 轮一次） |
| `--dry-run` | - | false | 只打印数据，不发请求 |
| `--mqtt` | `USE_MQTT` | false | 通过 MQTT 发布数据 |
| `--rainfall` | `RAINFALL_MM` | 随机 | 固定降雨强度 (mm/h)，覆盖天气模拟 |
| `--crack-width` | `CRACK_WIDTH_MM` | 随机 | 固定裂缝宽度 (mm)，覆盖每铺地随机值 |
| `--wait-api` | - | false | 启动前等待 API 就绪 |

### 使用示例

```bash
# 1. 干跑验证数据格式
python sensor_simulator.py --dry-run

# 2. 固定 20mm/h 暴雨，测试排水告警
python sensor_simulator.py --rainfall 20 --simulate --interval 30

# 3. 固定裂缝宽度 5mm，研究对排水的影响
python sensor_simulator.py --crack-width 5.0 --simulate

# 4. MQTT 模式上报 + HTTP 双通道
python sensor_simulator.py --mqtt --simulate

# 5. 只模拟1个铺地，间隔5秒（快速演示）
python sensor_simulator.py --pavements 1 --interval 5 --simulate --aesthetic
```

### MQTT 主题格式

```
sensors/{pavement_id}/data
```

Payload 为 JSON 格式，与 REST API 的 `SensorDataDTO` 完全一致：

```json
{
  "pavementId": "a1b2c3d4-...",
  "recordedAt": "2024-01-01T12:00:00",
  "rainfallMm": 5.2,
  "waterDepthMm": 12.34,
  "crackWidthMm": 1.234,
  "stepFrequency": 3.5,
  "temperature": 23.5,
  "humidity": 65.2
}
```

---

## 监控与运维

### Spring Actuator 端点

| 端点 | 路径 | 说明 |
|---|---|---|
| 健康检查 | `/actuator/health` | liveness + readiness probes |
| 应用信息 | `/actuator/info` | 版本、构建信息 |
| 指标列表 | `/actuator/metrics` | 可用指标列表 |
| Prometheus | `/actuator/prometheus` | Prometheus 抓取格式 |

### 关键指标

在 Prometheus 中可查看：
- `jvm_memory_used_bytes` — JVM 内存使用
- `http_server_requests_seconds` — HTTP 请求耗时（带 uri/status 标签）
- `system_cpu_usage` — CPU 使用率
- `spring_integration_mqtt_inbound` — MQTT 消息统计（启用 MQTT 后）

### PostgreSQL 索引

[init.sql](file:///d:/SOLO-2/AI_solo_coder_task_A_138/sql/init.sql) 已配置以下索引：

| 索引 | 作用 |
|---|---|
| `idx_sensor_data_pavement_time` | 按铺地+时间倒序查询传感器数据（复合索引） |
| `idx_simulation_pavement_time` | 按铺地+时间倒序查询仿真历史 |
| `idx_aesthetic_pavement_time` | 按铺地+时间倒序查询美学结果 |
| `idx_alert_pavement_time` | 按铺地+时间倒序查询告警 |
| `idx_alert_unack` | 未确认告警的部分索引（快速查询 active 告警） |

---

## API 接口

### 铺地管理

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/api/pavements` | 获取所有铺地列表 |
| GET | `/api/pavements/{id}` | 获取单个铺地详情 |
| POST | `/api/pavements` | 新增铺地 |

### 传感器数据

| 方法 | 路径 | 说明 |
|---|---|---|
| POST | `/api/sensor-data` | 上报传感器数据 |
| GET | `/api/sensor-data/{pavementId}/latest` | 最新 N 条数据 |
| GET | `/api/sensor-data/{pavementId}/range?start=&end=` | 时间范围数据 |

### 排水仿真

| 方法 | 路径 | 说明 |
|---|---|---|
| POST | `/api/simulation/run` | 运行排水仿真 |
| GET | `/api/simulation/{pavementId}/history` | 仿真历史 |

### 美学分析

| 方法 | 路径 | 说明 |
|---|---|---|
| POST | `/api/aesthetic/analyze/{pavementId}` | 运行美学分析 |
| GET | `/api/aesthetic/{pavementId}/history` | 分析历史 |

### 告警管理

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/api/alerts/unacknowledged` | 未确认告警列表 |
| GET | `/api/alerts/pavement/{pavementId}` | 指定铺地的告警 |
| PUT | `/api/alerts/{alertId}/acknowledge` | 确认告警 |
| WS | `/ws/alerts` | 实时告警推送 |

---

## 配置参数

### 水力学参数 (drainage.*)

```yaml
drainage:
  gravity: 9.81                    # 重力加速度 m/s²
  friction-coeff: 0.03             # Manning 糙率系数
  time-step: 0.5                   # 时间步长 Δt (s)
  sample-interval: 10              # 时间序列采样间隔 (步)
  recession-threshold-m: 0.001     # 积水消退阈值 (m)
  recession-alert-threshold-sec: 1800  # 告警阈值 (s)
  crack-infiltration-width-factor: 10.0  # 裂缝宽度渗透增强系数
  crack-infiltration-step-factor: 0.1   # 踩踏渗透增强系数
  crack-flux-base-multiplier: 0.8  # 裂缝通量基础倍率
  crack-depth-enhance-divisor: 5.0 # 裂缝格渗透增强除数
  slope-vertical-reduction: 0.5    # Y方向坡度衰减
  cfl-factor: 0.5                  # CFL 安全系数
```

### 美学参数 (aesthetic.*)

```yaml
aesthetic:
  unbiased-box-sizes: [2,4,8,16,32,64,128]  # 无偏盒计数尺度
  raw-box-sizes: [2,4,8,16,32,64]           # 原始盒计数尺度
  entropy-bins: 18                  # 角度熵分箱数
  entropy-bin-degrees: 10.0         # 每箱角度 (°)
  brown-correction-power: 0.15      # Brown 校正幂次
  boundary-band-ratio: 6.0          # 边界环带比例
  fractal-weight: 0.4               # 分形维数权重
  entropy-weight: 0.3               # 信息熵权重
  density-weight: 0.3               # 裂缝密度权重
  density-scale: 10.0               # 密度归一化系数
  fractal-clip-min: 1.0             # 分形维数下界
  fractal-clip-max: 2.0             # 分形维数上界
  fractal-scale-factor: 1.05        # 分形维数比例系数
  irregularity-scale: 0.1           # 不规则度缩放
  crack-branch-high-prob: 0.7       # 高概率分支
  crack-branch-low-prob: 0.4        # 低概率分支
  crack-split-prob: 0.35            # 裂缝分裂概率
  default-seed-points: 15           # 默认种子点数
  seed-points-variation: 10         # 种子点变异范围
  default-target-segments: 35       # 默认目标段数
  default-irregularity: 0.7         # 默认不规则度
  default-seed: 42                  # 默认随机种子
  length-weight-multiplier: 2.0     # 长度权重倍数
  symmetry-chi-divisor: 3.0         # 对称性卡方除数
  symmetry-dist-weight: 0.4         # 分布对称性权重
  symmetry-horiz-weight: 0.3        # 水平对称权重
  symmetry-vert-weight: 0.3         # 垂直对称权重
```

### MQTT 配置 (mqtt.*)

```yaml
mqtt:
  enabled: false
  broker-url: tcp://localhost:1883
  client-id: icecrack-backend
  topic: sensors/+/data
  username:
  password:
  qos: 1
  connection-timeout: 10
  keep-alive: 60
```

---

## 许可

本项目为园林史研究用途。
