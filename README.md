# Relations 插件

![Version](https://img.shields.io/badge/Version-1.0--SNAPSHOT-blue) ![Folia](https://img.shields.io/badge/Folia-Supported-green) ![Java](https://img.shields.io/badge/Java-21-orange)

一个功能丰富的 Minecraft 关系插件，支持结婚、兄弟、闺蜜等多种关系，并提供丰富的亲密度互动功能。
本插件全面支持 **Paper**、**Spigot** 以及 **Folia** 服务端（1.21+），数据库操作全异步处理，确保主线程流畅。

## ✨ 特色功能

*   **多样的关系类型**: 支持伴侣、兄弟、挚友、闺蜜、导师等多种关系，可高度自定义。
*   **Folia 支持**: 完美适配 Folia 多线程环境，利用 Regionized Scheduler 进行高效调度。
*   **亲密度系统**: 通过赠送物品增加亲密度，支持每日上限控制。
*   **婚姻专属**: 
    *   `sethome`/`home` 婚姻家园
    *   `gift` 互相赠送礼物
    *   `tp` 快速传送至伴侣
    *   `kiss` 亲吻特效
*   **现代配色**: 全面支持 MiniMessage 格式，内置统一的现代 RGB 配色方案。
*   **可视化 GUI**: 支持 TrMenu 风格的字符网格布局 (`Layout`)，所见即所得。
*   **高级材质**: 支持 `paper{cmd:1000}` 语法直接定义 CustomModelData，便于资源包适配。
*   **灵活配置**: 支持 SQLite/MySQL 数据库，所有消息均可自定义。

## 🎨 配色方案说明

本插件采用统一的现代 RGB 配色方案，支持以下三种颜色格式混合使用：
1.  **MiniMessage**: `<color:#RRGGBB>`
2.  **Legacy**: `&c`, `&l`
3.  **Hex**: `&#RRGGBB` 或 `{#RRGGBB}`

**默认配色表**:
*   **主文本**: `<color:#E6E6E6>` (浅灰白)
*   **次级/说明**: `<color:#A0A0A0>` (深灰)
*   **标题/重点**: `<color:#FFD479>` (暖金)
*   **成功**: `<color:#6BFF95>` (柔绿)
*   **错误**: `<color:#FF6B6B>` (柔红)
*   **伴侣**: `<color:#FF8FB1>` (粉色)
*   **兄弟**: `<color:#6EC6FF>` (天蓝)

## 🖥️ 高级 GUI 配置

插件支持高度自定义的 GUI 布局，您可以在 `menu.yml` 中使用字符画的形式设计菜单。

### 布局示例 (Layout)

插件支持**单页**和**多页**两种布局模式：

**1. 单页模式** (经典):
```yaml
Layout:
  - '#########'
  - '#A  Z  B#'
  - '#   +   #'
  - '#########'
```

**2. 多页模式** (推荐):
支持为每一页单独定义布局 (列表的列表)，超出定义的页数将自动重复使用最后一页的布局。
```yaml
Layout:
  # 第一页布局
  - - '#########'
    - '#+++++++#'
    - '#+++++++#'
    - '#+++++++#'
    - '#+++++++#'
    - '####N####' # 只有下一页按钮
  # 第二页及后续布局
  - - '#########'
    - '#+++++++#'
    - '#+++++++#'
    - '#+++++++#'
    - '#+++++++#'
    - '###PZ####' # 包含上一页和关闭按钮
```

### 关系列表配置 (Relations)

您可以在 `menu.yml` 的 `relations` 部分自定义关系列表的显示样式，支持为不同类型的关系设置独立的图标和 Lore。

```yaml
relations:
  layout-char: '+' # Layout 中用于显示列表的字符
  
  # 默认类型标题 (如 "兄弟", "伴侣")
  header-item:
    material: BOOK
    name: "<display>"
    lore:
      - "当前: <current>/<max>"

  # 默认成员头像
  member-item:
    material: PLAYER_HEAD
    name: "<display>"
    lore:
      - "亲密度: <affinity>"
      - "日期: <date>"

  # 针对特定类型的覆盖配置
  types:
    marriage:
      header-item:
        material: 'STONE' # 婚姻关系使用石头作为标题图标
        name: "<display> <red>(❤)"
```
```yaml
Icons:
  '#': 
    display:
      material: GRAY_STAINED_GLASS_PANE
      name: " "
  'Z':
    display:
      material: "PAPER{cmd:10001}" # 支持 CustomModelData
      name: "<green>自定义图标"
    actions:
      all: "sound:UI_BUTTON_CLICK-1-1; close"
```

*   **Layout**: 使用字符定义界面，`+` (或 `relations.layout-char` 定义的字符) 代表关系列表的展示槽位。
*   **Icons**: 定义字符对应的物品。
*   **CMD 语法**: 在 material 中使用 `{cmd:123}` 即可快速指定 CustomModelData。
*   **多动作**: `actions` 支持分号分隔的多个动作，如 `sound:xxx; close; console:say hi`。

## 📈 亲密度等级与奖励

在 `config.yml` 中，您可以为每个亲密度等级配置奖励和动作。当玩家关系升级时，将自动执行这些动作。

```yaml
levels:
  1: 0
  2:
    affinity: 500
    actions:
      - "[message] <color:#6BFF95>恭喜！你和 <partner> 的关系达到了 Lv.2！</color>"
      - "[title] <color:#FF8FB1>关系升级！</color>;<color:#FFD866>Lv.2 达成</color>;10;70;20"
      - "[broadcast] <player> 和 <partner> 的关系更进一步了！"
      - "[potion] SPEED;60;1" # 给予速度效果 60秒 1级
  3: 1000
```

**支持的动作类型**:
*   `[op] <command>`: 以 OP 身份执行指令 (慎用)
*   `[player] <command>`: 以玩家身份执行指令
*   `[console] <command>`: 以控制台身份执行指令
*   `[message] <text>`: 发送聊天消息
*   `[broadcast] <text>`: 发送全服公告
*   `[title] <title>;<subtitle>;<in>;<stay>;<out>`: 发送屏幕标题
*   `[potion] <type>;<duration>;<level>`: 给予药水效果 (时长单位: 秒)

**可用占位符**:
*   `<player>`: 玩家名称
*   `<partner>`: 伴侣/对象名称
*   `<level>`: 当前等级
*   `<type>`: 关系类型

## 🧩 PlaceholderAPI 变量

本插件完整支持 PlaceholderAPI，您可以在计分板、聊天或菜单中使用以下变量：

| 变量 | 描述 | 示例 |
| :--- | :--- | :--- |
| `%relations_gender%` | 显示玩家的性别 | `MALE` / `FEMALE` |
| `%relations_marriage_date%` | 显示玩家结婚的时间 | `2023-10-01 12:00:00` |
| `%relations_partner_<type>%` | 显示指定关系类型的对象名称 | `%relations_partner_marriage%` -> `Cinnaio` |
| `%relations_affinity_<type>%` | 显示指定关系的亲密度数值 | `%relations_affinity_brother%` -> `150` |
| `%relations_has_partner_<type>%` | 检查玩家是否有指定关系 (true/false) | `%relations_has_partner_mentor%` -> `false` |
| `%relations_top_name_<type>_<rank>%` | 显示排行榜指定排名的玩家组合 | `%relations_top_name_marriage_1%` -> `PlayerA & PlayerB` |
| `%relations_top_affinity_<type>_<rank>%` | 显示排行榜指定排名的亲密度 | `%relations_top_affinity_marriage_1%` -> `999` |

*注：`<type>` 为 `config.yml` 中定义的关系类型 ID (如 `marriage`, `brother`, `best_friend` 等)。*

## 📖 指令列表

### 玩家指令
*   `/rel gui` - 打开关系菜单
*   `/rel list` - 查看自己的所有关系列表
*   `/rel top <类型>` - 查看指定关系的亲密度排行榜
*   `/rel request <玩家> <类型>` - 发送关系请求
*   `/rel accept <玩家>` - 接受请求
*   `/rel deny <玩家>` - 拒绝请求
*   `/rel remove <玩家>` - 解除关系
*   `/rel gender <male|female|other>` - 设置性别
*   `/rel marry <sethome|home|gift|tp|list>` - 婚姻专属指令

### 管理员指令 (支持控制台)
*   `/rel admin reload` - 重载配置文件 (会自动关闭所有玩家打开的 GUI)
*   `/rel admin affinity <set/add/remove> <玩家1> [玩家2] <类型> <数值>` - 管理亲密度
    *   **注意**: 管理员指令修改亲密度**不受每日上限限制**。
*   `/rel save <类型> [数值]` - 将手中物品保存为亲密度道具 (需 `relations.admin.save` 权限)
*   `/rel debug addfake <名字> <类型> [亲密度]` - 添加虚假关系用于测试 GUI 显示 (仅内存临时生效名字，数据库写入关系)

## ⚙️ 权限节点

### 指令权限
*   `relations.command.use` - 基础使用权限 (默认拥有)
*   `relations.command.list` - 查看关系列表 (默认拥有)
*   `relations.command.top` - 查看排行榜 (默认拥有)
*   `relations.command.request` - 发送请求 (默认拥有)
*   `relations.command.accept` - 接受请求 (默认拥有)
*   `relations.command.deny` - 拒绝请求 (默认拥有)
*   `relations.command.remove` - 解除关系 (默认拥有)
*   `relations.command.gender` - 设置性别 (默认拥有)
*   `relations.command.marry` - 婚姻指令 (默认拥有)
*   `relations.admin` - 管理员权限 (重载、管理亲密度) (默认 OP)
*   `relations.admin.save` - 保存亲密度物品 (默认 OP)

### 限制权限 (VIP功能)
*   `relations.limit.<type>.<number>` - 设置玩家拥有某种关系的最大数量
    *   示例: `relations.limit.brother.5` (允许拥有 5 个兄弟)
*   `relations.affinity_limit.<type>.<number>` - 设置玩家某种关系的每日亲密度获取上限
    *   示例: `relations.affinity_limit.marriage.1000` (每天最多增加 1000 点亲密度)

## 📥 安装

1. 将插件 jar 文件放入 `plugins` 文件夹。
2. 重启服务器。
3. 修改 `config.yml` 配置数据库和消息（可选）。
4. 重载插件或重启服务器生效。
