# 导航 Navigate

> **状态：V1 已实现** · 分支 `feat/navigate-v1`  
> **独立模块**，与「导游 Guide」拆开；**不要**做成 monolithic `ScenicGuide` / `realtime-local-guide`。

## 1. 职责

| 能力 | 说明 | V1 |
|------|------|----|
| 附近 POI | 出口 / 洗手间 / 电梯 / 餐饮 / 酒店 / ATM / 药店 / 停车 | `nearby_poi` ✅ Overpass |
| 外链导航 | 调起高德 / 百度 / geo / Google，**不做**自研 turn-by-turn | `open_navigation` ✅ |
| 酒店价 | 联网搜索摘要价位（非订房闭环） | `hotel_price_lookup` ✅ |

**不做**：后台轨迹、无确认摄像头、与导游共用一个大开关。

## 2. 入口

- **对话工具**（主）：Agent 调 `nearby_poi` / `open_navigation` / `hotel_price_lookup`
- **可选设置**：智能能力旁「导航/周边」分项开关（后续；默认随位置+联网）
- **不**占用全屏陪伴「看世界」入口

## 3. 共享底层（不复制）

- `get_location`（`capabilities` / LocationGate）
- `web_search`（联网）
- 权限 Gate / 智能能力 master
- 外链 Intent（`AppIntentTool` 或本模块 open_navigation）

## 4. 代码

```
builtin/navigate/
├── domain/
│   ├── NavigateConfig.kt / NavigateGate.kt
│   ├── PoiCategory.kt · GeoMath.kt · NavigationUriBuilder.kt
│   ├── OverpassPoiParser.kt · HotelPriceHints.kt
│   └── LocalAssistIntentRouter.kt  # 导游 vs 导航粗分（提示用）
└── tools/
    ├── NearbyPoiTool.kt · OpenNavigationTool.kt · HotelPriceTool.kt
```

注册：`PlatformPlugin`（导航工具，非导游包）。  
门闸：`ChatViewModel` → `NavigateGate.filterTools`。  
单测：`NavigateDomainTest`。  
CI：`.github/workflows/navigate-verify.yml`（JDK 21 + unit + compileDebugKotlin + assembleDebug）。

## 5. 与导游

| | 导航 Navigate | 导游 Guide |
|--|---------------|------------|
| 用户说法 | 出口/厕所/酒店价/带我去 | 这是什么/讲讲景点 |
| 相机 | 一般不需要 | 可联动「看世界」 |
| 交付 | **#109 已合** | Guide V1（`docs/guide.md`，看世界 + 位置增强） |
| 跳转 | 导游讲完「去这里」→ 调导航 | 导航到景点后可提示开导游 |

## 6. 交互示例

- 「附近厕所」→ `get_location` + `nearby_poi(restroom)` → 距离/方位列表
- 「带我去最近出口」→ POI + `open_navigation`
- 「这附近酒店多少钱」→ 定位粗地址 + `hotel_price_lookup` / web_search

## 7. 隐私

- 用时定位（`get_location` / last known），无后台轨迹
- POI/酒店价依赖联网；`open_navigation` 仅外链 Intent
