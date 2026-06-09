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
