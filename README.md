# CameraTrack

**为 Minecraft Java 26.3 / Paper 26.3 设计的自定义摄像轨道插件。**
用指令、原生菜单或手持工具摆放镜头节点，自动连成平滑轨道；播放时以旁观者实体运镜，用来做开场动画、过场和宣传片镜头。

---

## 特性

- **三种编辑方式**：`/camera` 指令、Dialog 可视化菜单、手持摄像工具。
- **自动成轨**：节点按顺序自动连接；同平面段（任一轴坐标相同）走直线，其余默认沿视线方向弯出弧线，也支持强制样条或直线。
- **平滑巡航**：位置与朝向按时间做连续样条，节点只是通过点，不会“到站停车”；起终点和停留处自然加减速。
- **逐段控制**：每个节点可单独设置停留时间、飞往下一节点的时间、缓动曲线、路径模式和到达时执行的命令。
- **节点标题**：每个节点可在镜头到达时显示屏幕标题与副标题，文本支持 MiniMessage 或原版 JSON 文本组件（颜色、翻译键等），淡入 / 停留 / 淡出时间可分别设置。
- **节点类型**：默认（正常经过）、切换（停留后硬切到下一节点，类似切镜头）、贝塞尔（不参与播放，只作为前后默认节点的曲线控制点）。
- **实体运镜**：播放时生成不可见的标记盔甲架，玩家旁观附身，客户端对实体位移/旋转做插值，画面接近原生运镜。
- **可配置收尾**：播放结束可回到起点或留在终点（每条轨道单独设置），并恢复游戏模式、飞行、速度与位置。
- **异常退出恢复**：播放途中退出或服务器崩溃时，重进服务器会自动回到播放起点并还原游戏模式与速度，不会卡在旁观者状态。
- **循环 / 倍速 / 可配置的轨迹粒子预览（亮绿色轨迹线 + 红色节点标记） / YAML 持久化**。

## 环境要求

| 项目 | 版本 |
| --- | --- |
| 服务端 | Paper **26.3**（API 26.3） |
| Java | **25** |
| 客户端 | 1.21.6+（Dialog 界面需要；其余功能无限制） |

编译目标：`io.papermc.paper:paper-api:26.3.build.237-stable`。

## 安装

1. 从 [Releases](../../releases) 下载 （或自行构建）。
2. 放入服务端 `plugins/` 目录。
3. 重启服务端。
4. 数据文件：`plugins/CameraTrack/tracks.yml`、`pending-returns.yml`（异常退出恢复快照）；配置：`plugins/CameraTrack/config.yml`。

## 快速开始

```text
/camera create intro          # 新建轨道
# 站到第一个机位（建议旁观/创造飞行），看向拍摄方向
/camera add intro 4           # 记录节点，4 秒后飞往下一节点
# 飞到下一个机位，调整视角，再次 add
/camera add intro 4
/camera nodes intro           # 检查节点
/camera preview intro         # 粒子预览轨迹
/camera play intro            # 播放
/camera stop                  # 随时停止并恢复状态
```

也可以直接输入 `/camera ui` 用菜单编辑，或 `/camera tool` 拿工具在游戏里铺轨。

## 指令

| 指令 | 说明 |
| --- | --- |
| `/camera create <名称>` | 新建轨道 |
| `/camera add <名称> [秒]` | 在眼睛位置追加节点，`秒` 为飞往下一节点的时间 |
| `/camera insert <名称> <位置> [秒]` | 在指定位置插入节点 |
| `/camera remove <名称> <序号>` | 删除节点 |
| `/camera nodes <名称>` | 列出节点 |
| `/camera set <名称> <序号> <字段> <值>` | 修改节点：`duration` `hold` `ease` `yaw` `pitch` `path` `type` `command` `title` `subtitle` `title-fade-in` `title-stay` `title-fade-out` |
| `/camera goto <名称> <序号>` | 传送到节点 |
| `/camera play <名称> [速度] [--loop] [--stay] [--return] [--tp] [--segmented]` | 播放；单次覆盖循环/收尾/播放模式 |
| `/camera stop [玩家]` | 停止并恢复状态 |
| `/camera loop <名称> <on\|off>` | 默认循环 |
| `/camera flow <名称> <on\|off>` | 平滑巡航（默认开） |
| `/camera return <名称> <on\|off>` | 播放结束后是否回到起点 |
| `/camera preview <名称>` | 粒子预览轨迹 |
| `/camera ui [名称]` | 打开 Dialog 编辑界面 |
| `/camera tool` | 获得手持摄像工具 |
| `/camera list` / `info` / `rename` / `delete` / `save` / `reload` | 轨道管理 |

别名：`/camtrack`、`/ctrack`。

## 手持工具

`/camera tool` 获得的物品是通过 `item_model` 显示成望远镜的烈焰棒，主手持有生效：

| 操作 | 效果 |
| --- | --- |
| 右键 | 添加节点 |
| Shift + 右键 | 打开当前轨道的 GUI |
| 左键 | 显示轨道信息 + 粒子预览整条轨迹 |
| Shift + 左键 | 切换轨道 |

## Dialog 界面

`/camera ui` 打开原生菜单（1.21.6+）：

- **轨道列表**：选择轨道、新建轨道；
- **轨道详情**：节点按钮、添加节点、播放、循环、平滑巡航、结束后返回起点、删除轨道；
- **节点详情**：输入框改停留/飞行秒数、标题/副标题与三段标题时间，单选改缓动/路径/节点类型，文本框改到达命令，另有传送、更新为当前视角、上移/下移、删除。

## 节点标题（title）

每个节点可以在镜头到达时显示一次屏幕标题（主标题 + 可选副标题），只对正在运镜的玩家显示。淡入、停留、淡出三个时间分别设置，默认 0.5 / 3.5 / 1.0 秒（与原版 `/title times` 一致）。

标题文本支持两种写法，按首字符自动识别：

- **MiniMessage**（推荐，颜色、渐变等最方便）：

  ```text
  /camera set intro 1 title "<gold><bold>第一章 · 黎明</bold></gold>"
  /camera set intro 1 subtitle "<gray>the beginning</gray>"
  /camera set intro 2 title "<lang:multiplayer.player.joined:'<player>'>"
  ```

- **原版 JSON 文本组件**（以 `{` 或 `[` 开头，写法和 `/title`、`/tellraw` 的 JSON 参数一致，支持 `text` / `translate` / `with` / `extra` / 颜色 / 各装饰）：

  ```text
  /camera set intro 3 title '{"translate":"multiplayer.player.joined","with":[{"text":"Steve"}],"color":"gold"}'
  ```

两种写法都支持占位符 `<player>`，会替换为正在观看的玩家名。时间设置：

```text
/camera set intro 1 title-fade-in 1.0   # 淡入 1 秒
/camera set intro 1 title-stay 3.0      # 停留 3 秒
/camera set intro 1 title-fade-out 0.5  # 淡出 0.5 秒
/camera set intro 1 title clear         # 清空标题（subtitle clear 同理）
```

GUI 的节点页有“标题 / 副标题 / 淡入 / 停留 / 淡出”五个输入框，留空即清空该行；文本无法解析时保存会被拒绝并提示。

## 轨迹预览

`/camera preview <名称>`（或手持摄像工具左键）会沿整条轨道画出粒子：

- **轨迹线**：默认段亮绿色带颜色粒子（DUST），按固定**距离**采样（默认每 0.15 格一个，时间步长按最快段自适应）；**切换段是灰色直线**（表示硬切、不飞行），贝塞尔控制点不画线，但前后默认节点之间的曲线仍为绿色；
- **节点标记**：默认红色、切换黄色、贝塞尔蓝色；
- 预览默认持续 **10 秒**，期间每 0.5 秒重新发射一轮（粒子在客户端只能存活约 1 秒）；同一玩家再次预览会先取消上一次。

`config.yml` 的 `preview` 段可调：

```yaml
preview:
  trail:
    color: "#00FF00"   # 普通飞行段：亮绿色
    cut-color: "#808080" # 切换段：灰色直线
    size: 1.0          # 粒子大小
    spacing: 0.15      # 相邻粒子间距（格），越小越密
    max-points: 1200   # 单条轨迹最多画多少个点
  node:
    color: "#FF0000"   # 默认节点：红色
    cut-color: "#FFFF00"    # 切换节点：黄色
    bezier-color: "#0000FF" # 贝塞尔节点：蓝色
    size: 2.0          # 比轨迹粒子更大
    count: 12          # 每个节点发射的粒子数
    spread: 0.06       # 节点粒子散布半径（格）
  duration-ticks: 200  # 预览持续（tick，20 tick = 1 秒；0 = 只发射一次）
  refresh-ticks: 10    # 重新发射间隔（tick）
```

## 路径模式（path）

每一段的路径形状挂在“起点节点”上：

| 值 | 行为 |
| --- | --- |
| `auto`（默认） | 两节点在任一轴坐标相同（同平面）时走直线；不同平面时多点由邻居切线样条连成一条完整曲线，两点轨道自动补一段单侧弧线（`path.auto-arc: false` 可关闭） |
| `line` | 强制直线，只有朝向变化 |
| `curve` | 强制 Catmull-Rom 样条，适合等高环绕、绕建筑飞行 |
| `arc` | **单侧弧线**：在连线中点朝“视线反方向”鼓出，形成完整的一段弧（不是 S 形）；适合两点轨道或斜向运镜 |

只有两个节点的轨道，普通样条的切线与速度都是 0，会退化成一条直线斜切；`auto` 会为它自动补弧线，也可以用 `path arc` 手动强制。`path.arc-strength` 控制弧顶偏移（占段长的比例，0 = 不弯，默认 0.15）。带 `hold` 的节点会保持直线并平滑停住，不会插弧线。

## 节点类型（type）

| 类型 | 行为 | 预览 |
| --- | --- | --- |
| `default`（默认） | 镜头正常经过，按 `path` 连接下一节点 | 红色节点、绿色轨迹 |
| `cut`（切换） | 在本节点停留结束后**直接硬切**到下一节点并继续播放，不沿轨道飞行；`duration` 不参与，`hold` 是这个镜头的停留时间 | 黄色节点、灰色直线 |
| `bezier`（贝塞尔） | **不参与播放**，只作为前后两个默认节点之间的二次贝塞尔控制点 | 蓝色节点，曲线仍为绿色 |

```text
/camera set intro 2 type cut
/camera set intro 3 type bezier
```

GUI 节点页也有“节点类型”单选项。贝塞尔节点的结构限制（播放前校验，不满足会拒绝播放并提示）：

- 前一个节点必须是 `default`；后一个节点可以是 `default` 或 `cut`（曲线照常算到切换节点，再由它硬切）；
- 不能位于轨道开头，不能与前一个 `cut` 相邻，也不能两个贝塞尔节点连续出现；
- 整条轨道只有贝塞尔节点时无法播放。

`cut` 节点落到的下一个节点会继续按正常路径逻辑连接再下一个节点（不会因为前面是切换就强制成直线）。

贝塞尔节点自己的 `duration`、`hold`、朝向、标题与命令都不参与播放；它前面默认节点的 `duration` 作为到后面默认节点的飞行时间。`cut` 节点位于轨道末尾时没有下一节点可切，等价于普通收尾。

## 配置

`config.yml`：

```yaml
# 语言: zh_cn / en_us / auto（跟随客户端语言）
language: zh_cn

# entity（默认）或 teleport（旧模式/兜底）
playback-mode: entity

path:
  # 同平面判定容差（格）；0 表示只接受坐标完全相同
  axis-tolerance: 0.05
  # 两点轨道（非共面）是否自动补一段单侧弧线
  auto-arc: true
  # 弧顶偏移 = 段长 × 该值（0 = 不弯，建议 0.1 - 0.3，最大 0.5）
  arc-strength: 0.15

# 轨迹预览的粒子颜色 / 大小 / 间距 / 持续时间，完整字段见「轨迹预览」
preview:
  trail:
    color: "#00FF00"
    spacing: 0.15
  node:
    color: "#FF0000"
    size: 2.0
  duration-ticks: 200
```

轨道级设置保存在 `tracks.yml`：`loop`、`flow`（平滑巡航）、`return-to-start`（结束后回起点）。

## 语言 / Lang

聊天提示和 Dialog 界面的全部文案（页面标题、按钮、说明、输入标签）都由翻译键控制，文件位于：

```text
plugins/CameraTrack/lang/
├── zh_cn.json   # 简体中文（默认）
└── en_us.json   # English
```

- 值使用 [MiniMessage](https://docs.advntr.dev/minimessage/format.html) 语法，`%name%`、`%count%` 等是占位符；
- `config.yml` 的 `language` 可选 `zh_cn`、`en_us` 或 `auto`；
- 新增语言：复制任一文件改名为 `<locale>.json`（如 `ja_jp.json`），覆盖需要翻译的键即可，缺失的键会回退到 `zh_cn`；
- 升级插件时，磁盘文件里没有的新键会自动从 jar 内置文件合并，不必手动补全。

## 数据格式

```yaml
tracks:
  intro:
    loop: false
    flow: true
    return-to-start: true
    nodes:
    - world: world
      x: 0.0
      y: 64.0
      z: 0.0
      yaw: 0.0
      pitch: 0.0
      hold: 0.0         # 到达后停留秒数（默认 0 = 连续巡航；需要停顿再设）
      duration: 3.0
      ease: smooth      # linear / smooth / in / out / inout
      path: auto        # auto / line / curve / arc
      type: default     # default / cut / bezier（默认可省略）
      command: ""       # 到达时以控制台执行，可留空
      title:            # 到达时显示的标题，可整段省略
        text: "<gold>第一章 · 黎明</gold>"
        subtitle: "<gray>the beginning</gray>"
        fade-in: 0.5
        stay: 3.5
        fade-out: 1.0
```

`title` 也可以简写成一行文本（`title: "<gold>Hello</gold>"`），等价于只设置 `text`。

## 权限

| 权限 | 默认 | 说明 |
| --- | --- | --- |
| `cameratrack.admin` | op | 创建、编辑、预览、保存轨道 |
| `cameratrack.play` | op | 播放、停止轨道（默认仅 OP；其他插件可单独授予该节点） |

所有 `/camera` 子命令默认仅 OP 可用；需要让非 OP 使用时，用权限插件授予 `cameratrack.play`（仅播放/停止）或 `cameratrack.admin`（全部功能）。

## 构建

需要 JDK 25 与 Maven 3.9+：

```bash
mvn -DskipTests package
```

Windows 也可以使用：

```powershell
./build.ps1
```

产物在 `target/CameraTrack-1.12.1.jar`。

## 已知限制

- 相机更新受服务端 tick 驱动（20 次/秒），客户端插值会补到渲染帧率；极高速贴地镜头建议增加节点。
- 平滑巡航模式下 `ease` 不生效；需要逐段缓动时关闭该轨道的 `flow` 或使用 `--segmented`。
- 播放依赖 `Player#teleport`/实体附身，监听传送事件并取消的插件可能干扰运镜。
- 一条轨道应只在一个世界内使用。
- 服务端连续移动的实体每 400 tick 会强制一次绝对位置同步，长镜头可拆段。



