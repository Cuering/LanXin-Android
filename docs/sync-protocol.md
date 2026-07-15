# LanXin ↔ AstrBot 同步协议草案（Phase 5.1）

> 状态：草案 / 进行中（`feat/phase5-sync`）  
> 范围：knowledge / memory 双向增量同步底座  
> 非目标：聊天消息同步、会话污染、全量备份替代

---

## 1. 身份模型

| 字段 | 说明 |
|------|------|
| `device_id` | 设备稳定 UUID，App 首次启动生成并持久化（DataStore） |
| `user_id` | 可选。AstrBot 侧用户/工作区标识；未配置时服务端可按 token 推导 |
| `auth` | HTTP `Authorization: Bearer <token>`（与现有 GitHub/API 风格一致） |

请求头建议：

```
Authorization: Bearer <token>
X-LanXin-Device-Id: <device_id>
X-LanXin-Client: android/<app_version>
Content-Type: application/json
```

---

## 2. 同步条目 `SyncItem`

| 字段 | 类型 | 必填 | 说明 |
|------|------|:----:|------|
| `id` | string | ✅ | 全局稳定 ID。App 本地 memory 可用 `memory:<localId>`；新建建议 UUID |
| `type` | string | ✅ | `memory` \| `knowledge` |
| `content` | string | ✅ | 正文；tombstone 时可为空串 |
| `updated_at` | long | ✅ | epoch 毫秒；冲突比较主键 |
| `deleted` | bool | ✅ | tombstone；true 表示已删除 |
| `source` | string | ✅ | 来源：`android` / `astrbot` / 自定义插件 id |
| `subtype` | string? | | memory 子类型：preference/factual/…；knowledge 可放 chunk 分类 |
| `importance` | float? | | 1–10，memory 用 |
| `metadata` | string? | | JSON 字符串，原样透传 |
| `created_at` | long? | | 创建时间（毫秒） |
| `device_id` | string? | | 最后写入设备 |

**tombstone 规则**：删除不物理抹除远程历史，而是 push `deleted=true` + 更新 `updated_at`。

---

## 3. 增量同步

### 3.1 Pull — `POST /api/sync/pull`

请求：

```json
{
  "device_id": "…",
  "user_id": "…",
  "since": 1710000000000,
  "cursor": null,
  "types": ["memory"],
  "limit": 200
}
```

| 字段 | 说明 |
|------|------|
| `since` | 客户端上次成功同步的 server_time / 本地水位（毫秒）。服务端返回 `updated_at > since` 或含 tombstone |
| `cursor` | 可选分页游标；与 `since` 二选一优先 cursor |
| `types` | 过滤类型；空/缺省 = 全部 |
| `limit` | 默认 200，上限建议 1000 |

响应：

```json
{
  "items": [ /* SyncItem[] */ ],
  "next_cursor": "opaque-or-null",
  "server_time": 1710000001000,
  "has_more": false
}
```

客户端应持久化 `server_time`（或 cursor）作为下次 `since`。

### 3.2 Push — `POST /api/sync/push`

请求：

```json
{
  "device_id": "…",
  "user_id": "…",
  "items": [ /* SyncItem[] from outbox */ ]
}
```

响应：

```json
{
  "accepted": 3,
  "rejected": [
    { "id": "memory:9", "reason": "validation_error" }
  ],
  "applied": [ /* 服务端最终态（可选，LWW 后） */ ],
  "server_time": 1710000002000
}
```

### 3.3 推荐同步顺序

```
1. push outbox（本地未上传变更）
2. pull since last_server_time
3. 合并远端（LWW，见 §4）
4. 更新 cursor / server_time
5. 清理已 ack 的 outbox
```

---

## 4. 冲突策略（5.2 占位，接口预留）

**默认：LWW（Last-Write-Wins）**

比较顺序：

1. `updated_at` 较大者胜
2. 相等时：`deleted=true` 优先于未删除（防复活）
3. 仍相等：`source` 字典序较大者胜（稳定 tie-break）
4. 仍相等：保留 `id` 对应的本地或远端由实现指定（`preferRemote` 开关）

App 侧：`LwwResolver.pick(local, remote)`  
服务端应对 push 做同等规则，并在 `applied` 回传最终态。

> 5.2 将补齐：字段级合并、importance 保护、用户手动 resolve UI。

---

## 5. 与现有模块映射

### 5.1 Android Memory

| SyncItem | MemoryEntity |
|----------|--------------|
| `id` | `memory:{id}`（本地 Long）或 metadata 中的 `sync_id` |
| `type` | 固定 `memory` |
| `subtype` | `MemoryEntity.type` |
| `content` | `content` |
| `updated_at` | `lastAccessedAt ?: createdAt`（后续可加 `updated_at` 列） |
| `deleted` | outbox 删除事件 / 本地已删 |
| `importance` | `importance` |
| `metadata` | `metadata` |
| `created_at` | `createdAt` |

**不走聊天通道**：同步仅 HTTP `/api/sync/*`，不注入 ChatViewModel / 会话消息。

### 5.2 Android Knowledge

| SyncItem | 端侧知识 |
|----------|----------|
| `type` | `knowledge` |
| `id` | `knowledge:{vectorId}` 或文档 chunk 稳定哈希 |
| `content` | chunk 文本 |
| `metadata` | 源文件、页码、embedding 版本等 JSON |

5.1 仅预留接口；knowledge 适配器可后置。

### 5.3 AstrBot 对接注意点（文档，不改系统源码）

建议 endpoint（AstrBot 插件或路由实现）：

| Method | Path | 职责 |
|--------|------|------|
| POST | `/api/sync/pull` | 按 since/cursor 返回 memory + knowledge |
| POST | `/api/sync/push` | 接收 outbox，LWW 写入后回执 |

字段映射：

- **memory_system_manager**：`content` / tags / importance / timestamps → SyncItem；删除走 tombstone
- **knowledge**：chunk 级同步；向量可在端侧重算（同步正文即可，embedding 本地重建）
- 鉴权：复用 AstrBot API token / 会话 token；校验 `device_id` 绑定

本仓库 **不修改** `/AstrBot/astrbot` 系统源码；服务端实现可放独立插件或后续 PR。

---

## 6. Outbox（本地变更队列）

本地对 memory 的 add/update/delete 先写入 outbox：

```
SyncOutboxEntry {
  local_id: Long auto
  item_id: String        // SyncItem.id
  payload_json: String   // 完整 SyncItem
  op: upsert | delete
  created_at: Long
  attempts: Int
  last_error: String?
}
```

push 成功（accepted）后删除对应 outbox 行；rejected 保留并递增 attempts。

---

## 7. 错误与幂等

- 同一 `id` 重复 push：服务端 LWW，幂等
- 网络失败：outbox 保留，指数退避（5.1 仅记录 attempts）
- `401/403`：停止自动同步，提示配置 token
- 时钟漂移：以 `server_time` 为水位，不信任纯本地 wall clock 作为全局序

---

## 8. 版本

| protocol_version | 说明 |
|------------------|------|
| 1 | 本文草案：pull/push + LWW 占位 + memory 优先 |
