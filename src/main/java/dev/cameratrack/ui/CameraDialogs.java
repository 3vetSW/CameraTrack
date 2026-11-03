package dev.cameratrack.ui;

import dev.cameratrack.CameraTrackPlugin;
import dev.cameratrack.lang.Lang;
import dev.cameratrack.playback.PlaybackManager;
import dev.cameratrack.track.CameraNode;
import dev.cameratrack.track.CameraTrack;
import dev.cameratrack.track.Ease;
import dev.cameratrack.track.PathMode;
import dev.cameratrack.track.TrackStore;
import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.dialog.DialogResponseView;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.action.DialogAction;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import io.papermc.paper.registry.data.dialog.input.DialogInput;
import io.papermc.paper.registry.data.dialog.input.SingleOptionDialogInput;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickCallback;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

/**
 * 基于 Minecraft 1.21.6+ / Paper Dialog API 的可视化编辑界面。
 *
 * <p>dialog 本身不能就地刷新，所以这里用“点按钮 → 回调里重新打开一个新 dialog”的方式做导航：
 * 轨道列表 → 轨道详情（节点按钮）→ 节点详情（输入框 + 保存/传送/更新视角/删除）。</p>
 */
public final class CameraDialogs {

    private static final int MAX_NODE_BUTTONS = 45;
    private static final float MAX_SECONDS = 600.0F;

    private final CameraTrackPlugin plugin;
    private final TrackStore store;
    private final PlaybackManager playback;
    private final Lang lang;

    public CameraDialogs(CameraTrackPlugin plugin, TrackStore store, PlaybackManager playback, Lang lang) {
        this.plugin = plugin;
        this.store = store;
        this.playback = playback;
        this.lang = lang;
    }

    // ------------------------------------------------------------------ 主界面

    public void openMain(Player player) {
        List<ActionButton> buttons = new ArrayList<>();
        for (String name : this.store.names()) {
            CameraTrack track = this.store.get(name);
            if (track == null) {
                continue;
            }
            buttons.add(button(
                    Component.text(name + "  (" + track.size() + " 节点)", NamedTextColor.YELLOW),
                    Component.text("编辑轨道 " + name),
                    view -> later(player, () -> openTrack(player, name))));
        }
        if (buttons.isEmpty()) {
            buttons.add(closeButton());
        }
        buttons.add(button(Component.text("+ 新建轨道", NamedTextColor.GREEN),
                Component.text("输入名称创建一条新轨道"),
                view -> later(player, () -> openCreate(player))));
        if (buttons.size() > 1) {
            buttons.add(closeButton());
        }

        List<DialogBody> body = List.of(
                body("共 " + this.store.tracks().size() + " 条轨道。选择一条轨道编辑，或新建一条。"),
                body("提示：轨道界面里可以直接添加节点、播放、切换循环；节点界面可以改停留/飞行/缓动/路径/命令。"));
        show(player, dialog(Component.text("CameraTrack 轨道管理", NamedTextColor.GOLD), body, List.of(), buttons, 2));
    }

    // ------------------------------------------------------------------ 轨道详情

    public void openTrack(Player player, String trackName) {
        CameraTrack track = this.store.get(trackName);
        if (track == null) {
            this.lang.send(player, "track.not-found", "name", trackName);
            openMain(player);
            return;
        }
        String name = track.name();
        List<ActionButton> buttons = new ArrayList<>();
        int shown = Math.min(track.size(), MAX_NODE_BUTTONS);
        for (int i = 0; i < shown; i++) {
            CameraNode node = track.get(i);
            int index = i;
            buttons.add(button(
                    Component.text("#" + (i + 1) + "  " + format(node.x()) + ", " + format(node.y()) + ", " + format(node.z()),
                            NamedTextColor.AQUA),
                    Component.text("点击编辑节点 #" + (i + 1)),
                    view -> later(player, () -> openNode(player, name, index))));
        }
        buttons.add(button(Component.text("+ 添加节点（当前位置）", NamedTextColor.GREEN),
                Component.text("把你当前眼睛所在的位置和朝向记录为新节点"),
                view -> {
                    addNodeAtPlayer(player, name);
                    later(player, () -> openTrack(player, name));
                }));
        buttons.add(button(Component.text("播放轨道", NamedTextColor.GREEN),
                Component.text("立即开始播放这条轨道"),
                view -> later(player, () -> play(player, name))));
        buttons.add(button(
                Component.text("循环: " + (track.loop() ? "开" : "关"), NamedTextColor.YELLOW),
                Component.text("切换这条轨道的默认循环状态"),
                view -> {
                    CameraTrack current = this.store.get(name);
                    if (current != null) {
                        current.setLoop(!current.loop());
                        this.store.save();
                    }
                    later(player, () -> openTrack(player, name));
                }));
        buttons.add(button(
                Component.text("平滑巡航: " + (track.flow() ? "开" : "关"), NamedTextColor.YELLOW),
                Component.text("开：节点之间连续通过，转动更顺；关：逐段缓动"),
                view -> {
                    CameraTrack current = this.store.get(name);
                    if (current != null) {
                        current.setFlow(!current.flow());
                        this.store.save();
                    }
                    later(player, () -> openTrack(player, name));
                }));
        buttons.add(button(
                Component.text("结束后返回起点: " + (track.returnToStart() ? "开" : "关"), NamedTextColor.YELLOW),
                Component.text("开：播完回到原位置；关：播完停在终点"),
                view -> {
                    CameraTrack current = this.store.get(name);
                    if (current != null) {
                        current.setReturnToStart(!current.returnToStart());
                        this.store.save();
                    }
                    later(player, () -> openTrack(player, name));
                }));
        buttons.add(button(Component.text("删除轨道", NamedTextColor.RED),
                Component.text("删除整条轨道（需确认）"),
                view -> later(player, () -> confirmDeleteTrack(player, name))));
        buttons.add(button(Component.text("← 返回", NamedTextColor.GRAY),
                Component.text("回到轨道列表"),
                view -> later(player, () -> openMain(player))));

        List<DialogBody> body = new ArrayList<>();
        body.add(body("节点数 " + track.size() + " · 总时长 " + format(track.totalDuration()) + "s · 默认循环 "
                + (track.loop() ? "开" : "关") + " · 平滑巡航 " + (track.flow() ? "开" : "关")
                + " · 结束后返回 " + (track.returnToStart() ? "开" : "关")));
        if (track.size() == 0) {
            body.add(body("还没有节点：点下面的“添加节点”把当前位置记成第一个镜头。"));
        } else if (track.size() > shown) {
            body.add(body("节点过多，仅显示前 " + shown + " 个；其余请用指令编辑。"));
        }
        body.add(body("同平面（任一轴坐标相同）的相邻节点会自动走直线，其余走样条。"));

        show(player, dialog(Component.text("轨道: " + name, NamedTextColor.GOLD), body, List.of(), buttons, 1));
    }

    // ------------------------------------------------------------------ 节点详情

    public void openNode(Player player, String trackName, int index) {
        CameraTrack track = this.store.get(trackName);
        if (track == null || index < 0 || index >= track.size()) {
            openTrack(player, trackName);
            return;
        }
        CameraNode node = track.get(index);
        String name = track.name();

        List<DialogInput> inputs = List.of(
                DialogInput.text("duration", Component.text("飞往下一节点（秒，可填小数）", NamedTextColor.GRAY))
                        .initial(String.valueOf(node.duration()))
                        .maxLength(12)
                        .width(320)
                        .build(),
                DialogInput.text("hold", Component.text("到达后停留（秒，可填小数）", NamedTextColor.GRAY))
                        .initial(String.valueOf(node.hold()))
                        .maxLength(12)
                        .width(320)
                        .build(),
                DialogInput.singleOption("ease", Component.text("缓动曲线", NamedTextColor.GRAY), easeOptions(node.ease()))
                        .width(320)
                        .build(),
                DialogInput.singleOption("path", Component.text("路径模式", NamedTextColor.GRAY), pathOptions(node.path()))
                        .width(320)
                        .build(),
                DialogInput.text("command", Component.text("到达时执行的命令（可留空）", NamedTextColor.GRAY))
                        .initial(node.command().length() > 256 ? node.command().substring(0, 256) : node.command())
                        .maxLength(256)
                        .width(320)
                        .build()
        );

        List<ActionButton> buttons = new ArrayList<>();
        buttons.add(button(Component.text("保存设置", NamedTextColor.GREEN),
                Component.text("应用上面的输入"),
                view -> {
                    applyNode(player, name, index, view);
                    later(player, () -> openNode(player, name, index));
                }));
        buttons.add(button(Component.text("传送到该节点", NamedTextColor.AQUA),
                Component.text("把玩家传送到这个镜头的位置"),
                view -> later(player, () -> teleportToNode(player, name, index))));
        buttons.add(button(Component.text("更新为当前视角", NamedTextColor.YELLOW),
                Component.text("把这个节点的位置和朝向改成你现在的视角"),
                view -> {
                    updateNodePose(player, name, index);
                    later(player, () -> openNode(player, name, index));
                }));
        buttons.add(button(Component.text("上移", NamedTextColor.YELLOW),
                Component.text("与上一个节点交换顺序"),
                view -> {
                    swap(player, name, index, index - 1);
                    later(player, () -> openNode(player, name, Math.max(0, index - 1)));
                }));
        buttons.add(button(Component.text("下移", NamedTextColor.YELLOW),
                Component.text("与下一个节点交换顺序"),
                view -> {
                    swap(player, name, index, index + 1);
                    later(player, () -> openNode(player, name, Math.min(track.size() - 1, index + 1)));
                }));
        buttons.add(button(Component.text("删除节点", NamedTextColor.RED),
                Component.text("删除这个节点（需确认）"),
                view -> later(player, () -> confirmDeleteNode(player, name, index))));
        buttons.add(button(Component.text("← 返回轨道", NamedTextColor.GRAY),
                Component.text("回到轨道详情"),
                view -> later(player, () -> openTrack(player, name))));

        List<DialogBody> body = new ArrayList<>();
        body.add(body("坐标 " + format(node.x()) + ", " + format(node.y()) + ", " + format(node.z())
                + " · 朝向 yaw " + format(node.yaw()) + " / pitch " + format(node.pitch())));
        body.add(body("当前：停留 " + format(node.hold()) + "s · 飞行 " + format(node.duration())
                + "s · 缓动 " + node.ease().id() + " · 路径 " + node.path().id()));
        body.add(body("修改上面的输入后点“保存设置”。路径 auto 表示同平面自动走直线。"));

        show(player, dialog(Component.text("节点 #" + (index + 1) + " · " + name, NamedTextColor.GOLD),
                body, inputs, buttons, 1));
    }

    // ------------------------------------------------------------------ 新建 / 删除

    private void openCreate(Player player) {
        List<DialogInput> inputs = List.of(
                DialogInput.text("name", Component.text("轨道名称（字母/数字/下划线/短横线）", NamedTextColor.GRAY))
                        .maxLength(32)
                        .width(320)
                        .build());
        List<ActionButton> buttons = new ArrayList<>();
        buttons.add(button(Component.text("创建", NamedTextColor.GREEN),
                Component.text("创建并打开这条轨道"),
                view -> {
                    String raw = view.getText("name");
                    if (raw == null || raw.isBlank()) {
                        return;
                    }
                    String name = raw.trim().toLowerCase(java.util.Locale.ROOT);
                    if (!TrackStore.isValidName(name) || this.store.get(name) != null) {
                        this.lang.send(player, "gui.error-name", "value", raw);
                        return;
                    }
                    this.store.create(name);
                    this.store.save();
                    later(player, () -> openTrack(player, name));
                }));
        buttons.add(button(Component.text("← 返回", NamedTextColor.GRAY),
                Component.text("回到轨道列表"),
                view -> later(player, () -> openMain(player))));

        show(player, dialog(Component.text("新建轨道", NamedTextColor.GOLD),
                List.of(body("给新轨道起一个名字，创建后再往里面添加镜头节点。")),
                inputs, buttons, 1));
    }

    private void confirmDeleteTrack(Player player, String trackName) {
        ActionButton yes = ActionButton.builder(Component.text("删除", NamedTextColor.RED))
                .tooltip(Component.text("永久删除这条轨道"))
                .action(customClick((view, audience) -> {
                    this.store.remove(trackName);
                    this.store.save();
                    later(player, () -> openMain(player));
                }))
                .build();
        show(player, Dialog.create(builder -> builder.empty()
                .base(DialogBase.builder(Component.text("删除轨道: " + trackName, NamedTextColor.RED))
                        .body(List.of(body("轨道及其全部节点会被删除，且无法撤销。")))
                        .build())
                .type(DialogType.confirmation(yes, cancelButton(player, () -> openTrack(player, trackName))))));
    }

    private void confirmDeleteNode(Player player, String trackName, int index) {
        ActionButton yes = ActionButton.builder(Component.text("删除", NamedTextColor.RED))
                .tooltip(Component.text("删除这个节点"))
                .action(customClick((view, audience) -> {
                    CameraTrack track = this.store.get(trackName);
                    if (track != null && index >= 0 && index < track.size()) {
                        track.remove(index);
                        this.store.save();
                    }
                    later(player, () -> openTrack(player, trackName));
                }))
                .build();
        show(player, Dialog.create(builder -> builder.empty()
                .base(DialogBase.builder(Component.text("删除节点 #" + (index + 1), NamedTextColor.RED))
                        .body(List.of(body("这个镜头节点会被移除，相邻节点会自动重新连接。")))
                        .build())
                .type(DialogType.confirmation(yes, cancelButton(player, () -> openNode(player, trackName, index))))));
    }

    // ------------------------------------------------------------------ 动作

    private void addNodeAtPlayer(Player player, String trackName) {
        CameraTrack track = this.store.get(trackName);
        Location eye = player.getEyeLocation();
        if (track == null || eye.getWorld() == null) {
            return;
        }
        track.add(CameraNode.at(eye, CameraNode.DEFAULT_HOLD, CameraNode.DEFAULT_DURATION, Ease.SMOOTH, ""));
        this.store.save();
        this.lang.send(player, "gui.added", "index", track.size());
    }

    private void play(Player player, String trackName) {
        CameraTrack track = this.store.get(trackName);
        if (track == null) {
            return;
        }
        if (track.size() < 2) {
            this.lang.send(player, "gui.error-min-nodes");
            return;
        }
        this.playback.start(player, track, 1.0D, track.loop(), !track.returnToStart(),
                this.plugin.defaultPlaybackMode(), track.flow());
        this.lang.send(player, "gui.playing", "name", track.name());
    }

    private void teleportToNode(Player player, String trackName, int index) {
        CameraTrack track = this.store.get(trackName);
        if (track == null || index < 0 || index >= track.size()) {
            return;
        }
        CameraNode node = track.get(index);
        World world = Bukkit.getWorld(node.world());
        if (world == null) {
            this.lang.send(player, "gui.error-world", "world", node.world());
            return;
        }
        Location target = node.toLocation(world);
        target.setY(target.getY() - player.getEyeHeight());
        player.teleport(target);
    }

    private void updateNodePose(Player player, String trackName, int index) {
        CameraTrack track = this.store.get(trackName);
        Location eye = player.getEyeLocation();
        if (track == null || index < 0 || index >= track.size() || eye.getWorld() == null) {
            return;
        }
        track.set(index, track.get(index).withPose(eye));
        this.store.save();
    }

    private void swap(Player player, String trackName, int a, int b) {
        CameraTrack track = this.store.get(trackName);
        if (track == null || a < 0 || b < 0 || a >= track.size() || b >= track.size() || a == b) {
            return;
        }
        CameraNode first = track.get(a);
        CameraNode second = track.get(b);
        track.set(a, second);
        track.set(b, first);
        this.store.save();
    }

    private void applyNode(Player player, String trackName, int index, DialogResponseView view) {
        CameraTrack track = this.store.get(trackName);
        if (track == null || index < 0 || index >= track.size()) {
            return;
        }
        CameraNode node = track.get(index);

        String durationText = view.getText("duration");
        String holdText = view.getText("hold");
        String easeId = view.getText("ease");
        String pathId = view.getText("path");
        String command = view.getText("command");

        Double duration = parseSeconds(durationText);
        if (durationText != null && !durationText.isBlank() && duration == null) {
            this.lang.send(player, "number.invalid", "value", durationText);
            return;
        }
        Double hold = parseSeconds(holdText);
        if (holdText != null && !holdText.isBlank() && hold == null) {
            this.lang.send(player, "number.invalid", "value", holdText);
            return;
        }

        CameraNode updated = node;
        if (duration != null) {
            updated = updated.withDuration(clamp(duration));
        }
        if (hold != null) {
            updated = updated.withHold(clamp(hold));
        }
        if (easeId != null) {
            Ease ease = Ease.byId(easeId, null);
            if (ease != null) {
                updated = updated.withEase(ease);
            }
        }
        if (pathId != null) {
            PathMode mode = PathMode.byId(pathId, null);
            if (mode != null) {
                updated = updated.withPath(mode);
            }
        }
        if (command != null) {
            updated = updated.withCommand(command);
        }
        track.set(index, updated);
        this.store.save();
        this.lang.send(player, "gui.saved", "index", index + 1);
    }

    private static Double parseSeconds(String raw) {
        if (raw == null) {
            return null;
        }
        try {
            double value = Double.parseDouble(raw.trim());
            if (!Double.isFinite(value)) {
                return null;
            }
            return value;
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private static double clamp(double value) {
        return Math.max(0.0D, Math.min(MAX_SECONDS, value));
    }

    // ------------------------------------------------------------------ 构建工具

    private Dialog dialog(Component title, List<DialogBody> body, List<DialogInput> inputs,
                          List<ActionButton> buttons, int columns) {
        return Dialog.create(builder -> builder.empty()
                .base(DialogBase.builder(title)
                        .body(body)
                        .inputs(inputs)
                        .canCloseWithEscape(true)
                        .afterAction(DialogBase.DialogAfterAction.CLOSE)
                        .build())
                .type(DialogType.multiAction(buttons).columns(columns).build()));
    }

    private ActionButton button(Component label, Component tooltip, java.util.function.Consumer<DialogResponseView> action) {
        return ActionButton.builder(label)
                .tooltip(tooltip)
                .action(customClick((view, audience) -> action.accept(view)))
                .build();
    }

    private static DialogAction customClick(io.papermc.paper.registry.data.dialog.action.DialogActionCallback callback) {
        return DialogAction.customClick(callback, ClickCallback.Options.builder().uses(1).build());
    }

    private static ActionButton closeButton() {
        return ActionButton.builder(Component.text("关闭", NamedTextColor.GRAY))
                .tooltip(Component.text("关闭界面"))
                .build();
    }

    private ActionButton cancelButton(Player player, Runnable reopen) {
        return ActionButton.builder(Component.text("取消", NamedTextColor.GRAY))
                .tooltip(Component.text("返回"))
                .action(customClick((view, audience) -> later(player, reopen)))
                .build();
    }

    private static DialogBody body(String text) {
        return DialogBody.plainMessage(Component.text(text, NamedTextColor.GRAY));
    }

    private static List<SingleOptionDialogInput.OptionEntry> easeOptions(Ease current) {
        List<SingleOptionDialogInput.OptionEntry> options = new ArrayList<>();
        for (Ease ease : Ease.values()) {
            options.add(SingleOptionDialogInput.OptionEntry.create(
                    ease.id(), Component.text(ease.id()), ease == current));
        }
        return options;
    }

    private static List<SingleOptionDialogInput.OptionEntry> pathOptions(PathMode current) {
        List<SingleOptionDialogInput.OptionEntry> options = new ArrayList<>();
        for (PathMode mode : PathMode.values()) {
            options.add(SingleOptionDialogInput.OptionEntry.create(
                    mode.id(), Component.text(mode.id()), mode == current));
        }
        return options;
    }

    private static String format(double value) {
        return String.format(java.util.Locale.ROOT, "%.1f", value);
    }

    private void show(Player player, Dialog dialog) {
        player.showDialog(dialog);
    }

    private void later(Player player, Runnable action) {
        if (!player.isOnline()) {
            return;
        }
        Bukkit.getScheduler().runTask(this.plugin, () -> {
            if (player.isOnline()) {
                action.run();
            }
        });
    }
}



