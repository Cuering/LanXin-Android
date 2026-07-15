# builtin/unified_search — 统一搜索

> 模块：`com.lanxin.android.builtin.unifiedsearch`  
> 状态：Phase 4.4 / 4.5 ✅

## 职责

将 memory / knowledge / chat / unified_inbox 四路检索合一，RRF(k=60) 融合排序后 Top-8 注入 prompt。

## 检索管道

```
用户输入
  ├──→ memory/         MemoryRepository.searchForInject()
  ├──→ knowledge/      VectorPipeline.searchHybrid(source=knowledge)
  ├──→ chat/           ChatHistoryProvider.search()
  └──→ unified_inbox/  CrossSessionRepository.searchForInject()
  │
  ↓ 并行 + 单路超时 2s 降级
  ↓ RRF: score = Σ 1/(k + rank_i), k=60
  ↓ Top-8 注入
```

## 注入格式

```
[统一参考]
━━━ 记忆 ━━━
- [类型] {内容}
━━━ 知识 ━━━
- {知识片段}
━━━ 聊天历史 ━━━
- [{时间}] {摘要}
━━━ 跨会话 ━━━
- [{平台}/{会话}] {消息}
━━━ 参考结束 ━━━
```

零结果路由自动跳过。

## 关键类

| 类 | 说明 |
|----|------|
| `UnifiedSearchService` | 四路并行 + RRF + inject |
| `UnifiedSearchScreen` | 设置/试搜页，展示各路由命中数 |
| `UnifiedSearchViewModel` | 开关、试搜状态 |

## Chat 集成

`ChatViewModel.injectMemoryIntoLastUserMessage` 优先走 `UnifiedSearchService.inject()`；
关闭统一搜索时回退到既有 `MemoryInjector`。
