# SchoolShop 外部服务配置指南

> 所有密钥放在 **`application-local.yml`**（已从 Git 忽略），不要写进仓库。  
> 模板见：`src/main/resources/application-local.yml.example`

---

## 快速开始（零配置联调）

不填任何 Key 也能跑：

| 能力 | 缺省行为 |
|------|----------|
| 微信登录 | `mock-enabled: true` 时用 `mock_{code}` 作为 openid |
| 内容安全 | `content-check-mode: local` 本地敏感词 |
| 文件上传 | 返回 mock URL，元数据仍写入数据库 |
| 资料下载 | 返回 mock 预签名链接 |
| AI 对话 | 规则引擎兜底（课表/积分/搭子 FAQ） |
| 管理端 | `admin.token` 为空时 Admin 接口返回 403 |

前端联调：`USE_MOCK=false` + 后端启动即可。

---

## 1. 微信小程序（登录 + 内容安全）

### 配置项

```yaml
schoolshop:
  wechat:
    app-id: "wxXXXXXXXX"
    app-secret: "xxxxxxxx"
    mock-enabled: false   # 生产务必 false
  security:
    content-check-mode: wechat   # 启用微信 msgSecCheck
```

### 去哪里拿

1. 打开 [微信公众平台](https://mp.weixin.qq.com/)
2. **开发 → 开发管理 → 开发设置**
3. 复制 **AppID**、**AppSecret**（重置后旧 Secret 失效）
4. **开发 → 开发管理 → 接口设置** 确认已开通「内容安全」相关能力

### 小程序后台还需配置

- **开发 → 开发管理 → 开发设置 → 服务器域名**：填入你的 API 域名（HTTPS）
- request 合法域名、uploadFile、downloadFile 域名与 OSS 域名一致

### 相关代码

- 登录：`integration/wechat/WeChatClient.code2Session`
- 内容安全：`WeChatClient.checkText`（`msg_sec_check`）

---

## 2. 阿里云 OSS（图片上传 + 资料私有下载）

### 配置项

```yaml
schoolshop:
  oss:
    enabled: true
    endpoint: "oss-cn-hangzhou.aliyuncs.com"
    access-key-id: "LTAI..."
    access-key-secret: "..."
    public-bucket: "schoolshop-public"
    private-bucket: "schoolshop-private"
    public-base-url: "https://schoolshop-public.oss-cn-hangzhou.aliyuncs.com"
```

### 去哪里拿

1. 登录 [阿里云控制台](https://oss.console.aliyun.com/)
2. 创建两个 Bucket：
   - **公共读**：帖子配图 `public-bucket`
   - **私有**：资料文件 `private-bucket`
3. **AccessKey**：右上角头像 → AccessKey 管理（建议用 RAM 子账号，仅 OSS 权限）
4. `endpoint` 在 Bucket 概览页查看（如 `oss-cn-hangzhou.aliyuncs.com`）
5. `public-base-url` = `https://{bucket}.{endpoint}`

### 小程序域名

将 OSS 域名加入小程序 **downloadFile / uploadFile 合法域名**。

### 相关代码

- `integration/oss/OssStorageService`

---

## 3. AI 大模型（可选，课表助手增强）

### 配置项

```yaml
schoolshop:
  ai:
    enabled: true
    api-key: "sk-..."
    base-url: "https://api.openai.com/v1"   # 或国内兼容网关
    model: "gpt-4o-mini"
```

### 去哪里拿

| 服务商 | 获取地址 |
|--------|----------|
| OpenAI | https://platform.openai.com/api-keys |
| 国内兼容（如 DeepSeek、硅基流动等） | 各平台控制台创建 API Key，将 `base-url` 改为对方文档地址 |

未配置时自动使用 **规则引擎** 回复，不影响演示。

### 相关代码

- `integration/ai/LlmClient`（OpenAI Chat Completions 兼容协议）

---

## 4. 管理端 Admin Token

### 配置项

```yaml
schoolshop:
  admin:
    token: "your-strong-random-token"
```

### 用法

请求管理接口时加 Header：

```
X-Admin-Token: your-strong-random-token
```

### 接口列表

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | /admin/posts | 帖子审核列表 |
| POST | /admin/posts/{id}/audit | `{ "pass": true, "reason": "" }` |
| GET | /admin/materials | 资料审核列表 |
| POST | /admin/materials/{id}/audit | 同上 |
| GET | /admin/reports | 举报列表 |
| POST | /admin/reports/{id}/handle | 处理举报 |
| GET | /admin/users | 用户列表 |
| POST | /admin/users/{id}/ban?banned=true | 封禁/解封 |
| POST | /admin/points/{userId}/adjust | `{ "amount": 100, "remark": "补发" }` |

`token` 留空时所有 `/admin/**` 返回 403。

---

## 5. JWT（必改生产）

```yaml
schoolshop:
  jwt:
    secret: "至少32字符的随机字符串"
    expire-hours: 168
```

生产环境务必更换 `secret`，可用：

```bash
openssl rand -base64 32
```

---

## 6. MySQL

```yaml
spring:
  datasource:
    username: root
    password: "你的密码"
```

初始化：

```bash
mysql -u root -p < src/docs/init.sql
mysql -u root -p schoolshop < src/docs/init-v11.sql
```

---

## 7. 定时任务（无需 Key）

```yaml
schoolshop:
  task-job:
    enabled: true
    auto-confirm-hours: 48      # 待验收超时自动确认
    cancel-expired-recruiting: true  # 过 deadline 招募中任务取消并解冻积分
```

---

## 8. 健康检查与集成状态

`GET /api/health` 返回示例：

```json
{
  "status": "UP",
  "integrations": {
    "wechat": false,
    "oss": false,
    "ai": false,
    "admin": false
  }
}
```

用于确认哪些外部服务已真正启用（不暴露密钥）。

---

## 9. 上线 Checklist

- [ ] `application-local.yml` 已配置且未提交 Git
- [ ] `wechat.mock-enabled: false`
- [ ] JWT secret 已更换
- [ ] HTTPS + 小程序合法域名
- [ ] OSS 公共/私有 Bucket 权限正确
- [ ] `content-check-mode: wechat`
- [ ] `admin.token` 已设置且仅内网/运维使用
- [ ] 执行 `init.sql` + `init-v11.sql`

---

## 10. 常见问题

**Q：微信登录报「微信登录未配置」**  
A：填写 `app-id` / `app-secret`，或开发阶段保持 `mock-enabled: true`。

**Q：上传图片 URL 无法访问**  
A：检查 `oss.enabled` 与 `public-base-url`，或将 OSS 域名加入小程序合法域名。

**Q：AI 仍是固定回复**  
A：确认 `ai.enabled: true` 且 `api-key` 非空；查看后端日志是否有 LLM 调用错误。

**Q：Admin 403**  
A：配置 `admin.token` 并在请求头携带 `X-Admin-Token`。
