# 全屏陪伴 · 视觉讲解（真·场景识别）

> **状态：V1 已实现（提问抓帧 + 多模态接线）** · 分支 `feat/companion-vision-explain`  
> **产品归属：导游 Guide**（`docs/guide.md`），**不是**导航 Navigate（`docs/navigate.md`）。两模块拆开，勿揉成 ScenicGuide。  
> **勿与 #99 混淆。** #99（`docs/scene-sensing.md`）= 默认关 + 确认 Gate + **单次手动拍照** + 本地颜色启发式 → 换背景/mood。  
> **本 epic** = 打开摄像头后**边看边讲**，桌宠/兰心根据画面内容结合用户提问讲解；落在**全屏陪伴**里。

## 0. 与 #99 关系

| | #99（已合，底座） | 本 epic（V1） |
|--|------------------|--------------|
| 入口 | 设置 → 场景识别 | **全屏陪伴页**「看世界」开关 |
| 取景 | 用户点一次拍照 | 预览 PiP（CameraX），非后台偷拍 |
| 识别 | 本地颜色启发式 | **云端多模态**描述/问答 |
| 落地 | 换背景 / mood | **对话讲解**（帧注入当轮请求） |
| 复用 | Gate、consent、CAMERA 按需、默认关 | 全部复用；consent 共用 DataStore |

## 1. 入口与 UI（✅）

1. **全屏陪伴**（`CompanionScreen`）顶栏：**「看世界」Switch**（默认关，会话级不持久化）
2. 首次开 → 复用 #99 `consentGranted` 隐私确认（同文案扩展：预览可见、提问可能上传缩略帧）
3. 开后：右上角 **CameraX 画中画预览** + 角标「正在看」+「看一眼」
4. 关开关 / `onLeavePage`：**立即**停预览（Compose dispose unbind）
5. **禁止**：无确认后台常开；无预览的静默取帧

## 2. 帧策略 P0（✅）

```
用户提问 / 点「看一眼」
  → 若「看世界」开且 consent + CAMERA
  → 从 ImageAnalysis 内存缓存抓 1 帧
  → 降采样 JPEG 边长 ≤768，Base64 仅内存
  → 送多模态 Provider（OpenAI 兼容 image_url）
  → 本轮回复结合画面讲解
```

- 无提问不上传、不分析
- 失败降级：仅文本 stub 聊 + 短提示「这轮没看清」/「当前模型看不了图」

## 3. 多模态（✅ V1 范围）

| 优先级 | 路径 | 说明 |
|--------|------|------|
| 1 | **云端多模态 Provider** | 已配置 enabled 的 PlatformV2；模型名启发式 vision + OpenAI 兼容路径 |
| 2 | 无可用视觉 | `VisionModelCapability.MSG_NO_VISION` 等明确文案，**禁止假装本地 VLM** |

代码：

```
builtin/pet/domain/
  CompanionVisionSession      # 门闸：looking + consent + camera
  VisionModelCapability       # 模型/路径是否可看图
  CompanionVisionFrame(+Encoder)
  VisionExplainClient
  OpenAiVisionExplainClient   # chat/completions + image_url
presentation/
  CompanionCameraPreview      # CameraX PiP + 内存帧缓存
  CompanionScreen             # 开关 / 确认 / 提问抓帧接线
```

## 4. 隐私（✅）

- 默认关；Gate 确认文案写清预览与可能上传缩略帧
- 帧**不落盘**；不写 debug 日志原图
- 关闭开关 / 离开全屏陪伴：停预览
- 设置里可撤回 consent（对齐 #99 `SceneSensingPreferences.setConsentGranted(false)`）

## 5. 明确不做（仍遵守）

- 无确认后台常开摄像头  
- 发明 Live2D 资源 / 新 exp·motion / 改 #98 mood 协议  
- 把本地启发式包装成「真视觉理解」  
- 常开后台抽帧（P1）  
- Anthropic/Google 原生视觉路径（V1 仅 OpenAI 兼容 image_url）

## 6. 单测 / CI

- `CompanionVisionSessionTest`：默认关、Gate、抓帧条件
- `VisionModelCapabilityTest`：无 vision 不瞎编、body 含 image_url
- Workflow：`.github/workflows/companion-vision-explain-verify.yml`

## 7. 后续切片

| | 内容 |
|--|------|
| P1 | 变化抽帧缓存；Anthropic/Gemini 原生路径 |
| P2 | 与 ChatRouter 正式统一；可选「记住这一眼」 |
