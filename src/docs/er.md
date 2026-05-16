# SchoolShop 数据库 ER 图

> 依据 [mysql.md](./mysql.md) 绘制。在 VS Code / GitHub / Cursor 中预览本文件即可渲染 Mermaid 图。  
> `order.biz_id` 为多态关联：`type=task` 时指向 `task.id`，`type=material` 时指向 `material.id`（库表未建物理外键，由应用层保证）。

---

## 1. 总览（16 表）

```mermaid
erDiagram
    USER ||--o{ USER_FOLLOW : "follower"
    USER ||--o{ USER_FOLLOW : "followee"
    USER ||--o{ UPLOAD_FILE : uploads
    USER ||--o{ TASK : publishes
    USER ||--o{ TASK : accepts
    USER ||--o{ MATERIAL : sells
    USER ||--o{ USER_MATERIAL : purchases
    USER ||--o{ POST : publishes
    USER ||--o{ POST_LIKE : likes
    USER ||--o{ POST_COMMENT : comments
    USER ||--o{ ORDER_TBL : places
    USER ||--o{ WALLET_RECORD : owns
    USER ||--o{ WITHDRAW : applies
    USER ||--o{ ACTIVITY_NOTIFICATION : receives
    USER ||--o{ ACTIVITY_NOTIFICATION : triggers
    USER ||--o{ CONVERSATION : owns
    USER ||--o{ CONVERSATION : peer
    USER ||--o{ MESSAGE : sends
    USER ||--o{ MESSAGE : receives

    MATERIAL ||--o{ USER_MATERIAL : "sold_to"
    POST ||--o{ POST_LIKE : has
    POST ||--o{ POST_COMMENT : has
    POST ||--o{ ACTIVITY_NOTIFICATION : relates

    TASK ||--o{ ORDER_TBL : "biz task"
    MATERIAL ||--o{ ORDER_TBL : "biz material"

    ORDER_TBL }o--|| PAY_NOTIFY_LOG : "out_trade_no"

    USER {
        bigint id PK
        varchar openid UK
        varchar unionid
        varchar nickname
        int wallet_balance
        int wallet_frozen
        tinyint status
    }

    USER_FOLLOW {
        bigint id PK
        bigint follower_id FK
        bigint followee_id FK
    }

    UPLOAD_FILE {
        bigint id PK
        bigint user_id FK
        varchar file_key
        varchar biz_type
    }

    TASK {
        bigint id PK
        bigint publisher_id FK
        bigint acceptor_id FK
        int reward_amount
        tinyint status
        int version
        json tags
    }

    MATERIAL {
        bigint id PK
        bigint user_id FK
        varchar file_key
        int price
        tinyint status
    }

    USER_MATERIAL {
        bigint id PK
        bigint user_id FK
        bigint material_id FK
    }

    POST {
        bigint id PK
        bigint user_id FK
        int category_id
        json images
        tinyint is_deleted
    }

    POST_LIKE {
        bigint id PK
        bigint user_id FK
        bigint post_id FK
    }

    POST_COMMENT {
        bigint id PK
        bigint post_id FK
        bigint user_id FK
        bigint parent_id
    }

    ORDER_TBL {
        bigint id PK
        varchar order_no UK
        bigint user_id FK
        varchar type
        bigint biz_id
        tinyint status
        varchar out_trade_no UK
    }

    WALLET_RECORD {
        bigint id PK
        bigint user_id FK
        varchar type
        int amount
        varchar biz_type
        bigint biz_id
    }

    WITHDRAW {
        bigint id PK
        bigint user_id FK
        int amount
        tinyint status
    }

    ACTIVITY_NOTIFICATION {
        bigint id PK
        bigint user_id FK
        bigint actor_id FK
        bigint post_id FK
        varchar type
    }

    CONVERSATION {
        bigint id PK
        bigint user_id FK
        bigint peer_id FK
        int unread
    }

    MESSAGE {
        bigint id PK
        bigint sender_id FK
        bigint receiver_id FK
        varchar type
    }

    PAY_NOTIFY_LOG {
        bigint id PK
        varchar out_trade_no UK
    }
```

---

## 2. 用户与社交

```mermaid
erDiagram
    USER ||--o{ USER_FOLLOW : follower_id
    USER ||--o{ USER_FOLLOW : followee_id
    USER ||--o{ POST : user_id
    USER ||--o{ POST_LIKE : user_id
    USER ||--o{ POST_COMMENT : user_id
    USER ||--o{ ACTIVITY_NOTIFICATION : user_id
    USER ||--o{ ACTIVITY_NOTIFICATION : actor_id

    POST ||--o{ POST_LIKE : post_id
    POST ||--o{ POST_COMMENT : post_id
    POST ||--o{ ACTIVITY_NOTIFICATION : post_id

    USER {
        bigint id PK
        varchar nickname
        tinyint real_name_verified
    }

    USER_FOLLOW {
        bigint follower_id FK
        bigint followee_id FK
    }

    POST {
        bigint id PK
        bigint user_id FK
        int category_id
        int like_count
        int comment_count
    }

    POST_LIKE {
        bigint user_id FK
        bigint post_id FK
    }

    POST_COMMENT {
        bigint post_id FK
        bigint user_id FK
        bigint parent_id
    }

    ACTIVITY_NOTIFICATION {
        bigint user_id FK
        bigint actor_id FK
        varchar type
        bigint post_id FK
    }
```

---

## 3. 代办悬赏与资料集市

```mermaid
erDiagram
    USER ||--o{ TASK : publisher_id
    USER ||--o{ TASK : acceptor_id
    USER ||--o{ MATERIAL : user_id
    USER ||--o{ UPLOAD_FILE : user_id
    USER ||--o{ USER_MATERIAL : user_id

    MATERIAL ||--o{ USER_MATERIAL : material_id

    USER {
        bigint id PK
    }

    UPLOAD_FILE {
        bigint user_id FK
        varchar file_key
        varchar biz_type
    }

    TASK {
        bigint id PK
        bigint publisher_id FK
        bigint acceptor_id FK
        int reward_amount
        tinyint status
        json delivery_images
    }

    MATERIAL {
        bigint id PK
        bigint user_id FK
        varchar file_key
        int sold_count
    }

    USER_MATERIAL {
        bigint user_id FK
        bigint material_id FK
    }
```

---

## 4. 订单、支付与钱包

```mermaid
erDiagram
    USER ||--o{ ORDER_TBL : user_id
    USER ||--o{ WALLET_RECORD : user_id
    USER ||--o{ WITHDRAW : user_id

    TASK ||--o{ ORDER_TBL : "biz_id type=task"
    MATERIAL ||--o{ ORDER_TBL : "biz_id type=material"

    ORDER_TBL }o--o| PAY_NOTIFY_LOG : out_trade_no

    USER {
        bigint id PK
        int wallet_balance
        int wallet_frozen
    }

    ORDER_TBL {
        bigint id PK
        varchar order_no UK
        varchar type
        bigint biz_id
        int amount
        tinyint status
        varchar out_trade_no UK
    }

    TASK {
        bigint id PK
        tinyint status
    }

    MATERIAL {
        bigint id PK
    }

    WALLET_RECORD {
        bigint user_id FK
        varchar type
        int amount
        varchar biz_type
        bigint biz_id
    }

    WITHDRAW {
        bigint user_id FK
        int amount
        tinyint status
    }

    PAY_NOTIFY_LOG {
        varchar out_trade_no UK
    }
```

**说明**

- 创建任务 / 购买资料时各写入一条 `ORDER_TBL`（`status=0`）。
- 微信支付回调验签后写 `PAY_NOTIFY_LOG`，再更新 `ORDER_TBL` 与 `task` / `user_material`。
- 任务验收完成后，`WALLET_RECORD` 给接单人入账（`biz_type=task`）。

---

## 5. 私信

```mermaid
erDiagram
    USER ||--o{ CONVERSATION : user_id
    USER ||--o{ CONVERSATION : peer_id
    USER ||--o{ MESSAGE : sender_id
    USER ||--o{ MESSAGE : receiver_id

    USER {
        bigint id PK
    }

    CONVERSATION {
        bigint id PK
        bigint user_id FK
        bigint peer_id FK
        varchar last_message
        int unread
    }

    MESSAGE {
        bigint id PK
        bigint sender_id FK
        bigint receiver_id FK
        varchar type
        tinyint is_read
    }
```

**说明**：每对用户维护两条 `CONVERSATION`（分别以各自为 `user_id`）；发消息时更新双方会话摘要并插入 `MESSAGE`。

---

## 6. 关系速查

| 关系 | 类型 | 说明 |
|------|------|------|
| user ↔ user_follow | 1:N（自关联） | 关注者 / 被关注者均指向 user |
| user → post | 1:N | 发帖 |
| post ↔ post_like | 1:N | UK(user_id, post_id) |
| post ↔ post_comment | 1:N | 评论，parent_id 预留回复 |
| user → task | 1:N | publisher；acceptor 可空 |
| user → material | 1:N | 卖家上架 |
| user ↔ material | N:M | 经 user_material 表示已购 |
| user → order | 1:N | 统一订单 |
| order → task / material | N:1（多态） | 由 type + biz_id 区分 |
| order ↔ pay_notify_log | 1:1（逻辑） | out_trade_no 幂等 |
| user → wallet_record / withdraw | 1:N | 流水与提现 |
| user → activity_notification | 1:N | 接收方 user_id；触发方 actor_id |
| user ↔ conversation | 1:N | 每对用户两条会话记录 |
| user ↔ message | 1:N | 发送方 / 接收方 |

---

## 7. 模块划分（逻辑视图）

```mermaid
flowchart TB
    subgraph core [用户中心]
        USER[user]
    end

    subgraph social [社区]
        POST[post]
        POST_LIKE[post_like]
        POST_COMMENT[post_comment]
        ACTIVITY[activity_notification]
        FOLLOW[user_follow]
    end

    subgraph trade [交易]
        TASK[task]
        MATERIAL[material]
        USER_MATERIAL[user_material]
        ORDER_TBL[order]
        PAY_LOG[pay_notify_log]
    end

    subgraph wallet [钱包]
        WALLET[wallet_record]
        WITHDRAW[withdraw]
    end

    subgraph msg [消息]
        CONV[conversation]
        MSG[message]
    end

    subgraph infra [基础设施]
        UPLOAD[upload_file]
    end

    USER --> social
    USER --> trade
    USER --> wallet
    USER --> msg
    USER --> UPLOAD
    ORDER_TBL --> PAY_LOG
```
