# P3：Live2D Cubism 真渲染引擎

> 相关：[`meiju-style-pet.md`](./meiju-style-pet.md) · [`live2d-mao-sample.md`](./live2d-mao-sample.md) · [`debug-assets.md`](./debug-assets.md)

## 目标

将 **Cubism Web 运行时**接进 `desktop-pet.html`，真 load `*.moc3` + 纹理，替代「只叠贴图的轻晃壳」。失败仍降级壳/占位，不崩。

## 架构

```
Native (Kotlin)
  Live2dDisplayController.decide → LIVE2D_SHELL | PLACEHOLDER | FALLBACK
  PetBridgeProtocol.LOAD_LIVE2D (path + fileUrl + dirUrl + model3B64)
        │
        ▼
WebView  file:///android_asset/pet/desktop-pet.html
  lib/live2dcubismcore.min.js   ← Cubism Core
  lib/pixi.min.js               ← PixiJS 6
  lib/cubism4.min.js            ← pixi-live2d-display Cubism4
        │
        ▼
  LIVE2D_REAL  PIXI.live2d.Live2DModel.from(model3Url)
        │ fail
        ▼
  LIVE2D_SHELL  Canvas 叠纹理 / 几何呼吸（M2b 路径）
        │ fail
        ▼
  FALLBACK / PLACEHOLDER
```

## 仓内运行时

| 文件 | 来源 | 许可 |
|------|------|------|
| `assets/pet/lib/live2dcubismcore.min.js` | Live2D Cubism Core redistributable | Live2D proprietary redistributable |
| `assets/pet/lib/pixi.min.js` | PixiJS 6.5.10 | MIT |
| `assets/pet/lib/cubism4.min.js` | pixi-live2d-display 0.4.0 | MIT |
| `assets/pet/lib/NOTICE.txt` | 本仓声明 | — |

大模型资源仍不进 git（用户模型在 `LanXin/live2d/`）；内置 Mao 白名单不变。

## Bridge 协议

| 方向 | command | 要点 |
|------|---------|------|
| N→W | `LOAD_LIVE2D` | `live2dMode=LIVE2D_SHELL` 表示 model3 就绪；Web 优先真 Cubism |
| W→N | `LIVE2D_STATUS` | `live2dMode=LIVE2D_REAL` / `LIVE2D_SHELL` / `FALLBACK` / `PLACEHOLDER` |
| N→W | `SET_EXPRESSION` | 映射 exp + `ParamMouthOpenY`（真渲染）/ canvas 嘴（壳） |

Native 枚举仍以 `LIVE2D_SHELL` 表示「可加载」；**真渲染成功与否以 Web 回传 `LIVE2D_REAL` 为准**。

## 模型路径

1. 用户 `live2d_model_path` → `LanXin/live2d/<name>/*.model3.json`
2. 内置 Mao：`BuiltInLive2dAssets` → `filesDir/builtin-live2d/Mao/` 或 `file:///android_asset/pet/live2d/Mao/`
3. Debug 旁路：`filesDir/debug-assets/live2d/`（仅 Debug）

## 表情 / 口型

相位主映射 01–05；扩展 06–08（闲置变体 / 点触 / 音乐高潮）。清单与映射见 `MaoOfficialMotionCatalog`。

| Expression | Cubism expression（Mao） | 触发 / 口型 |
|------------|--------------------------|-------------|
| IDLE_SMILE | exp_01 | 闲置默认 · ParamMouthOpenY=0 |
| LISTENING | exp_02 | 听用户说 · 0 |
| THINKING | exp_03 | 推理中 · 0 |
| SPEAKING | exp_04 | 播报 · 动画 0.25–0.70 |
| APOLOGY | exp_05 | 出错 · 0 |
| IDLE_VARIANT_A | exp_06 | 闲置随机变体 · 0 |
| TAP_REACTION | exp_07 | 点触短表情 · 0 |
| MUSIC_PEAK | exp_08 | 音乐高潮弱表情 · 0 |
| FALLBACK_NEUTRAL | exp_01 | 降级占位 · 0 |

无对应 exp 时忽略，不抛错。Bridge 附带 `cubismExpression=exp_0N`。

## 动作组（Mao model3）

| Group | 文件 | 行为 |
|-------|------|------|
| Idle | mtn_01, sample_01 | 加载后 / 回 Idle 轮换；SPEAKING 可弱播 |
| TapBody | mtn_02–04, special_01–03 | 点触优先；期间参数律动 `danceWeight` 降权 |

Native → Web：`PLAY_MOTION`（`motionGroup` / 可选 `motionIndex`）。  
Web → Native：`MODEL_TAPPED`（`hitArea`）。  
与 Cubism 参数音乐律动共存：motion 忙时降权，不整模贴纸晃。

## 文本 → 表情 / 动作

SPEAKING 相位下，对 `replyText` / 字幕做映射（无 ML、无网络）。

**优先级**：显式 `[[mood=…]]`（`MoodTagMapper`）→ 关键词（#97）→ 相位默认。

### mood 标签（从现有 exp/motion 反推，禁止发明资源）

| mood | Expression | exp | Motion | 说明 |
|------|------------|-----|--------|------|
| smile | IDLE_SMILE | exp_01 | Idle[0] | 微笑 / greeting 别名 |
| listen | LISTENING | exp_02 | — | 在听 |
| think | THINKING | exp_03 | — | 思考 |
| speak | SPEAKING | exp_04 | — | 说话态表情 |
| sorry | APOLOGY | exp_05 | — | 抱歉 / sad 别名 |
| idle | IDLE_VARIANT_A | exp_06 | — | 闲变 |
| joy | TAP_REACTION | exp_07 | TapBody[3] | 开心；happy/excited 别名 |
| music | MUSIC_PEAK | exp_08 | Idle[1] | 律动 |
| tap | TAP_REACTION | exp_07 | TapBody[0] | 点触邀请 |

生成侧**只允许**上表 mood。无合适动作则只播表情；非法 mood 忽略。  
气泡展示前 `MoodTagMapper.stripTags` 剥离标签。

### 关键词兜底（#97）

| ruleId | 关键词示例 | Expression | Motion |
|--------|------------|------------|--------|
| apology | 抱歉 / 对不起 / 出错 | APOLOGY | — |
| joy | 哈哈 / 太棒 / 喜欢 | TAP_REACTION | TapBody[3] |
| music | 音乐 / 听歌 / BGM | MUSIC_PEAK | Idle[1] |
| tap_invite | 点我 / 摸摸 | TAP_REACTION | TapBody[0] |
| think_tone | 让我想 / 稍等 | THINKING | — |
| greeting | 你好 / hello | IDLE_SMILE | Idle[0] |
| sad | 难过 / 呜呜 | APOLOGY | — |
| idle_variant | 闲着 / 摸鱼 | IDLE_VARIANT_A | — |

实现：`MoodTagMapper` + `TextExpressionMotionMapper`；接入：`FloatingPetService.pushSessionToWeb`、`CompanionViewModel.encodeExpressionRaw/encodePlayMotionRaw/encodeBubbleRaw`。  
同一 `roundId:ruleId` 只推一次 `PLAY_MOTION`，口型保持 SPEAKING。

## 验证

```bash
# 单测
./gradlew :app:testDebugUnitTest --tests 'com.lanxin.android.builtin.pet.*'

# CI
.github/workflows/p3-live2d-cubism-verify.yml
```

Surface checks：lib 三件套 + NOTICE、`desktop-pet.html` 含 Cubism / LIVE2D_REAL、文档与单测存在。

## 非目标

- 音素级 lip-sync / 麦克风实时口型
- 妹居商业 moc3 入库
- 本机全量 `gradlew assemble` 作为交付门槛（CI Debug APK 为准）
- 运行时在线 CDN 拉引擎（全部仓内 assets）
