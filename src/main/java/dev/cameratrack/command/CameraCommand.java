package dev.cameratrack.command;

import dev.cameratrack.CameraTrackPlugin;
import dev.cameratrack.lang.Lang;
import dev.cameratrack.playback.PlaybackManager;
import dev.cameratrack.playback.PlaybackMode;
import dev.cameratrack.text.TextComponents;
import dev.cameratrack.track.CameraNode;
import dev.cameratrack.track.CameraTrack;
import dev.cameratrack.track.Ease;
import dev.cameratrack.track.NodeTitle;
import dev.cameratrack.track.PathMode;
import dev.cameratrack.track.TrackStore;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * /camera 指令树。所有聊天提示都通过 {@link Lang} 的翻译键输出。
 */
public final class CameraCommand implements TabExecutor {

    private static final List<String> SUBCOMMANDS = List.of(
            "help", "create", "delete", "rename", "list", "info",
            "add", "insert", "remove", "nodes", "goto", "set",
            "play", "stop", "loop", "flow", "return", "preview", "ui", "tool", "save", "reload");

    private final CameraTrackPlugin plugin;
    private final TrackStore store;
    private final PlaybackManager playback;
    private final Lang lang;

    public CameraCommand(CameraTrackPlugin plugin, TrackStore store, PlaybackManager playback, Lang lang) {
        this.plugin = plugin;
        this.store = store;
        this.playback = playback;
        this.lang = lang;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        // 默认所有命令仅 OP 可用；非 OP 需要权限插件单独授予 cameratrack.play / cameratrack.admin
        if (!sender.hasPermission("cameratrack.admin") && !sender.hasPermission("cameratrack.play")) {
            this.lang.send(sender, "no-permission");
            return true;
        }
        if (args.length == 0) {
            help(sender, label);
            return true;
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "help" -> help(sender, label);
            case "create" -> create(sender, args, label);
            case "delete" -> delete(sender, args);
            case "rename" -> rename(sender, args);
            case "list" -> list(sender);
            case "info" -> info(sender, args);
            case "add" -> add(sender, args, label);
            case "insert" -> insert(sender, args, label);
            case "remove" -> remove(sender, args);
            case "nodes" -> nodes(sender, args);
            case "goto" -> gotoNode(sender, args);
            case "set" -> set(sender, args);
            case "play" -> play(sender, args);
            case "stop" -> stop(sender, args);
            case "loop" -> loop(sender, args);
            case "flow" -> flow(sender, args);
            case "return" -> returnSetting(sender, args);
            case "preview" -> preview(sender, args);
            case "ui" -> ui(sender, args);
            case "tool" -> tool(sender);
            case "save" -> {
                if (!admin(sender)) {
                    return true;
                }
                this.store.save();
                this.lang.send(sender, "save.success");
            }
            case "reload" -> {
                if (!admin(sender)) {
                    return true;
                }
                this.store.load();
                this.lang.send(sender, "reload.success", "count", this.store.tracks().size());
            }
            default -> this.lang.send(sender, "unknown-subcommand", "label", label);
        }
        return true;
    }

    // ------------------------------------------------------------------ 轨道管理

    private void create(CommandSender sender, String[] args, String label) {
        if (!admin(sender)) {
            return;
        }
        if (args.length < 2) {
            this.lang.send(sender, "usage.create", "label", label);
            return;
        }
        String name = args[1].toLowerCase(Locale.ROOT);
        if (!TrackStore.isValidName(name)) {
            this.lang.send(sender, "create.invalid-name");
            return;
        }
        if (this.store.get(name) != null) {
            this.lang.send(sender, "create.exists", "name", name);
            return;
        }
        this.store.create(name);
        this.store.save();
        this.lang.send(sender, "create.success", "name", name, "label", label);
    }

    private void delete(CommandSender sender, String[] args) {
        if (!admin(sender)) {
            return;
        }
        if (args.length < 2) {
            this.lang.send(sender, "usage.delete", "label", "camera");
            return;
        }
        if (!this.store.remove(args[1])) {
            this.lang.send(sender, "delete.not-found", "name", args[1]);
            return;
        }
        this.store.save();
        this.lang.send(sender, "delete.success", "name", args[1].toLowerCase(Locale.ROOT));
    }

    private void rename(CommandSender sender, String[] args) {
        if (!admin(sender)) {
            return;
        }
        if (args.length < 3) {
            this.lang.send(sender, "usage.rename", "label", "camera");
            return;
        }
        if (!TrackStore.isValidName(args[2])) {
            this.lang.send(sender, "create.invalid-name");
            return;
        }
        if (this.store.get(args[2]) != null) {
            this.lang.send(sender, "rename.exists", "name", args[2]);
            return;
        }
        if (!this.store.rename(args[1], args[2])) {
            this.lang.send(sender, "rename.not-found", "name", args[1]);
            return;
        }
        this.store.save();
        this.lang.send(sender, "rename.success", "name", args[2].toLowerCase(Locale.ROOT));
    }

    private void list(CommandSender sender) {
        if (this.store.tracks().isEmpty()) {
            this.lang.send(sender, "list.empty");
            return;
        }
        this.lang.send(sender, "list.header", "count", this.store.tracks().size());
        for (String name : this.store.names()) {
            CameraTrack track = this.store.get(name);
            this.lang.sendPlain(sender, "list.entry",
                    "name", name,
                    "nodes", track.size(),
                    "duration", format(track.totalDuration()),
                    "flow", this.lang.component(sender, track.flow() ? "common.on" : "common.off"),
                    "loop", this.lang.component(sender, track.loop() ? "common.on" : "common.off"));
        }
    }

    private void info(CommandSender sender, String[] args) {
        if (args.length < 2) {
            this.lang.send(sender, "usage.info", "label", "camera");
            return;
        }
        CameraTrack track = track(sender, args[1]);
        if (track == null) {
            return;
        }
        this.lang.send(sender, "info.header", "name", track.name());
        this.lang.sendPlain(sender, "info.nodes", "count", track.size());
        this.lang.sendPlain(sender, "info.duration", "duration", format(track.totalDuration()));
        this.lang.sendPlain(sender, "info.loop", "value", onOff(sender, track.loop()));
        this.lang.sendPlain(sender, "info.flow", "value", onOff(sender, track.flow()));
        this.lang.sendPlain(sender, "info.return", "value", yesNo(sender, track.returnToStart()));
        if (track.size() > 0) {
            this.lang.sendPlain(sender, "info.world", "world", track.get(0).world());
        }
    }

    private void loop(CommandSender sender, String[] args) {
        if (!admin(sender)) {
            return;
        }
        if (args.length < 3) {
            this.lang.send(sender, "usage.loop", "label", "camera");
            return;
        }
        CameraTrack track = track(sender, args[1]);
        if (track == null) {
            return;
        }
        Boolean value = parseOnOff(args[2]);
        if (value == null) {
            this.lang.send(sender, "loop.invalid");
            return;
        }
        track.setLoop(value);
        this.store.save();
        this.lang.send(sender, "loop.success", "name", track.name(), "value", onOff(sender, value));
    }

    private void flow(CommandSender sender, String[] args) {
        if (!admin(sender)) {
            return;
        }
        if (args.length < 3) {
            this.lang.send(sender, "usage.flow", "label", "camera");
            return;
        }
        CameraTrack track = track(sender, args[1]);
        if (track == null) {
            return;
        }
        Boolean value = parseOnOff(args[2]);
        if (value == null) {
            this.lang.send(sender, "flow.invalid");
            return;
        }
        track.setFlow(value);
        this.store.save();
        this.lang.send(sender, "flow.success", "name", track.name(), "value", onOff(sender, value));
    }

    private void returnSetting(CommandSender sender, String[] args) {
        if (!admin(sender)) {
            return;
        }
        if (args.length < 3) {
            this.lang.send(sender, "usage.return", "label", "camera");
            return;
        }
        CameraTrack track = track(sender, args[1]);
        if (track == null) {
            return;
        }
        Boolean value = parseOnOff(args[2]);
        if (value == null) {
            this.lang.send(sender, "return.invalid");
            return;
        }
        track.setReturnToStart(value);
        this.store.save();
        this.lang.send(sender, value ? "return.on" : "return.off", "name", track.name());
    }

    // ------------------------------------------------------------------ 节点编辑

    private void add(CommandSender sender, String[] args, String label) {
        if (!admin(sender)) {
            return;
        }
        Player player = player(sender);
        if (player == null) {
            return;
        }
        if (args.length < 2) {
            this.lang.send(sender, "usage.add", "label", label);
            return;
        }
        CameraTrack track = track(sender, args[1]);
        if (track == null) {
            return;
        }
        Double duration = args.length >= 3 ? number(sender, args[2]) : CameraNode.DEFAULT_DURATION;
        if (duration == null) {
            return;
        }
        if (duration < 0.0D || duration > 600.0D) {
            this.lang.send(sender, "add.duration-range");
            return;
        }
        Location location = player.getEyeLocation();
        if (location.getWorld() == null) {
            this.lang.send(sender, "add.world-missing");
            return;
        }
        track.add(CameraNode.at(location, CameraNode.DEFAULT_HOLD, duration, Ease.SMOOTH, ""));
        this.store.save();
        this.lang.send(sender, "add.success",
                "index", track.size(),
                "x", format(location.getX()),
                "y", format(location.getY()),
                "z", format(location.getZ()));
    }

    private void insert(CommandSender sender, String[] args, String label) {
        if (!admin(sender)) {
            return;
        }
        Player player = player(sender);
        if (player == null) {
            return;
        }
        if (args.length < 3) {
            this.lang.send(sender, "usage.insert", "label", label);
            return;
        }
        CameraTrack track = track(sender, args[1]);
        if (track == null) {
            return;
        }
        int index = index(sender, args[2], track, true);
        if (index < 0) {
            return;
        }
        Double duration = args.length >= 4 ? number(sender, args[3]) : CameraNode.DEFAULT_DURATION;
        if (duration == null || duration < 0.0D || duration > 600.0D) {
            if (duration != null) {
                this.lang.send(sender, "add.duration-range");
            }
            return;
        }
        Location location = player.getEyeLocation();
        if (location.getWorld() == null) {
            this.lang.send(sender, "add.world-missing");
            return;
        }
        track.insert(index, CameraNode.at(location, CameraNode.DEFAULT_HOLD, duration, Ease.SMOOTH, ""));
        this.store.save();
        this.lang.send(sender, "insert.success", "index", index + 1, "count", track.size());
    }

    private void remove(CommandSender sender, String[] args) {
        if (!admin(sender)) {
            return;
        }
        if (args.length < 3) {
            this.lang.send(sender, "usage.remove", "label", "camera");
            return;
        }
        CameraTrack track = track(sender, args[1]);
        if (track == null) {
            return;
        }
        int index = index(sender, args[2], track, false);
        if (index < 0) {
            return;
        }
        track.remove(index);
        this.store.save();
        this.lang.send(sender, "remove.success", "index", index + 1, "count", track.size());
    }

    private void nodes(CommandSender sender, String[] args) {
        if (args.length < 2) {
            this.lang.send(sender, "usage.nodes", "label", "camera");
            return;
        }
        CameraTrack track = track(sender, args[1]);
        if (track == null) {
            return;
        }
        if (track.size() == 0) {
            this.lang.send(sender, "nodes.empty", "name", track.name());
            return;
        }
        this.lang.send(sender, "nodes.header", "name", track.name());
        for (int i = 0; i < track.size(); i++) {
            CameraNode node = track.get(i);
            Component line = this.lang.component(sender, "nodes.entry",
                    "index", i + 1,
                    "x", format(node.x()),
                    "y", format(node.y()),
                    "z", format(node.z()),
                    "yaw", format(node.yaw()),
                    "pitch", format(node.pitch()),
                    "hold", format(node.hold()),
                    "duration", format(node.duration()),
                    "ease", node.ease().id(),
                    "path", node.path().id());
            if (!node.command().isBlank()) {
                line = line.append(this.lang.component(sender, "nodes.command", "command", node.command()));
            }
            NodeTitle title = node.title();
            if (title != null && !title.isEmpty()) {
                if (!title.text().isBlank()) {
                    line = line.append(this.lang.component(sender, "nodes.title", "title", shorten(title.text())));
                }
                if (!title.subtitle().isBlank()) {
                    line = line.append(this.lang.component(sender, "nodes.subtitle", "subtitle", shorten(title.subtitle())));
                }
            }
            sender.sendMessage(line);
        }
    }

    private void gotoNode(CommandSender sender, String[] args) {
        if (!admin(sender)) {
            return;
        }
        Player player = player(sender);
        if (player == null) {
            return;
        }
        if (args.length < 3) {
            this.lang.send(sender, "usage.goto", "label", "camera");
            return;
        }
        CameraTrack track = track(sender, args[1]);
        if (track == null) {
            return;
        }
        int index = index(sender, args[2], track, false);
        if (index < 0) {
            return;
        }
        if (this.playback.isPlaying(player)) {
            this.lang.send(sender, "goto.busy");
            return;
        }
        CameraNode node = track.get(index);
        World world = Bukkit.getWorld(node.world());
        if (world == null) {
            this.lang.send(sender, "goto.world-missing", "world", node.world());
            return;
        }
        // 节点记录的是镜头（眼睛）位置，传送时把脚部位置下移一个视高，镜头才会正好落在节点上
        Location target = node.toLocation(world);
        target.setY(target.getY() - player.getEyeHeight());
        player.teleport(target);
        this.lang.send(sender, "goto.success", "index", index + 1);
    }

    private void set(CommandSender sender, String[] args) {
        if (!admin(sender)) {
            return;
        }
        if (args.length < 5) {
            this.lang.send(sender, "usage.set", "label", "camera");
            return;
        }
        CameraTrack track = track(sender, args[1]);
        if (track == null) {
            return;
        }
        int index = index(sender, args[2], track, false);
        if (index < 0) {
            return;
        }
        CameraNode node = track.get(index);
        String field = args[3].toLowerCase(Locale.ROOT);
        String value = unquote(String.join(" ", Arrays.copyOfRange(args, 4, args.length)).trim());
        CameraNode updated;
        switch (field) {
            case "duration" -> {
                Double parsed = number(sender, value);
                if (parsed == null) {
                    return;
                }
                if (parsed < 0.0D || parsed > 600.0D) {
                    this.lang.send(sender, "add.duration-range");
                    return;
                }
                updated = node.withDuration(parsed);
            }
            case "hold" -> {
                Double parsed = number(sender, value);
                if (parsed == null) {
                    return;
                }
                if (parsed < 0.0D || parsed > 600.0D) {
                    this.lang.send(sender, "set.hold-range");
                    return;
                }
                updated = node.withHold(parsed);
            }
            case "ease" -> {
                Ease ease = Ease.byId(value, null);
                if (ease == null) {
                    this.lang.send(sender, "set.ease-invalid", "values", Ease.ids());
                    return;
                }
                updated = node.withEase(ease);
            }
            case "yaw" -> {
                Double parsed = number(sender, value);
                if (parsed == null) {
                    return;
                }
                updated = node.withRotation(parsed.floatValue(), node.pitch());
            }
            case "pitch" -> {
                Double parsed = number(sender, value);
                if (parsed == null) {
                    return;
                }
                updated = node.withRotation(node.yaw(), (float) Math.max(-90.0D, Math.min(90.0D, parsed)));
            }
            case "command" -> updated = node.withCommand(value);
            case "title" -> {
                if (value.equalsIgnoreCase("clear")) {
                    updated = node.withTitle(node.title().withText(""));
                } else {
                    if (!TextComponents.isValid(value, playerName(sender))) {
                        this.lang.send(sender, "title.invalid", "value", value);
                        return;
                    }
                    updated = node.withTitle(node.title().withText(value));
                }
            }
            case "subtitle" -> {
                if (value.equalsIgnoreCase("clear")) {
                    updated = node.withTitle(node.title().withSubtitle(""));
                } else {
                    if (!TextComponents.isValid(value, playerName(sender))) {
                        this.lang.send(sender, "title.invalid", "value", value);
                        return;
                    }
                    updated = node.withTitle(node.title().withSubtitle(value));
                }
            }
            case "title-fade-in" -> {
                Double parsed = titleSeconds(sender, value);
                if (parsed == null) {
                    return;
                }
                updated = node.withTitle(node.title().withFadeIn(parsed));
            }
            case "title-stay" -> {
                Double parsed = titleSeconds(sender, value);
                if (parsed == null) {
                    return;
                }
                updated = node.withTitle(node.title().withStay(parsed));
            }
            case "title-fade-out" -> {
                Double parsed = titleSeconds(sender, value);
                if (parsed == null) {
                    return;
                }
                updated = node.withTitle(node.title().withFadeOut(parsed));
            }
            case "path" -> {
                PathMode pathMode = PathMode.byId(value, null);
                if (pathMode == null) {
                    this.lang.send(sender, "set.path-invalid", "values", PathMode.ids());
                    return;
                }
                updated = node.withPath(pathMode);
            }
            default -> {
                this.lang.send(sender, "set.field-invalid");
                return;
            }
        }
        track.set(index, updated);
        this.store.save();
        this.lang.send(sender, "set.success", "index", index + 1, "field", field);
    }

    // ------------------------------------------------------------------ 播放

    private void play(CommandSender sender, String[] args) {
        if (!(sender.hasPermission("cameratrack.play") || sender.hasPermission("cameratrack.admin"))) {
            this.lang.send(sender, "play.no-permission");
            return;
        }
        Player player = player(sender);
        if (player == null) {
            return;
        }
        if (args.length < 2) {
            this.lang.send(sender, "usage.play", "label", "camera");
            return;
        }
        CameraTrack track = track(sender, args[1]);
        if (track == null) {
            return;
        }
        if (track.size() < 2) {
            this.lang.send(sender, "play.too-few", "count", track.size());
            return;
        }

        double speed = 1.0D;
        boolean loop = track.loop();
        boolean stay = !track.returnToStart();
        boolean flow = track.flow();
        PlaybackMode mode = this.plugin.defaultPlaybackMode();
        for (int i = 2; i < args.length; i++) {
            String argument = args[i].toLowerCase(Locale.ROOT);
            switch (argument) {
                case "--loop" -> loop = true;
                case "--noloop" -> loop = false;
                case "--stay" -> stay = true;
                case "--return" -> stay = false;
                case "--tp" -> mode = PlaybackMode.PLAYER_TELEPORT;
                case "--entity" -> mode = PlaybackMode.SPECTATOR_ENTITY;
                case "--flow" -> flow = true;
                case "--segmented" -> flow = false;
                default -> {
                    Double parsed = number(sender, argument);
                    if (parsed == null) {
                        return;
                    }
                    if (parsed < 0.1D || parsed > 20.0D) {
                        this.lang.send(sender, "play.speed-range");
                        return;
                    }
                    speed = parsed;
                }
            }
        }

        for (CameraNode node : track.nodes()) {
            if (Bukkit.getWorld(node.world()) == null) {
                this.lang.send(sender, "play.world-missing", "world", node.world());
                return;
            }
        }

        if (!this.playback.start(player, track, speed, loop, stay, mode, flow)) {
            this.lang.send(sender, "play.failed");
            return;
        }
        this.lang.send(sender, "play.started",
                "name", track.name(),
                "nodes", track.size(),
                "duration", format(track.totalDuration()),
                "speed", format(speed),
                "mode", this.lang.component(sender, mode == PlaybackMode.SPECTATOR_ENTITY
                        ? "play.mode.entity" : "play.mode.teleport"),
                "flow", this.lang.component(sender, flow ? "play.flow.on" : "play.flow.off"),
                "ending", this.lang.component(sender, stay ? "play.ending.stay" : "play.ending.return"));
    }

    private void stop(CommandSender sender, String[] args) {
        if (args.length >= 2 && sender.hasPermission("cameratrack.admin")) {
            Player target = Bukkit.getPlayerExact(args[1]);
            if (target == null) {
                this.lang.send(sender, "stop.target-missing", "name", args[1]);
                return;
            }
            if (this.playback.stop(target)) {
                this.lang.send(sender, "stop.target-success", "name", target.getName());
            } else {
                this.lang.send(sender, "stop.not-playing");
            }
            return;
        }
        if (!(sender.hasPermission("cameratrack.play") || sender.hasPermission("cameratrack.admin"))) {
            this.lang.send(sender, "stop.no-permission");
            return;
        }
        Player player = player(sender);
        if (player == null) {
            return;
        }
        if (this.playback.stop(player)) {
            this.lang.send(player, "stop.success");
        } else {
            this.lang.send(player, "stop.self-not-playing");
        }
    }

    private void preview(CommandSender sender, String[] args) {
        if (!admin(sender)) {
            return;
        }
        if (args.length < 2) {
            this.lang.send(sender, "usage.preview", "label", "camera");
            return;
        }
        CameraTrack track = track(sender, args[1]);
        if (track == null) {
            return;
        }
        if (track.size() < 2) {
            this.lang.send(sender, "preview.too-few");
            return;
        }
        World world = Bukkit.getWorld(track.get(0).world());
        if (world == null) {
            this.lang.send(sender, "preview.world-missing");
            return;
        }
        int limit = this.plugin.preview().render(sender instanceof Player player ? player : null, world, track);
        this.lang.send(sender, "preview.success",
                "count", limit,
                "seconds", formatSeconds(this.plugin.previewSettings().durationSeconds()));
    }

    // ------------------------------------------------------------------ 工具

    private void ui(CommandSender sender, String[] args) {
        if (!admin(sender)) {
            return;
        }
        Player player = player(sender);
        if (player == null) {
            return;
        }
        if (this.playback.isPlaying(player)) {
            this.lang.send(sender, "ui.busy");
            return;
        }
        if (args.length >= 2) {
            CameraTrack track = track(sender, args[1]);
            if (track == null) {
                return;
            }
            this.plugin.dialogs().openTrack(player, track.name());
        } else {
            this.plugin.dialogs().openMain(player);
        }
    }

    private void tool(CommandSender sender) {
        if (!admin(sender)) {
            return;
        }
        Player player = player(sender);
        if (player == null) {
            return;
        }
        ItemStack item = this.plugin.tool().createItem();
        Map<Integer, ItemStack> leftover = player.getInventory().addItem(item);
        for (ItemStack stack : leftover.values()) {
            player.getWorld().dropItemNaturally(player.getLocation(), stack);
        }
        this.lang.send(sender, "tool.given");
    }

    private void help(CommandSender sender, String label) {
        this.lang.send(sender, "help", "label", label, "eases", Ease.ids());
    }

    private boolean admin(CommandSender sender) {
        if (sender.hasPermission("cameratrack.admin")) {
            return true;
        }
        this.lang.send(sender, "admin.required");
        return false;
    }

    private Player player(CommandSender sender) {
        if (sender instanceof Player player) {
            return player;
        }
        this.lang.send(sender, "player-only");
        return null;
    }

    private CameraTrack track(CommandSender sender, String name) {
        CameraTrack track = this.store.get(name);
        if (track == null) {
            this.lang.send(sender, "track.not-found", "name", name);
        }
        return track;
    }

    /**
     * 解析玩家输入的 1 基序号；insert 时允许 size+1。
     */
    private int index(CommandSender sender, String raw, CameraTrack track, boolean allowAppend) {
        int parsed;
        try {
            parsed = Integer.parseInt(raw);
        } catch (NumberFormatException exception) {
            this.lang.send(sender, "index.not-number");
            return -1;
        }
        int max = allowAppend ? track.size() + 1 : track.size();
        if (parsed < 1 || parsed > max) {
            this.lang.send(sender, "index.range", "max", max);
            return -1;
        }
        return parsed - 1;
    }

    private Double number(CommandSender sender, String raw) {
        try {
            return Double.parseDouble(raw);
        } catch (NumberFormatException exception) {
            this.lang.send(sender, "number.invalid", "value", raw);
            return null;
        }
    }

    /** 标题三个时间参数的校验：0 - 600 秒。 */
    private Double titleSeconds(CommandSender sender, String raw) {
        Double parsed = number(sender, raw);
        if (parsed == null) {
            return null;
        }
        if (parsed < 0.0D || parsed > NodeTitle.MAX_SECONDS) {
            this.lang.send(sender, "title.range");
            return null;
        }
        return parsed;
    }

    /** 控制台执行时没有玩家名，{@code <player>} 占位符保持原样。 */
    private static String playerName(CommandSender sender) {
        return sender instanceof Player player ? player.getName() : null;
    }

    /**
     * 去掉玩家为了包含空格而写在两端的引号。
     *
     * <p>Bukkit 的旧式指令参数不会自动去掉引号，{@code set ... command "playsound ..."}
     * 会把引号一起存进数据；这里统一剥掉一层成对引号，让文档里的写法真正可用。</p>
     */
    private static String unquote(String value) {
        if (value.length() >= 2) {
            char first = value.charAt(0);
            char last = value.charAt(value.length() - 1);
            if ((first == '"' && last == '"') || (first == '\'' && last == '\'')) {
                return value.substring(1, value.length() - 1);
            }
        }
        return value;
    }

    /** 节点列表里展示标题时截断，避免一条记录占满聊天栏。 */
    private static String shorten(String value) {
        if (value == null) {
            return "";
        }
        return value.length() <= 48 ? value : value.substring(0, 48) + "…";
    }

    private Component onOff(CommandSender sender, boolean value) {
        return this.lang.component(sender, value ? "common.on" : "common.off");
    }

    private Component yesNo(CommandSender sender, boolean value) {
        return this.lang.component(sender, value ? "common.yes" : "common.no");
    }

    private static Boolean parseOnOff(String raw) {
        return switch (raw.toLowerCase(Locale.ROOT)) {
            case "on", "true", "yes", "1" -> Boolean.TRUE;
            case "off", "false", "no", "0" -> Boolean.FALSE;
            default -> null;
        };
    }

    private static String format(double value) {
        return String.format(Locale.ROOT, "%.2f", value);
    }

    private static String formatSeconds(double value) {
        return String.format(Locale.ROOT, "%.1f", value);
    }

    // ------------------------------------------------------------------ 补全

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("cameratrack.admin") && !sender.hasPermission("cameratrack.play")) {
            return List.of();
        }
        if (args.length == 1) {
            return filter(SUBCOMMANDS, args[0]);
        }
        String sub = args[0].toLowerCase(Locale.ROOT);

        if (args.length == 2) {
            return switch (sub) {
                case "delete", "rename", "info", "add", "insert", "remove", "nodes", "goto", "set",
                     "play", "loop", "flow", "return", "preview", "ui" -> filter(this.store.names(), args[1]);
                case "stop" -> filter(Bukkit.getOnlinePlayers().stream().map(Player::getName).toList(), args[1]);
                default -> List.of();
            };
        }

        if (args.length == 3) {
            CameraTrack track = this.store.get(args[1]);
            return switch (sub) {
                case "set", "goto", "remove" -> filter(indexes(track), args[2]);
                case "insert" -> filter(indexesPlus(track), args[2]);
                case "play" -> filter(List.of("1.0", "2.0", "0.5", "--loop", "--stay", "--tp", "--segmented"), args[2]);
                case "loop", "flow", "return" -> filter(List.of("on", "off"), args[2]);
                default -> List.of();
            };
        }

        if (args.length == 4 && sub.equals("set")) {
            return filter(List.of("duration", "hold", "ease", "yaw", "pitch", "path", "command",
                    "title", "subtitle", "title-fade-in", "title-stay", "title-fade-out"), args[3]);
        }
        if (args.length == 5 && sub.equals("set")) {
            String field = args[3].toLowerCase(Locale.ROOT);
            if (field.equals("ease")) {
                List<String> ids = new ArrayList<>();
                for (Ease ease : Ease.values()) {
                    ids.add(ease.id());
                }
                return filter(ids, args[4]);
            }
            if (field.equals("path")) {
                List<String> ids = new ArrayList<>();
                for (PathMode pathMode : PathMode.values()) {
                    ids.add(pathMode.id());
                }
                return filter(ids, args[4]);
            }
            if (field.equals("title-fade-in") || field.equals("title-stay") || field.equals("title-fade-out")) {
                return filter(List.of("0", "0.5", "1.0", "2.0", "3.5"), args[4]);
            }
            if (field.equals("command") || field.equals("title") || field.equals("subtitle")) {
                return List.of();
            }
        }
        if (sub.equals("play") && args.length >= 4) {
            return filter(List.of("--loop", "--stay", "--tp", "--segmented"), args[args.length - 1]);
        }
        return List.of();
    }

    private static List<String> indexes(CameraTrack track) {
        if (track == null) {
            return List.of();
        }
        List<String> result = new ArrayList<>(track.size());
        for (int i = 1; i <= track.size(); i++) {
            result.add(String.valueOf(i));
        }
        return result;
    }

    private static List<String> indexesPlus(CameraTrack track) {
        if (track == null) {
            return List.of();
        }
        List<String> result = new ArrayList<>(track.size() + 1);
        for (int i = 1; i <= track.size() + 1; i++) {
            result.add(String.valueOf(i));
        }
        return result;
    }

    private static List<String> filter(java.util.Collection<String> options, String prefix) {
        String lower = prefix.toLowerCase(Locale.ROOT);
        List<String> result = new ArrayList<>();
        for (String option : options) {
            if (option.toLowerCase(Locale.ROOT).startsWith(lower)) {
                result.add(option);
            }
        }
        return result;
    }
}
