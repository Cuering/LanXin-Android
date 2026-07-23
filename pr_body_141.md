## 改动

### 1. 折叠本地脑 phrase loop
- `LocalReplySanitizer`: 检测连续重复回复，折叠为单条提示
- JNI UTF-8 边界缓冲修复
- 删会话/欢迎页 → 全屏陪伴路由修复

### 2. 分步引导卡（SetupGuideCard）
- **本地推理页**: 5 步引导（准备模型→启用加载→验证文字→ASR→TTS），每步有 ✅/⭕ 状态 + 行内操作按钮
- **ASR 页**: 4 步清单（导入→加载→权限→试转写）
- **桌宠页**: 就绪清单（Live2D/ASR/TTS/本地脑）+ 跳转按钮
- 三页可互相跳转，用户从模型导入到语音对话一路引导
- 新增「复制诊断信息」卡片，一键复制引擎/路由/路径/错误用于反馈

### 3. 桌宠 FGS 防闪退
- FloatingPetService 前台服务启动异常处理
- Stub 响应不回声，避免复读

### 4. 文档
- `docs/local-brain-setup-guide.md`: 与 App 内引导对齐的详细排查清单

## 文件变更
- `SetupGuideCard.kt` (新增)
- `LocalInferenceScreen.kt`
- `VoiceAsrScreen.kt`
- `DesktopPetScreen.kt`
- `NavigationGraph.kt`
- `docs/local-brain-setup-guide.md`
