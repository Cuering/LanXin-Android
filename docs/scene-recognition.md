# 场景识别（摄像头 → 轻量场景）

> 状态：**配置 + 确认 Gate ✅**（`feat/scene-recognition-camera-gate`）  
> 模块：`builtin/pet` · DataStore `scene_recognition_enabled` / `scene_recognition_consent`  
> **默认关**；开启需用户明确确认隐私说明 + 系统 CAMERA 权限；关闭即清会话缓存。

## 1. 目标

最小可用「摄像头 → 场景标签 → 轻量文案反馈」：

| 层 | 行为 |
|----|------|
| **设置** | 设置 → 场景识别：总开关默认 **false** |
| **确认 Gate** | 开启前 AlertDialog 隐私说明；确认后才写 consent + enabled |
| **权限** | 系统 `CAMERA`；拒绝则不开 |
| **采集** | 仅用户主动「试识别」；启发式亮度/色温，无 ML 上传 |
| **缓存** | 仅内存 `SceneRecognitionSession`；关闭/清缓存丢弃 |
| **Live2D** | **不**硬绑不存在的 exp/motion |

## 2. 代码位置

```
app/.../builtin/pet/
  domain/
    SceneRecognitionConfig.kt / Gate / Settings / Session
    SceneRecognizer.kt / SceneCaptureCoordinator.kt
  data/SceneRecognitionPreferences.kt
  presentation/SceneRecognitionScreen.kt / ViewModel
  di/PetModule.kt  # Binds Settings + Recognizer
```

## 3. 默认与隐私

- **默认关**、**默认未确认**
- 不后台偷拍、不落盘原图、不上传
- 可随时关闭并清会话缓存

## 4. 单测

- `SceneRecognitionGateTest`：默认值、Gate、关闭清理、启发式标签、Coordinator 链路
