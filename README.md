# 智能充电桩调度计费系统

本目录为软件工程课程作业的最终工程提交目录，包含后端、前端和设计文档。

## 目录结构

```text
charging-pile-system/
  backend/    SpringBoot 后端工程
  frontend/   Vue3 + Vite 前端工程
  docs/       系统概要设计 PDF
```

## 后端运行

```bash
cd backend
mvn spring-boot:run
```

后端默认提供 `/api/...` 接口，使用 SQLite 作为持久化数据库。

## 前端运行

```bash
cd frontend
npm install
npm run dev
```

前端通过 Vite 开发服务器运行，并通过接口访问后端服务。

## 验证命令

后端测试：

```bash
cd backend
mvn test
```

前端构建：

```bash
cd frontend
npm run build
```

## 设计一致性说明

工程按照系统概要设计中的分层结构组织：

- 用户界面层：Vue 用户端和管理员端页面
- 控制器层：UserController、ChargingController、BillController、AdminController
- 业务/应用层：Service、状态机、调度策略、业务数据对象
- 持久化层：Mapper 与 SQLite
- 系统层：SpringBoot、JVM、SQLite JDBC 和操作系统运行支撑

后端已将开始充电、查询充电状态、结束充电等充电过程业务统一放入 ChargingService；调度逻辑通过 SchedulingStrategy 体系表达；快充/慢充请求与充电桩类型保持一致，不跨类型分配。

## 系统参数（与验收用例一致）

| 项目 | 取值 |
| --- | --- |
| 快充功率 | 30 度/小时 |
| 慢充功率 | 10 度/小时 |
| 峰时电价 | 1.0 元/度（10:00–15:00、18:00–21:00）|
| 平时电价 | 0.7 元/度（7:00–10:00、15:00–18:00、21:00–23:00）|
| 谷时电价 | 0.4 元/度（23:00–次日 7:00）|
| 服务费 | 0.8 元/度 |
| 快充桩数 | 2 |
| 慢充桩数 | 3 |
| 每桩排队队列长度 M | 3（含 1 充电 + 2 排队）|
| 等候区容量 N | 10 |

上述参数在 `backend/src/main/resources/data.sql`（功率与电价种子数据）和 `BillingService`（分时电价时段）中实现。

## 故障再调度（等候区冻结）

当充电桩故障或关闭时，`PileService.powerOff` 调用 `QueueService.releaseDispatchedForPile`：

- 故障桩上已分配但未完成的车辆进入“故障优先队列”（`priority=1`）；
- `dispatchNext` 先在最高优先级分组内选车，使故障车绝对优先于普通等候区车辆（普通等候区被冻结），直至故障车全部进入充电区后再恢复对普通等候区的调度；
- 再调度仅在同类型充电桩内进行，不跨快充/慢充类型。

该行为由 `FaultPriorityDispatchTest` 验证：`priority=1` 的故障车即使到达时间晚于普通等候区车辆，也会被优先调度。
