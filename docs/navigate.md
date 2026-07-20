# 导航 Navigate

> **状态：V1 插件形态 · 默认 OFF** · 分支 `feat/navigate-guide-plugins-default-off`  
> **独立模块**，与「导游 Guide」拆开；**不要**做成 monolithic `ScenicGuide` / `realtime-local-guide`。

## 1. 职责

| 能力 | 说明 | V1 |
|------|------|----|
| 附近 POI | 出口 / 洗手间 / 电梯 / 餐饮 / 酒店 / ATM / 药店 / 停车 | `nearby_poi` ✅ Overpass |
| 外链导航 | 调起高德 / 百度 / geo / Google，**不做**自研 turn-by-turn | `open_navigation` ✅ |
| 酒店价 | 联网搜索摘要价位（非订房闭环） | `hotel_price_lookup` ✅ |

**不做**：后台轨迹、无确认摄像头、与导游共用一个大开关、默认开启。

## 2. 插件与默认关

| 项 | 值 |
|----|-----|
| 插件 id | `lanxin.navigate`（NavigatePlugin） |
| 默认 | **OFF**（`NavigateConfig.DEFAULT_ENABLED = false`） |
| PluginManager | `register(NavigatePlugin, defaultEnabled=false)` → plugin-state 首次落盘 `false` |
| DataStore | `smart_capabilities_navigate`（智能能力子开关，默认 false） |
| 门闸 | `NavigateGate.filterTools(pluginEnabled, master, location, web)` |

**关时**：三工具不进 Agent 列表、filterTools 剔除、不调 Overpass/外链、不主动要定位。  
**get_location** 仍在 `PlatformPlugin`（位置能力），不随导航默认关而消失。

## 3. 开启步骤

1. 设置 → **智能能力** → 打开 **主开关**（若关）
2. 打开 **导航** 子开关（默认关）
3. 附近 POI / 酒店价另需：**位置** ON + **联网搜索** ON + 运行时定位权限
4. 重启对话后 Agent 可见 `nearby_poi` / `open_navigation` / `hotel_price_lookup`

也可在插件管理器对 `lanxin.navigate` 启用（与智能能力开关同步）。

## 4. 入口

- **对话工具**（主）：Agent 调三工具（仅插件开后）
- **设置**：智能能力页「导航」开关（默认关）
- **不**占用全屏陪伴「看世界」入口

## 5. 共享底层（不复制）

- `get_location`（`capabilities` / LocationGate）
- `web_search`（联网）
- 权限 Gate / 智能能力 master
- 外链 Intent（本模块 open_navigation）

## 6. 代码

```
builtin/navigate/
├── NavigatePlugin.kt          # id=lanxin.navigate
├── di/NavigateModule.kt       # PluginManager.register defaultEnabled=false
├── domain/
│   ├── NavigateConfig.kt      # DEFAULT_ENABLED=false · PLUGIN_ID
│   ├── NavigateGate.kt        # filterTools(pluginEnabled, master, location, web)
│   ├── PoiCategory.kt · GeoMath.kt · NavigationUriBuilder.kt
│   ├── OverpassPoiParser.kt · HotelPriceHints.kt
│   └── LocalAssistIntentRouter.kt
└── tools/
    ├── NearbyPoiTool.kt · OpenNavigationTool.kt · HotelPriceTool.kt
```

注册：`NavigateModule` → `NavigatePlugin`（**非** PlatformPlugin）；`LanXinApp` 注入 `NavigatePluginRegistration`。  
门闸：`ChatViewModel` → `ChatSendToolFilterLogic` → `NavigateGate.filterTools(pluginEnabled=smart.navigateEnabled, …)`。  
单测：`NavigateDomainTest` + `ChatSendFailureLogicTest`（默认 OFF 不可见 / ON 后可用）。  
CI：`.github/workflows/navigate-verify.yml`。

## 7. 与导游

| | 导航 Navigate | 导游 Guide |
|--|---------------|------------|
| 用户说法 | 出口/厕所/酒店价/带我去 | 这是什么/讲讲景点 |
| 默认 | **OFF** | **OFF** |
| 相机 | 一般不需要 | 可联动「看世界」 |
| 交付 | 插件 V1 | 插件 V1 |
| 跳转 | 导游讲完「去这里」→ 调导航 | 导航到景点后可提示开导游 |

## 8. 交互示例（需先开导航）

- 「附近厕所」→ `get_location` + `nearby_poi(restroom)` → 距离/方位列表
- 「带我去最近出口」→ POI + `open_navigation`
- 「这附近酒店多少钱」→ 定位粗地址 + `hotel_price_lookup` / web_search

## 9. 隐私

- 用时定位（`get_location` / last known），无后台轨迹
- 默认关：不调 Overpass、不主动要定位
- POI/酒店价依赖联网；`open_navigation` 仅外链 Intent
