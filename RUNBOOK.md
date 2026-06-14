# 运行与验收说明（RUNBOOK）

本文档说明如何启动后端、前端，如何在「真实墙钟」与「仿真时钟（sim profile）」
两种模式间切换，以及如何运行验收复现脚本与测试。

仓库根目录即本工程目录（backend / frontend / docs）。

---

## 1. 两种运行模式

系统业务逻辑（调度、计费、故障处理）只有一套代码，两种模式仅在
**时钟来源** 和 **数据库文件** 上不同：

| 模式 | profile | 时钟来源 | 数据库文件 | 用途 |
| --- | --- | --- | --- | --- |
| 真实墙钟（默认） | 默认（不带 -Dspring-boot.run.profiles） | 系统真实时间 | `charging-pile.db` | 生产 / 日常验收，进度随真实时间走动 |
| 仿真时钟 | `sim` | 可注入，由 `/api/admin/sim/clock` 设定 | `charging-pile-sim.db` | 黑盒回放验收用例、演示跨时段电价与故障时序 |

要点：
- 仿真时钟端点 `/api/admin/sim/clock` 仅在 `sim` profile 挂载。默认 profile 下
  访问返回 404 —— 生产环境没有任何篡改时间的后门。
- 两个模式用不同的数据库文件，互不污染。
- 仿真时钟是「被设定的固定值」，不会自己流逝；需要主动推进（见 4.3）。
  因此在 sim 模式下若不推进时钟，充电进度会停在设定时刻不动，这是预期行为。

---

## 2. 启动后端

### 2.1 真实墙钟（默认，推荐日常验收）

```bash
cd backend
mvn -DskipTests spring-boot:run
```

- 监听 `localhost:8080`，使用 `charging-pile.db`。
- 充电进度随真实时间走动；充满后由监控器自动结算离桩、生成账单。
- 想看进度走动：申请电量给大一点（快充 5 度约 10 分钟，慢充 2 度约 12 分钟），
  期间刷新「充电过程」可看到电量/费用实时增长。
- 想快点看账单闭环：用小电量（如 0.5 度，约 1 分钟）即可。

### 2.2 仿真时钟（sim profile）

```bash
cd backend
mvn -DskipTests spring-boot:run -Dspring-boot.run.profiles=sim
```

- 监听 `localhost:8080`，使用 `charging-pile-sim.db`，初始仿真时间 06:00。
- 通过 `/api/admin/sim/clock` 推进时间（见 4.3），几秒即可复现数小时剧情。

### 2.3 切换模式 / 清空数据库

切换前先停掉占用 8080 的进程，必要时删除对应库文件即可获得干净起点：

```bash
# 停掉 8080
lsof -ti:8080 | xargs kill -9

# 清空真实墙钟库（全空白起点）
rm -f backend/charging-pile.db

# 清空仿真库
rm -f backend/charging-pile-sim.db
```

删除后下次启动会按 `schema.sql` 自动重建表结构与 `data.sql` 种子数据
（充电桩、计费参数），用户数据为空。

---

## 3. 启动前端

```bash
cd frontend
npm install      # 首次需要
npm run dev
```

- 开发服务器默认 `http://localhost:5173`，并监听 `0.0.0.0`（局域网可访问）。
- 前端通过 Vite 代理把 `/api` 转发到后端，默认目标 `http://localhost:8080`。
- 如需指向其他后端地址：

```bash
VITE_API_TARGET=http://<后端IP>:8080 npm run dev
```

- 生产构建：

```bash
npm run build    # 产物在 frontend/dist/
```

---

## 4. 验收操作

### 4.1 完整流程（用任意车牌，不依赖测试编号）

浏览器打开 `http://localhost:5173`：

1. 注册：填车牌（如 `京A88888`）、用户名、电池容量。
2. 登录：车牌 + 设定的密码。
3. 提交充电请求：选快充/慢充 + 充电量。
4. 开始充电：系统按「完成充电所需时间最短」自动选桩。
5. 查充电过程：实时电量、费用、用时。
6. 查账单/详单：充满后生成；账单可下钻查看其包含的详单（每段充电一条）。

### 4.2 故障调度验收（命令行，需后端运行）

```bash
# 让某桩故障（pileId: 1=快充1 2=快充2 3=慢充1 4=慢充2 5=慢充3）
curl -X POST localhost:8080/api/admin/pile/fault \
  -H 'Content-Type: application/json' -d '{"pileId":1}'

# 故障恢复
curl -X POST localhost:8080/api/admin/pile/recover \
  -H 'Content-Type: application/json' -d '{"pileId":1}'
```

故障语义（与详细需求一致）：若故障桩上有车正在充电，先按已充电量结算出
**一条详单**，其剩余需求作为高优先级请求迁到同类型其他桩续充；整个充电过程
最终归并为 **一张账单**，对应 **至少 2 条详单**。普通等候区在故障车全部
进入充电区前被冻结。再调度不跨快充/慢充类型。

### 4.3 推进仿真时钟（仅 sim profile）

```bash
# 查询当前仿真时间
curl localhost:8080/api/admin/sim/clock

# 跳到指定时刻（让充电按时推进、触发充满离桩）
curl -X POST localhost:8080/api/admin/sim/clock \
  -H 'Content-Type: application/json' \
  -d '{"time":"2026-06-15T11:00:00"}'
```

监控器每 0.5 秒轮询，仿真时间越过充满时刻时自动结算离桩。

---

## 5. 黑盒复现脚本（验收用例 → CSV）

`acceptance_blackbox.py` 用纯 HTTP 黑盒方式驱动 **sim profile** 后端，按验收用例的
42 个事件推进仿真钟回放，并把每个事件时刻的系统状态导出为 CSV，布局对齐
「作业验收用例.xlsx」的「测试用例」工作表，可逐格复现验收表。

前置：后端必须以 sim profile 运行（见 2.2）。脚本启动时会做健康检查，
若后端不可达或非 sim profile，会立即报错并以退出码 2 退出，不会静默失败。

```bash
# 1) 启动 sim profile 后端（见 2.2），另开一个终端：
cd <仓库根目录>
python3 acceptance_blackbox.py --base http://localhost:8080 --out acceptance.csv
```

输出两个 CSV（UTF-8-sig，Excel 可直接打开）：

- `acceptance.csv` —— 桩位快照：列为「时刻 / 事件 / 快充1 / 快充2 / 慢充1 /
  慢充2 / 慢充3 / 等候区」，每个事件占 3 行（对应每桩 M=3 个位置），
  位置格式 `(车号,已充电量,当前费用)`，空位为 `-`。
- `acceptance.csv.bills.csv` —— 账单汇总：车号 / 充电量 / 充电费 / 服务费 /
  合计 / 充电桩 / 详单数（故障车显示 2 张详单）。

退出码：`0` 正常完成；`2` 后端不可用或非 sim profile。

---

## 6. 测试

后端单元/集成测试（含故障调度、桩内队列、账单聚合）：

```bash
cd backend
mvn test
```

涵盖：
- `ChargingFlowIntegrationTest` —— 充电主流程
- `PileQueueCapacityTest` —— 桩内队列 M=3
- `FaultPriorityDispatchTest` —— 故障优先调度、等候区冻结
- `FaultSchedulingTest` —— 故障置 FAULT、车头结算、剩余迁桩续充

前端构建检查：

```bash
cd frontend
npm run build
```

---

## 7. 常见问题

- 充电进度不动、用时一直 0、开始时间停在 06:00：
  说明后端跑在 sim profile 且仿真钟没推进。要么推进仿真钟（4.3），
  要么切到真实墙钟模式（2.1）。

- `/api/admin/sim/clock` 返回 404：
  后端运行在默认（真实墙钟）profile，这是正常的——该端点只在 sim profile 存在。

- 刚提交小电量请求就查不到充电进度：
  电量太小已秒充满自动离桩，去「账单查询」即可看到结果。

- 局域网其他机器访问前端：用 `http://<本机IP>:5173`，后端地址通过
  `VITE_API_TARGET` 指定（见第 3 节）。
