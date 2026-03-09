# 一、建模目标与边界

本文基于你现有的 **基础平台表结构（Tenant / Organization / User / RBAC）**，给出一套 **可落地、可扩展、不污染平台层** 的充电管理系统详细建模方案。

目标：

* 支撑 **多租户**（SaaS）
* 支撑 **多运营商**（一个租户内可有多个）
* 支撑 **多场站 / 多设备**
* 权限、数据隔离、业务演进不互相拖死

---

# 二、总体分层模型（非常关键）

```text
┌─────────────────────────────┐
│        基础平台层（已有）    │
│ tenant / user / role        │
│ organization / resource     │
└───────────────▲─────────────┘
                │（权限 / 可见性）
┌───────────────┴─────────────┐
│        业务关联层（轻）      │
│ org ↔ station / operator    │
└───────────────▲─────────────┘
                │（业务规则）
┌───────────────┴─────────────┐
│        充电业务域（核心）    │
│ operator / station / charger│
│ order / bill / settlement   │
└─────────────────────────────┘
```

原则：

* **平台表不存业务规则**
* **业务表不感知权限实现细节**

---

# 三、核心领域一：运营商域（Operator Domain）

## 3.1 运营商不是 Tenant

> Tenant = 系统隔离边界
> Operator = 业务经营主体

### operator（运营商）

```sql
CREATE TABLE cm_operator (
  OPERATOR_ID      varchar(32) NOT NULL,
  TENANT_ID        varchar(32) NOT NULL,
  OPERATOR_CODE    varchar(32) NOT NULL,
  OPERATOR_NAME    varchar(100) NOT NULL,
  STATUS           int NOT NULL COMMENT '0停用 1启用',
  CONTACT_NAME     varchar(50),
  CONTACT_PHONE    varchar(20),
  CREATE_TIME      datetime DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (OPERATOR_ID)
);
```

语义：

* 一个 Tenant 下 **N 个 Operator**
* 平台自营 + 第三方运营商天然支持

---

### operator_account（运营商账户 / 结算主体）

```sql
CREATE TABLE cm_operator_account (
  ACCOUNT_ID    varchar(32) NOT NULL,
  OPERATOR_ID   varchar(32) NOT NULL,
  ACCOUNT_TYPE  int COMMENT '1主账户 2分账账户',
  STATUS        int,
  PRIMARY KEY (ACCOUNT_ID)
);
```

> 用于后期 **分账 / 结算 / 对账**，不要省

---

# 四、核心领域二：场站域（Station Domain）

> **业务约束说明（重要）**：
>
> * **运营商（Operator）与场站（Station）是严格的一对多关系**
> * 一个运营商可以拥有多个场站
> * 一个场站在任一时刻 **只归属于一个运营商**
> * 不支持场站跨运营商共享（如后期需要联营，应通过扩展表而非破坏主关系）

## 4.1 场站是业务资产，不是组织

### station（充电场站）

```sql
CREATE TABLE cm_station (
  STATION_ID     varchar(32) NOT NULL,
  OPERATOR_ID    varchar(32) NOT NULL,
  STATION_CODE   varchar(50),
  STATION_NAME   varchar(100) NOT NULL,
  PROVINCE       varchar(20),
  CITY           varchar(20),
  ADDRESS        varchar(255),
  STATUS         int COMMENT '0停运 1运营',
  OPEN_TIME      varchar(50),
  CREATE_TIME    datetime,
  PRIMARY KEY (STATION_ID)
);
```

---

### station_price（场站定价策略）

```sql
CREATE TABLE cm_station_price (
  PRICE_ID     varchar(32) NOT NULL,
  STATION_ID   varchar(32) NOT NULL,
  TIME_RANGE   varchar(50) COMMENT '峰平谷',
  PRICE        decimal(10,4),
  PRIMARY KEY (PRICE_ID)
);
```

---

## 4.2 场站与组织的关系（权限视角）

```sql
CREATE TABLE cm_station_org (
  STATION_ID       varchar(32) NOT NULL,
  ORGANIZATION_ID  varchar(32) NOT NULL,
  PRIMARY KEY (STATION_ID, ORGANIZATION_ID)
);
```

用途：

* 组织能“看到 / 管理”哪些场站
* **不参与业务计算**

---

# 五、核心领域三：设备域（Charger Domain）

### charger（充电桩）

```sql
CREATE TABLE cm_charger (
  CHARGER_ID    varchar(32) NOT NULL,
  STATION_ID    varchar(32) NOT NULL,
  CHARGER_CODE  varchar(50),
  MODEL         varchar(50),
  STATUS        int COMMENT '0离线 1在线',
  PRIMARY KEY (CHARGER_ID)
);
```

### connector（枪口）

```sql
CREATE TABLE cm_connector (
  CONNECTOR_ID varchar(32) NOT NULL,
  CHARGER_ID   varchar(32) NOT NULL,
  STATUS       int,
  PRIMARY KEY (CONNECTOR_ID)
);
```

---

# 六、核心领域四：订单 / 计费 / 结算

### charge_order（充电订单）

```sql
CREATE TABLE cm_charge_order (
  ORDER_ID     varchar(32) NOT NULL,
  USER_ID      varchar(32),
  STATION_ID   varchar(32),
  CHARGER_ID   varchar(32),
  START_TIME  datetime,
  END_TIME    datetime,
  TOTAL_FEE   decimal(10,2),
  STATUS      int,
  PRIMARY KEY (ORDER_ID)
);
```

### settlement_record（结算记录）

```sql
CREATE TABLE cm_settlement_record (
  SETTLEMENT_ID varchar(32) NOT NULL,
  OPERATOR_ID   varchar(32) NOT NULL,
  AMOUNT        decimal(12,2),
  PERIOD        varchar(20),
  STATUS        int,
  PRIMARY KEY (SETTLEMENT_ID)
);
```

---

# 七、权限与数据隔离如何生效（重点）

* 登录 → tenant 确定
* 用户 → organization 确定
* organization → station 映射
* station → operator → 业务数据

```text
User
 → Organization
   → Station
     → Operator
       → Order / Bill
```

**平台只管“能不能看”**
**业务自己算“怎么算钱”**

---

# 八、为什么这套模型能长期活

* Tenant 不被业务污染
* Organization 不背业务字段
* Operator / Station 清晰解耦
* 支持：

    * 平台自营
    * 多运营商
    * 联营 / 分账
    * 区域托管

---

# 九、下一步可演进方向

* 数据权限 DSL（基于组织 + 运营商）
* 设备实时状态独立服务
* 结算域独立微服务

---

**这套模型可以直接进入开发阶段。**

如果你需要：

* ER 图
* 表字段进一步细化
* 结合 OCPP / 国标协议

我可以继续拆。
