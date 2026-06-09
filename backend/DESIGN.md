# 智能充电桩调度计费系统 — MVP 详细设计

## 1. 项目结构

```
se-2/charging-pile/
├── pom.xml                          # Maven 配置
└── src/
    ├── main/
    │   ├── java/com/bupt/charging/
    │   │   ├── ChargingApplication.java          # SpringBoot 启动类
    │   │   ├── controller/
    │   │   │   ├── UserController.java           # UC1注册 + UC3账单
    │   │   │   ├── ChargingController.java       # UC2充电申请
    │   │   │   └── AdminController.java          # UC4运行充电桩 + UC5状态
    │   │   ├── service/
    │   │   │   ├── UserService.java
    │   │   │   ├── ChargingService.java
    │   │   │   ├── QueueService.java             # 调度核心
    │   │   │   ├── BillingService.java           # 计费
    │   │   │   └── PileService.java
    │   │   ├── entity/
    │   │   │   ├── User.java                     # 用户
    │   │   │   ├── ChargingRequest.java          # 充电请求
    │   │   │   ├── ChargingRecord.java           # 充电记录
    │   │   │   ├── Bill.java                     # 账单
    │   │   │   ├── ChargingPile.java             # 充电桩
    │   │   │   └── PricingConfig.java            # 计费参数
    │   │   ├── mapper/
    │   │   │   ├── UserMapper.java
    │   │   │   ├── ChargingRequestMapper.java
    │   │   │   ├── ChargingRecordMapper.java
    │   │   │   ├── BillMapper.java
    │   │   │   ├── ChargingPileMapper.java
    │   │   │   └── PricingConfigMapper.java
    │   │   └── config/
    │   │       └── SqliteConfig.java             # SQLite 数据源配置
    │   └── resources/
    │       ├── application.properties            # SpringBoot 配置
    │       └── schema.sql                        # 建表语句
    └── test/
        └── java/com/bupt/charging/
            └── ChargingApplicationTests.java
```

### 对应文档的分层

```
Controller/  → controller/
Service/     → service/
领域对象      → entity/
Mapper/      → mapper/ (MyBatis 接口)
```

---

## 2. 数据库设计（SQLite）

### 2.1 表结构

**user（用户表）**
| 列 | 类型 | 说明 |
|----|------|------|
| car_id | TEXT PK | 车辆ID（主键） |
| user_name | TEXT | 用户名 |
| car_capacity | REAL | 电池容量 kWh |
| password | TEXT | 加密密码 |
| state | TEXT | 状态：inactive/active |
| create_time | TEXT | 创建时间 |

**charging_request（充电请求表）**
| 列 | 类型 | 说明 |
|----|------|------|
| id | INTEGER PK | 自增ID |
| car_id | TEXT FK | 车辆ID |
| request_amount | REAL | 期望充电电量 kWh |
| request_mode | INTEGER | 0=慢充 1=快充 |
| request_time | TEXT | 请求时间 |
| car_state | TEXT | waiting/charging/done/cancelled |
| pile_id | INTEGER | 分配的充电桩ID（可为空） |
| queue_num | INTEGER | 排队序号 |

**charging_record（充电记录表）**
| 列 | 类型 | 说明 |
|----|------|------|
| id | INTEGER PK | 自增ID |
| car_id | TEXT FK | 车辆ID |
| request_id | INTEGER FK | 关联的充电请求ID |
| pile_id | INTEGER | 充电桩编号 |
| start_time | TEXT | 开始时间 |
| end_time | TEXT | 结束时间（可为空） |
| charge_amount | REAL | 实际充电量 kWh |
| charge_fee | REAL | 充电费 |
| service_fee | REAL | 服务费 |

**bill（账单表）**
| 列 | 类型 | 说明 |
|----|------|------|
| id | INTEGER PK | 自增ID |
| car_id | TEXT FK | 车辆ID |
| date | TEXT | 账单日期 |
| pile_id | INTEGER | 充电桩编号 |
| charge_amount | REAL | 充电总量 kWh |
| charge_duration | INTEGER | 充电时长（秒） |
| total_charge_fee | REAL | 总充电费 |
| total_service_fee | REAL | 总服务费 |
| total_fee | REAL | 总费用 |

**charging_pile（充电桩表）**
| 列 | 类型 | 说明 |
|----|------|------|
| id | INTEGER PK | 充电桩编号 |
| type | TEXT | fast/slow |
| power_state | TEXT | on/off |
| working_state | TEXT | idle/charging/running/fault |
| total_charge_num | INTEGER | 累计充电次数 |
| total_charge_time | INTEGER | 累计充电时长（秒） |
| total_capacity | REAL | 累计充电量 kWh |

**pricing_config（计费参数表）**
| 列 | 类型 | 说明 |
|----|------|------|
| id | INTEGER PK | 固定为1（单例配置） |
| peak_price | REAL | 峰时电价 元/kWh |
| normal_price | REAL | 平时电价 元/kWh |
| valley_price | REAL | 谷时电价 元/kWh |
| service_fee_rate | REAL | 服务费率 元/kWh |

### 2.2 初始数据

系统启动时预置：
- 5个充电桩：pile 1,2 快充(120kW)，pile 3,4,5 慢充(60kW)，初始状态均为 idle
- 1条计费参数：峰时1.2 元/kWh、平时0.8 元/kWh、谷时0.6 元/kWh、服务费率0.2 元/kWh
- 0个用户（通过注册API创建）

---

## 3. API 设计

### 3.1 UC1 注册

| 方法 | 路径 | 对应指令 | 参数 |
|------|------|---------|------|
| POST | /api/user/register | createNewAccount | JSON: {carId, userName, carCapacity} |
| POST | /api/user/set-password | set_pwd | JSON: {carId, password} |

### 3.2 UC2 充电申请

| 方法 | 路径 | 对应指令 | 参数 |
|------|------|---------|------|
| POST | /api/charging/request | E_chargingRequest | JSON: {carId, requestAmount, requestMode} |
| PUT | /api/charging/amount | Modify_Amount | JSON: {carId, amount} |
| PUT | /api/charging/mode | Modify_Mode | JSON: {carId, mode} |
| GET | /api/charging/state/{carId} | Query_Car_State | — |
| POST | /api/charging/start | Start_Charging | JSON: {carId, pileId} |
| GET | /api/charging/progress/{carId} | Query_Charging_State | — |
| POST | /api/charging/end | End_Charging | JSON: {carId, pileId} |

### 3.3 UC3 查看账单

| 方法 | 路径 | 对应指令 | 参数 |
|------|------|---------|------|
| GET | /api/bill/{carId} | Request_Bill | ?date=2026-06-03 |
| GET | /api/bill/detail/{billId} | Request_DetailedList | — |

### 3.4 UC4 运行充电桩

| 方法 | 路径 | 对应指令 | 参数 |
|------|------|---------|------|
| POST | /api/admin/pile/power-on | powerOn | JSON: {pileId} |
| PUT | /api/admin/pricing | setParameters | JSON: {peakPrice, normalPrice, valleyPrice, serviceFeeRate} |
| POST | /api/admin/pile/start | Start_ChargingPile | JSON: {pileId} |
| POST | /api/admin/pile/power-off | powerOff | JSON: {pileId} |

### 3.5 UC5 查看充电桩状态

| 方法 | 路径 | 对应指令 | 参数 |
|------|------|---------|------|
| GET | /api/admin/pile/state | Query_PileState | ?pileId=1（可选） |
| GET | /api/admin/queue/state | Query_QueueState | ?type=fast/slow |

---

## 4. 关键实现要点

### 4.1 调度策略（QueueService）

MVP 实现 FCFS（先来先到）。QueueService 维护两个内存队列：
- fastQueue（快充等待队列）
- slowQueue（慢充等待队列）

调度触发时机：
- End_Charging 后（充电桩释放）
- Start_Charging 后（车辆从等待队列入服务队列）

调度逻辑：
1. 从等待队列按 request_time 升序取出请求
2. 在同类型充电桩中选择排队车辆最少的桩
3. 分配后更新 ChargingRequest 的 pile_id 和 car_state

### 4.2 计费策略（BillingService）

充电费 = 充电电量 × 时段电价
- 08:00-22:00 峰时
- 22:00-08:00 谷时
- MVP 简化为两个时段即可

服务费 = 充电电量 × 服务费率

费用计算在 Query_Charging_State（实时）和 End_Charging（结算）时触发。

### 4.3 技术选型细节

| 组件 | 选择 | 说明 |
|------|------|------|
| 框架 | SpringBoot 3.x | starter-web + mybatis-spring-boot-starter |
| ORM | MyBatis | 注解方式，无需 XML |
| 数据库 | SQLite | sqlite-jdbc 驱动 |
| 数据库初始化 | schema.sql + data.sql | SpringBoot 自动执行 |
| 前端 | 静态 HTML + fetch | 复用 se-2/html/ 下已完成的页面，加 JS 调用后端 API |
| 密码存储 | BCrypt | Spring Security 的 BCryptPasswordEncoder |

### 4.4 前端集成方式

不做 Vue.js 工程化构建，直接给已有 HTML 页面加 JavaScript：

```
user_panel.html   +  <script> 调用 /api/charging/* /api/user/* /api/bill/*
admin_panel.html  +  <script> 调用 /api/admin/*
```

每个页面新增：
- 表单提交事件 → fetch 调用对应 API
- 页面加载时 → GET 请求刷新状态
- 结果显示 → 更新 DOM

---

## 5. 验收演示路径

按以下顺序操作即可演示全部 5 个用例：

```
1. UC1 注册
   打开 user_panel.html → 注册账号（carId=京A001, name=测试用户, capacity=60）
   → 设置密码

2. UC2 充电申请（全程）
   提交充电申请（慢充, 40kWh）→ 查看排队状态
   → 修改电量 → 修改模式 → 开始充电 → 查看充电状态 → 结束充电

3. UC3 查看账单
   查看今日账单 → 查看详单

4. UC4 运行充电桩（管理员）
   打开 admin_panel.html → 查看充电桩状态 → 启动充电桩
   → 设置计费参数 → 运行充电桩 → 关闭充电桩

5. UC5 查看状态
   查看充电桩状态（含定时刷新）→ 查看队列状态
```

---

## 6. 工时估算

| 模块 | 文件数 | 预计时间 |
|------|--------|---------|
| pom.xml + 配置 | 3 | 30min |
| entity 层（6个实体类） | 6 | 30min |
| mapper 层（6个接口） | 6 | 30min |
| service 层（5个服务类） | 5 | 2h（调度逻辑最复杂） |
| controller 层（3个控制器） | 3 | 1h |
| 前端接入（2个HTML加JS） | 2 | 1h |
| 联调测试 | — | 1h |
| **合计** | **25个文件** | **约6h** |
