# Live2D 官方 Sample：Niziiro Mao

| 项 | 路径 / 说明 |
|----|-------------|
| **仓内** | `app/src/main/assets/pet/live2d/Mao/`（约 4.2MB，**可 commit**） |
| **运行时落盘** | `filesDir/builtin-live2d/Mao/`（`BuiltInLive2dAssets.ensureInstalled`） |
| **逻辑路径** | `asset://pet/live2d/Mao/Mao.model3.json` |
| 来源 | [CubismWebSamples / Mao](https://github.com/Live2D/CubismWebSamples/tree/develop/Samples/Resources/Mao) |
| 许可 | [Live2D Sample Data Terms](https://www.live2d.com/en/learn/sample/model-terms) |
| 用途 | 开箱调试 / M2b WebView 壳真 load；非妹居商业资源 |
| 禁止 | 妹居 / 商业 moc3、ASR·TTS 大权重、本地脑权重进 git |

## 运行时

1. APK 打包 `pet/live2d/Mao/**`
2. `BuiltInLive2dAssets.ensureInstalled(context)` → `Context.filesDir/builtin-live2d/Mao/`
3. `PetResourceResolver` / `MeijuDebugPaths.resolveLive2dIfPresent` 优先级：
   - 用户配置 `live2d_model_path`
   - 已安装内置 Sample（绝对路径）
   - debug-assets（脚本旁路）
   - 妹居参考（仅 debug）
   - 逻辑路径 `asset://pet/live2d/Mao/Mao.model3.json`
4. `Live2dDisplayController`：逻辑路径 → `LIVE2D_SHELL` + reason `live2d_builtin_asset`（`file:///android_asset/...`）；落盘后 → `live2d_shell_ready`
5. 设置页：`已就绪（内置示例）` + Sample Terms 许可提示

## 重同步

```bash
bash scripts/vendor-live2d-mao.sh
```

`.gitignore` 默认 ban `*.moc3` / `*.model3.json`，白名单 `app/src/main/assets/pet/live2d/**`。

## 相关

- [`debug-assets.md`](./debug-assets.md) — ASR/TTS 与可选脚本
- [`meiju-style-pet.md`](./meiju-style-pet.md) — 桌宠主线


## P3 渲染

Mao 由 `desktop-pet.html` 经 **Cubism Core** 真渲染（`LIVE2D_REAL`）。运行时 JS 见 `assets/pet/lib/`；说明见 [`live2d-cubism-render.md`](./live2d-cubism-render.md)。
