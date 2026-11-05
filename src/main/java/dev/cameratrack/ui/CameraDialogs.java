package dev.cameratrack.ui;

import dev.cameratrack.CameraTrackPlugin;
import dev.cameratrack.lang.Lang;
import dev.cameratrack.playback.PlaybackManager;
import dev.cameratrack.text.TextComponents;
import dev.cameratrack.track.CameraNode;
import dev.cameratrack.track.CameraTrack;
import dev.cameratrack.track.Ease;
import dev.cameratrack.track.NodeTitle;
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
 *
 * <p>界面上的所有文案（标题、按钮、说明、输入标签、报错）都来自 {@link Lang} 的 {@code gui.*} 键，
 * 随 {@code config.yml} 的 {@code language} 设置切换；文本值本身是 MiniMessage。</p>
 */
public final class CameraDialogs {

    private static final int MAX_NODE_BUTTONS = 45;
    private static final float MAX_SECONDS = 600.0F;
    private static final int TITLE_TEXT_LENGTH = 512;
    private static final int SUBTITLE_TEXT_LENGTH = 256;

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
                    text(player, "gui.main.entry", "name", name, "nodes", track.size()),
                    text(player, "gui.main.entry.tooltip", "name", name),
                    view -> later(player, () -> openTrack(player, name))));
        }
        if (buttons.isEmpty()) {
            buttons.add(closeButton(player));
        }
        buttons.add(button(text(player, "gui.main.new"),
                text(player, "gui.main.new.tooltip"),
                view -> later(player, () -> openCreate(player))));
        if (buttons.size() > 1) {
            buttons.add(closeButton(player));
        }

        List<DialogBody> body = List.of(
                body(player, "gui.main.body.count", "count", this.store.tracks().size()),
                body(player, "gui.main.body.hint"));
        show(player, dialog(text(player, "gui.main.title"), body, List.of(), buttons, 2));
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
                    text(player, "gui.track.node",
                            "index", i + 1,
                            "x", format(node.x()), "y", format(node.y()), "z", format(node.z())),
                    text(player, "gui.track.node.tooltip", "index", i + 1),
                    view -> later(player, () -> openNode(player, name, index))));
        }
        buttons.add(button(text(player, "gui.track.add"),
                text(player, "gui.track.add.tooltip"),
                view -> {
                    addNodeAtPlayer(player, name);
                    later(player, () -> openTrack(player, name));
                }));
        buttons.add(button(text(player, "gui.track.play"),
                text(player, "gui.track.play.tooltip"),
                view -> later(player, () -> play(player, name))));
        buttons.add(button(
                text(player, "gui.track.loop", "value", onOff(player, track.loop())),
                text(player, "gui.track.loop.tooltip"),
                view -> {
                    CameraTrack current = this.store.get(name);
                    if (current != null) {
                        current.setLoop(!current.loop());
                        this.store.save();
                    }
                    later(player, () -> openTrack(player, name));
                }));
        buttons.add(button(
                text(player, "gui.track.flow", "value", onOff(player, track.flow())),
                text(player, "gui.track.flow.tooltip"),
                view -> {
                    CameraTrack current = this.store.get(name);
                    if (current != null) {
                        current.setFlow(!current.flow());
                        this.store.save();
                    }
                    later(player, () -> openTrack(player, name));
                }));
        buttons.add(button(
                text(player, "gui.track.return", "value", onOff(player, track.returnToStart())),
                text(player, "gui.track.return.tooltip"),
                view -> {
                    CameraTrack current = this.store.get(name);
                    if (current != null) {
                        current.setReturnToStart(!current.returnToStart());
                        this.store.save();
                    }
                    later(player, () -> openTrack(player, name));
                }));
        buttons.add(button(text(player, "gui.track.delete"),
                text(player, "gui.track.delete.tooltip"),
                view -> later(player, () -> confirmDeleteTrack(player, name))));
        buttons.add(button(text(player, "gui.track.back"),
                text(player, "gui.track.back.tooltip"),
                view -> later(player, () -> openMain(player))));

        List<DialogBody> body = new ArrayList<>();
        body.add(body(player, "gui.track.body.summary",
                "nodes", track.size(),
                "duration", format(track.totalDuration()),
                "loop", onOff(player, track.loop()),
                "flow", onOff(player, track.flow()),
                "return", onOff(player, track.returnToStart())));
        if (track.size() == 0) {
            body.add(body(player, "gui.track.body.empty"));
        } else if (track.size() > shown) {
            body.add(body(player, "gui.track.body.too-many", "shown", shown));
        }
        body.add(body(player, "gui.track.body.path-note"));

        show(player, dialog(text(player, "gui.track.title", "name", name), body, List.of(), buttons, 1));
    }

    // ------------------------------------------------------------------ 节点详情

    public void openNode(Player player, String trackName, int index) {
        CameraTrack track = this.store.get(trackName);
        if (track == null || index < 0 || index >= track.size()) {
            openTrack(player, trackName);
            return;
        }
        CameraNode node = track.get(index);
        NodeTitle title = node.title();
        String name = track.name();

        List<DialogInput> inputs = List.of(
                DialogInput.text("duration", text(player, "gui.node.input.duration"))
                        .initial(String.valueOf(node.duration()))
                        .maxLength(12)
                        .width(320)
                        .build(),
                DialogInput.text("hold", text(player, "gui.node.input.hold"))
                        .initial(String.valueOf(node.hold()))
                        .maxLength(12)
                        .width(320)
                        .build(),
                DialogInput.singleOption("ease", text(player, "gui.node.input.ease"), easeOptions(node.ease()))
                        .width(320)
                        .build(),
                DialogInput.singleOption("path", text(player, "gui.node.input.path"), pathOptions(node.path()))
                        .width(320)
                        .build(),
                DialogInput.text("command", text(player, "gui.node.input.command"))
                        .initial(truncate(node.command(), 256))
                        .maxLength(256)
                        .width(320)
                        .build(),
                DialogInput.text("title", text(player, "gui.node.input.title"))
                        .initial(truncate(title.text(), TITLE_TEXT_LENGTH))
                        .maxLength(TITLE_TEXT_LENGTH)
                        .width(320)
                        .build(),
                DialogInput.text("subtitle", text(player, "gui.node.input.subtitle"))
                        .initial(truncate(title.subtitle(), SUBTITLE_TEXT_LENGTH))
                        .maxLength(SUBTITLE_TEXT_LENGTH)
                        .width(320)
                        .build(),
                DialogInput.text("title_fade_in", text(player, "gui.node.input.fade-in"))
                        .initial(String.valueOf(title.fadeIn()))
                        .maxLength(12)
                        .width(160)
                        .build(),
                DialogInput.text("title_stay", text(player, "gui.node.input.stay"))
                        .initial(String.valueOf(title.stay()))
                        .maxLength(12)
                        .width(160)
                        .build(),
                DialogInput.text("title_fade_out", text(player, "gui.node.input.fade-out"))
                        .initial(String.valueOf(title.fadeOut()))
                        .maxLength(12)
                        .width(160)
                        .build()
        );

        List<ActionButton> buttons = new ArrayList<>();
        buttons.add(button(text(player, "gui.node.save"),
                text(player, "gui.node.save.tooltip"),
                view -> {
                    applyNode(player, name, index, view);
                    later(player, () -> openNode(player, name, index));
                }));
        buttons.add(button(text(player, "gui.node.goto"),
                text(player, "gui.node.goto.tooltip"),
                view -> later(player, () -> teleportToNode(player, name, index))));
        buttons.add(button(text(player, "gui.node.update"),
                text(player, "gui.node.update.tooltip"),
                view -> {
                    updateNodePose(player, name, index);
                    later(player, () -> openNode(player, name, index));
                }));
        buttons.add(button(text(player, "gui.node.up"),
                text(player, "gui.node.up.tooltip"),
                view -> {
                    swap(player, name, index, index - 1);
                    later(player, () -> openNode(player, name, Math.max(0, index - 1)));
                }));
        buttons.add(button(text(player, "gui.node.down"),
                text(player, "gui.node.down.tooltip"),
                view -> {
                    swap(player, name, index, index + 1);
                    later(player, () -> openNode(player, name, Math.min(track.size() - 1, index + 1)));
                }));
        buttons.add(button(text(player, "gui.node.delete"),
                text(player, "gui.node.delete.tooltip"),
                view -> later(player, () -> confirmDeleteNode(player, name, index))));
        buttons.add(button(text(player, "gui.node.back"),
                text(player, "gui.node.back.tooltip"),
                view -> later(player, () -> openTrack(player, name))));

        List<DialogBody> body = new ArrayList<>();
        body.add(body(player, "gui.node.body.pose",
                "x", format(node.x()), "y", format(node.y()), "z", format(node.z()),
                "yaw", format(node.yaw()), "pitch", format(node.pitch())));
        body.add(body(player, "gui.node.body.current",
                "hold", format(node.hold()),
                "duration", format(node.duration()),
                "ease", node.ease().id(),
                "path", node.path().id()));
        body.add(body(player, "gui.node.body.hint"));
        body.add(body(player, "gui.node.body.title-hint"));

        show(player, dialog(text(player, "gui.node.title", "index", index + 1, "track", name),
                body, inputs, buttons, 1));
    }

    // ------------------------------------------------------------------ 新建 / 删除

    private void openCreate(Player player) {
        List<DialogInput> inputs = List.of(
                DialogInput.text("name", text(player, "gui.create.input.name"))
                        .maxLength(32)
                        .width(320)
                        .build());
        List<ActionButton> buttons = new ArrayList<>();
        buttons.add(button(text(player, "gui.create.button"),
                text(player, "gui.create.button.tooltip"),
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
        buttons.add(button(text(player, "gui.track.back"),
                text(player, "gui.track.back.tooltip"),
                view -> later(player, () -> openMain(player))));

        show(player, dialog(text(player, "gui.create.title"),
                List.of(body(player, "gui.create.body")),
                inputs, buttons, 1));
    }

    private void confirmDeleteTrack(Player player, String trackName) {
        ActionButton yes = ActionButton.builder(text(player, "gui.confirm.delete-track.yes"))
                .tooltip(text(player, "gui.confirm.delete-track.yes.tooltip"))
                .action(customClick((view, audience) -> {
                    this.store.remove(trackName);
                    this.store.save();
                    later(player, () -> openMain(player));
                }))
                .build();
        show(player, Dialog.create(builder -> builder.empty()
                .base(DialogBase.builder(text(player, "gui.confirm.delete-track.title", "name", trackName))
                        .body(List.of(body(player, "gui.confirm.delete-track.body")))
                        .build())
                .type(DialogType.confirmation(yes, cancelButton(player, () -> openTrack(player, trackName))))));
    }

    private void confirmDeleteNode(Player player, String trackName, int index) {
        ActionButton yes = ActionButton.builder(text(player, "gui.confirm.delete-node.yes"))
                .tooltip(text(player, "gui.confirm.delete-node.yes.tooltip"))
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
                .base(DialogBase.builder(text(player, "gui.confirm.delete-node.title", "index", index + 1))
                        .body(List.of(body(player, "gui.confirm.delete-node.body")))
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
        String titleText = view.getText("title");
        String subtitleText = view.getText("subtitle");
        String fadeInText = view.getText("title_fade_in");
        String stayText = view.getText("title_stay");
        String fadeOutText = view.getText("title_fade_out");

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
        Double fadeIn = parseSeconds(fadeInText);
        if (fadeInText != null && !fadeInText.isBlank() && fadeIn == null) {
            this.lang.send(player, "number.invalid", "value", fadeInText);
            return;
        }
        Double stay = parseSeconds(stayText);
        if (stayText != null && !stayText.isBlank() && stay == null) {
            this.lang.send(player, "number.invalid", "value", stayText);
            return;
        }
        Double fadeOut = parseSeconds(fadeOutText);
        if (fadeOutText != null && !fadeOutText.isBlank() && fadeOut == null) {
            this.lang.send(player, "number.invalid", "value", fadeOutText);
            return;
        }
        // 标题文本既接受 MiniMessage，也接受原版 JSON 文本组件；解析失败时拒绝保存，避免播放时静默丢失
        if (!TextComponents.isValid(titleText, player.getName())) {
            this.lang.send(player, "gui.error-title", "value", titleText);
            return;
        }
        if (!TextComponents.isValid(subtitleText, player.getName())) {
            this.lang.send(player, "gui.error-title", "value", subtitleText);
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

        NodeTitle title = updated.title();
        boolean titleChanged = false;
        if (titleText != null) {
            title = title.withText(titleText);
            titleChanged = true;
        }
        if (subtitleText != null) {
            title = title.withSubtitle(subtitleText);
            titleChanged = true;
        }
        if (fadeIn != null) {
            title = title.withFadeIn(clamp(fadeIn));
            titleChanged = true;
        }
        if (stay != null) {
            title = title.withStay(clamp(stay));
            titleChanged = true;
        }
        if (fadeOut != null) {
            title = title.withFadeOut(clamp(fadeOut));
            titleChanged = true;
        }
        if (titleChanged) {
            updated = updated.withTitle(title);
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

    private static String truncate(String value, int limit) {
        if (value == null) {
            return "";
        }
        return value.length() > limit ? value.substring(0, limit) : value;
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

    private ActionButton closeButton(Player player) {
        return ActionButton.builder(text(player, "gui.close"))
                .tooltip(text(player, "gui.close.tooltip"))
                .build();
    }

    private ActionButton cancelButton(Player player, Runnable reopen) {
        return ActionButton.builder(text(player, "gui.cancel"))
                .tooltip(text(player, "gui.cancel.tooltip"))
                .action(customClick((view, audience) -> later(player, reopen)))
                .build();
    }

    private Component text(Player player, String key, Object... placeholders) {
        return this.lang.component(player, key, placeholders);
    }

    private Component onOff(Player player, boolean value) {
        return text(player, value ? "common.on" : "common.off");
    }

    private DialogBody body(Player player, String key, Object... placeholders) {
        return DialogBody.plainMessage(text(player, key, placeholders));
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
