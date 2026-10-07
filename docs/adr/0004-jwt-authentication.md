# 0004 · JWT + Spring Security 无状态鉴权

> **产品线决策文档**：本文回答"为什么这样选"。
> **实现差距见 [ADR-0007](./0007-jwt-auth-and-tenant-context.md) 与 [`../security-findings.md`](../security-findings.md) S-01~S-03**（6 处失效点的逐行事实）。

| 项 | 内容 |
|---|---|
| 状态 | 已采纳（**核心校验逻辑待修复**） |
| 日期 | 2026-01-25 |
| 决策者 | 项目维护者 |
| 相关文档 | [`../spec/tenant-and-iam.md`](../spec/tenant-and-iam.md) 第 5 节、[`../api/README.md`](../api/README.md) 第 4 节、[`../qa/known-issues.md`](../qa/known-issues.md) ISS-007 / ISS-008 / ISS-021 |

---

## 背景

前后端完全分离（两个 React 前端 + 一个 REST 后端），需要一种跨端、跨域、便于横向扩展的认证方式；同时要在令牌中携带租户与角色信息，为多租户隔离（ADR-0003）与 RBAC 授权提供上下文。

## 候选方案

| 方案 | 优点 | 缺点 |
|---|---|---|
| A. Session + Cookie | 服务端可即时失效；实现简单 | 跨域与水平扩展需要额外设施（粘性会话/共享 Session）；与双前端不同域名部署不友好 |
| **B. JWT + Spring Security 无状态（选定）** | 无状态易扩展；令牌自带 claims（用户/租户/角色）；适合多端调用 | 令牌颁发后难失效（需额外黑名单）；签名密钥需妥善保管；实现不当容易"看起来有鉴权实际没有" |
| C. OAuth2 / OIDC 自建授权中心 | 标准、可对接第三方登录 | 当前阶段引入成本高、收益不足 |

## 决策

采用**选项 B**：HS512 签名的 JWT，通过 Spring Security 的无状态（STATELESS）过滤器链校验。

| 约定 | 现行值 |
|---|---|
| 算法 | HS512（jjwt 0.12.5） |
| 令牌有效期 | 24 小时（`jwt.expiration=86400000`） |
| 刷新令牌有效期 | 24 小时（⚠️ 与代码注解中"默认 7 天"不一致） |
| 密码加密 | `BCryptPasswordEncoder` |
| Session 策略 | `STATELESS` |
| CSRF | 关闭（无 Cookie 会话） |
| CORS | 当前仅允许 `http://localhost:5173` |
| 授权规则 | `/api/auth/**` 放行；`/api/platform/**`、`/api/tenants/**` 需 `ROLE_PLATFORM_ADMIN`；其余 `/api/**` 需认证 |
| 过滤器 | `JwtAuthenticationFilter` 挂在 `UsernamePasswordAuthenticationFilter` 之前 |

## 理由

- 无状态适合未来的多实例部署；
- claims 可承载 `tenantId`，正好满足 ADR-0003 的租户上下文传递需求；
- Spring Security + jjwt 生态成熟，迁移到更强的算法或接入外部 IdP 的成本可控。

### ⚠️ 当前落地状态

| 环节 | 现状 |
|---|---|
| 登录签发令牌 | ✅ 正常（`AuthController#login`）——🔎 2026-01-25 实测令牌含 **4 个 claim**：`username`、`tenantId`、`roles`、`permissions` |
| ~~签名校验~~ | ~~❌ `validateToken()` 恒返回 `true` —— 任意非空 Bearer 串均可通过（ISS-007）~~ → ✅ **2026-01-25 已修**：`JwtTokenProvider.java:189-191` 现为 `parseClaims(token) != null`，`:196` 走 `Jwts.parser()` 验签 + 过期，非法/过期返回 null，空串先拒（`:197`） |
| ~~解析用户~~ | ~~❌ `getUsernameFromToken()` 恒返回 `""`~~ → ✅ 已从 claim 读取 |
| ~~解析租户~~ | ~~❌ `getTenantIdFromToken()` 恒返回 `"default-tenant"`~~ → ✅ **`:141-144`** 从 claim 取；写入 `TenantContext`（`JwtAuthenticationFilter:51`）并在 `finally` 清理（`:82`） |
| ~~生成 authorities~~ | ~~❌ 空列表 → 角色授权规则永不可能满足（ISS-008）~~ → ✅ **2026-01-25 已修（ISS-041 的修法）**：过滤器按令牌的 `roles`/`permissions` 重建 authorities（`:46-72`），与 `CustomUserDetailsService:57-73` 的 `ROLE_<name>` + 权限点口径一致；`admin` 实测 `GET /api/tenants` **200**（此前恒 403，根因见 ISS-061） |
| ~~依赖注入~~ | ~~❌ `JwtAuthenticationFilter` 内部 `new JwtTokenProvider()` 绕过 Spring~~ → ✅ **已改构造注入**（`:31-35`），`@Value` 的密钥/有效期在校验路径上生效（M-01 已修） |
| 密钥管理 | ❌ **仍未修**：明文写在受版本控制的 `application.yml:79`（🔄 **2026-03-22 行号订正：现 `application.yml:93`**，`jwt.secret` 自 10-03 平台轮加入 `platform.credential.master-key`（`:60`）等配置后整体下移）（ISS-021/S-04）。本轮明确边界**不做外部化**，作为已知接受风险记录 |
| CORS | ~~⚠️ 未包含 admin-web 的 `:5174`（ISS-018）~~ → ✅ 已包含：`SecurityConfig:50` = `List.of("http://localhost:5173","http://localhost:5174")`（仍硬编码、未外部化） |
| 刷新/登出 | ~~⚪ 桩实现~~ → 🟡 **2026-01-25 复测：刷新已真实、登出仍是无状态**。<br>✅ `POST /api/auth/refresh`（`AuthController:93-130`）现在会 `validateToken(refreshToken)`（`:99`）、取 `username`（`:103`）、重载用户并签新 access token，空串/无效各自报错。<br>❌ 三点残留：① **不校验旧 refresh token 是否被使用/撤销**（`:128` 原样把同一个 `refreshToken` 回给客户端）→ 无轮换、无撤销；② `jwt.refresh.expiration` 与 access 同为 `86400000`（24h），刷新等于续命 24h；③ `POST /api/auth/logout`（`:136-139`）只有 `SecurityContextHolder.clearContext()`，**JWT 无黑名单**，已签出的 access token 在其有效期内仍然可用。<br>④ 另外 `:34 @EnableMethodSecurity` 已开启，本 ADR 若引用「方法级注解失效」的结论现在不成立 |

## 后果

### 正面

- 结构清晰：登录 → 签发 → 过滤器校验 → SecurityContext，符合常见实践；
- 后续接入刷新令牌、黑名单、多因子都可以在同一链条上扩展。

### 负面 / 需要承担的成本

- 令牌一旦签发，在有效期内无法强制失效（需引入短有效期 + 刷新 + 黑名单组合）；
- 安全完全依赖实现正确性：当前实现处于"演示级"，不可用于真实数据。

### 必须遵守的约束与待办

| # | 待办 | 优先级 | 关联 |
|---|---|---|---|
| 1 | 实现真实的签名校验与过期判断（`Jws` 解析 + `exp` 比较） | **P0** | ISS-007 |
| 2 | Filter 改为构造器注入 `JwtTokenProvider`，删除手动 `new` | **P0** | ISS-007 |
| 3 | 从 claims 解析 `username`/`tenantId`/`roles` 并生成 authorities | **P0** | ISS-008 |
| 4 | 密钥与有效期改为环境变量 / 外部配置中心，且不再进入版本库 | **P0** | ISS-021 |
| 5 | 统一 `jwt.refresh.expiration` 配置与代码注释的一致性 | P1 | — |
| 6 | 实现刷新令牌与登出（黑名单或版本号机制） | P1 | US-904 |
| 7 | 补充 CORS 来源管理并为 admin-web 开放 `:5174` | P2 | ISS-018 |
| 8 | 记录登录/登出的审计日志 | P2 | BR-12 |

## 复审触发条件

- 需要跨多个后端服务共享身份（→ 引入统一认证服务 / OIDC）；
- 客户要求 SSO、强制二次验证或细粒度审计（→ 引入 IdP）；
- 出现令牌失效/踢出线上的强需求（→ 引入黑名单 + 短期令牌）。
