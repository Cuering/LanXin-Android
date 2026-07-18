# 摄像头 → 场景识别（Scene Sensing / M5 最小）

> 状态：**配置 + 确认 Gate + 本地启发式 + 陪伴背景接线 ✅**（#99 / `feat/camera-scene-recognition`）  
> 模块：`builtin/platform` · DataStore 键 `scene_sensing_*` · 设置路由 `Route.SCENE_SENSING`  
> **不**改 mood 标签协议（#98）与关键词映射（#97）；**不**发明 Live2D exp/motion。  
>
> ⚠️ **产品澄清**：本页是 **Gate/权限 + 单次手动拍 + 本地氛围映射**，**不是**「边拍边看 + 视觉讲解」。  
> 真目标见 [`docs/companion-vision-explain.md`](./companion-vision-explain.md)（全屏陪伴 · 多模态讲解 · 未开工）。

## 1. 目标

| 层 | 行为 |
|----|------|
| **默认** | 全关：不申请相机、不拍、不识别、不改背景 |
| **确认 Gate** | 首次开启必须弹窗同意隐私说明 → `consentGranted` |
| **权限** | 仅点「识别当前场景」时按需申请 `CAMERA`（系统预览快照） |
| **识别** | 本地颜色启发式（无 ML、无上传）→ `SceneLabel` |
| **落地** | 仅映射现有 `CompanionBackgrounds` 预设 + 可选 `MoodTagMapper` 合法 mood 提示 |

## 2. 能力边界

- **有**：手动快照 · 本机分类 · 写陪伴背景预设 · 缓存最近 scene/status
- **无**：后台偷拍 · 持续预览流 · 上传原图 · UsageStats/截屏 · 新 exp/motion · Agent 工具暴露（本 PR 不做 system tool）

## 3. 映射（仅现有资源）

| scene | 背景预设 | mood 提示 |
|-------|----------|-----------|
| daylight | sky | smile |
| night | night | idle |
| sunset_warm | sunset | joy |
| green_nature | mint | smile |
| cool_indoor | lavender | think |
| unknown | （不改） | — |

## 4. 代码位置

```
app/.../builtin/platform/
  domain/SceneSensingConfig|Gate|Settings
  domain/LocalSceneClassifier · SceneCaptureAnalyzer
  data/SceneSensingPreferences
  presentation/SceneSensingScreen · SceneSensingViewModel
  di/PlatformModule  # Binds SceneSensingSettings
```

设置 → **场景识别**；识别成功时 `PetSettings.setCompanionBackground` 写预设，陪伴页读既有配置即可。

## 5. 单测 / CI

- `SceneSensingGateTest` · `LocalSceneClassifierTest`
- Workflow：`.github/workflows/scene-sensing-verify.yml`
