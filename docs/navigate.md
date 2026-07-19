# 导航 Navigate

> **状态：V1 骨架（domain 纯逻辑）** · 分支 `feat/navigate-v1`  
> **独立模块**，与「导游 Guide」拆开；**不要**做成 monolithic `ScenicGuide` / `realtime-local-guide`。

## 1. 职责

| 能力 | 说明 | V1 |
|------|------|----|
| 附近 POI | 出口 / 洗手间 / 电梯 / 餐饮 / 酒店 / ATM / 药店 / 停车 | `nearby_poi`（待接线） |
| 外链导航 | 调起高德 / 百度 / geo / Google，**不做**自研 turn-by-turn | `open_navigation` |
| 酒店价 | 联网搜索摘要价位（非订房闭环） | `hotel_price_lookup` |

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
│   ├── NavigateConfig.kt       # 工具名与半径/条数默认
│   ├── PoiCategory.kt          # OSM Overpass 类别
│   ├── GeoMath.kt              # 距离/方位
│   ├── NavigationUriBuilder.kt # 外链 URI
│   └── LocalAssistIntentRouter.kt  # 导游 vs 导航粗分（提示用）
└── tools/                      # V1 接线：NearbyPoiTool 等（待落地）
```

单测：`NavigateDomainTest`。

## 5. 与导游

| | 导航 Navigate | 导游 Guide |
|--|---------------|------------|
| 用户说法 | 出口/厕所/酒店价/带我去 | 这是什么/讲讲景点 |
| 相机 | 一般不需要 | 可联动「看世界」 |
| 交付 | **先** V1 | 后 V1（复用 companion vision） |
| 跳转 | 导游讲完「去这里」→ 调导航 | 导航到景点后可提示开导游 |

## 6. 交互示例

- 「附近厕所」→ `get_location` + `nearby_poi(restroom)` → 距离/方位列表
- 「带我去最近出口」→ POI + `open_navigation`
- 「这附近酒店多少钱」→ 定位粗地址 + `hotel_price_lookup` / web_search
