# CameraTrack

**为 Minecraft Java 26.3 / Paper 26.3 设计的自定义摄像轨道插件。**
用指令、原生菜单或手持工具摆放镜头节点，自动连成平滑轨道；播放时以旁观者实体运镜，用来做开场动画、过场和宣传片镜头。

---

## 特性

- **三种编辑方式**：`/camera` 指令、Dialog 可视化菜单、手持摄像工具。
- **自动成轨**：节点按顺序自动连接；同平面段（任一轴坐标相同）走直线，其余用样条平滑过渡。
- **平滑巡航**：位置与朝向按时间做连续样条，节点只是通过点，不会“到站停车”；起终点和停留处自然加减速。
- **逐段控制**：每个节点可单独设置停留时间、飞往下一节点的时间、缓动曲线、路径模式和到达时执行的命令。
- **实体运镜**：播放时生成不可见的标记盔甲架，玩家旁观附身，客户端对实体位移/旋转做插值，画面接近原生运镜。
- **可配置收尾**：播放结束可回到起点或留在终点（每条轨道单独设置），并恢复游戏模式、飞行、速度与位置。
- **异常退出恢复**：播放途中退出或服务器崩溃时，重进服务器会自动回到播放起点并还原游戏模式与速度，不会卡在旁观者状态。
- **循环 / 倍速 / 轨迹粒子预览 / YAML 持久化**。

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
| `/camera set <名称> <序号> <字段> <值>` | 修改节点：`duration` `hold` `ease` `yaw` `pitch` `path` `command` |
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
- **节点详情**：输入框改停留/飞行秒数，单选改缓动/路径，文本框改到达命令，另有传送、更新为当前视角、上移/下移、删除。

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
```

轨道级设置保存在 `tracks.yml`：`loop`、`flow`（平滑巡航）、`return-to-start`（结束后回起点）。

## 语言 / Lang

聊天提示和 GUI 操作反馈都由翻译键控制，文件位于：

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
      hold: 0.5
      duration: 3.0
      ease: smooth      # linear / smooth / in / out / inout
      path: auto        # auto / line / curve
      command: ""       # 到达时以控制台执行，可留空
```

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

产物在 `target/CameraTrack-1.9.0.jar`。

## 已知限制

- 相机更新受服务端 tick 驱动（20 次/秒），客户端插值会补到渲染帧率；极高速贴地镜头建议增加节点。
- 平滑巡航模式下 `ease` 不生效；需要逐段缓动时关闭该轨道的 `flow` 或使用 `--segmented`。
- 播放依赖 `Player#teleport`/实体附身，监听传送事件并取消的插件可能干扰运镜。
- 一条轨道应只在一个世界内使用。
- 服务端连续移动的实体每 400 tick 会强制一次绝对位置同步，长镜头可拆段。



