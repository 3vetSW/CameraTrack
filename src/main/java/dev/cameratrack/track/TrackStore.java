package dev.cameratrack.track;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.logging.Level;

/**
 * tracks.yml 的读写与轨道集合管理。
 */
public final class TrackStore {

    private final JavaPlugin plugin;
    private final File file;
    private final Map<String, CameraTrack> tracks = new LinkedHashMap<>();

    public TrackStore(JavaPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "tracks.yml");
    }

    public static boolean isValidName(String name) {
        return name != null && name.matches("[A-Za-z0-9_-]{1,32}");
    }

    public void load() {
        this.tracks.clear();
        if (!this.file.isFile()) {
            return;
        }
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(this.file);
        ConfigurationSection root = yaml.getConfigurationSection("tracks");
        if (root == null) {
            return;
        }
        for (String rawName : root.getKeys(false)) {
            ConfigurationSection section = root.getConfigurationSection(rawName);
            if (section == null) {
                continue;
            }
            String name = rawName.toLowerCase(Locale.ROOT);
            CameraTrack track = new CameraTrack(name);
            track.setLoop(section.getBoolean("loop", false));
            track.setFlow(section.getBoolean("flow", true));
            track.setReturnToStart(section.getBoolean("return-to-start", true));
            for (Object entry : section.getList("nodes", List.of())) {
                if (!(entry instanceof Map<?, ?> map)) {
                    continue;
                }
                track.add(readNode(map));
            }
            this.tracks.put(name, track);
        }
    }

    private static CameraNode readNode(Map<?, ?> map) {
        Object command = map.get("command");
        Object world = map.get("world");
        Object ease = map.get("ease");
        Object path = map.get("path");
        Object type = map.get("type");
        return new CameraNode(
                world == null ? "world" : world.toString(),
                asDouble(map.get("x"), 0.0D),
                asDouble(map.get("y"), 64.0D),
                asDouble(map.get("z"), 0.0D),
                (float) asDouble(map.get("yaw"), 0.0D),
                (float) asDouble(map.get("pitch"), 0.0D),
                asDouble(map.get("hold"), CameraNode.DEFAULT_HOLD),
                asDouble(map.get("duration"), CameraNode.DEFAULT_DURATION),
                Ease.byId(ease == null ? "smooth" : ease.toString(), Ease.SMOOTH),
                PathMode.byId(path == null ? "auto" : path.toString(), PathMode.AUTO),
                command == null ? "" : command.toString(),
                readTitle(map.get("title")),
                NodeType.byId(type == null ? "default" : type.toString(), NodeType.DEFAULT)
        );
    }

    /**
     * 读取节点的 title 设置。
     *
     * <p>既支持完整写法（{@code title: {text: ..., subtitle: ..., fade-in: ...}}），
     * 也支持简写（{@code title: "一行文字"}，等价于只设置 text）。</p>
     */
    private static NodeTitle readTitle(Object value) {
        if (value instanceof Map<?, ?> map) {
            Object text = map.get("text");
            Object subtitle = map.get("subtitle");
            return new NodeTitle(
                    text == null ? "" : text.toString(),
                    subtitle == null ? "" : subtitle.toString(),
                    asDouble(map.get("fade-in"), NodeTitle.DEFAULT_FADE_IN),
                    asDouble(map.get("stay"), NodeTitle.DEFAULT_STAY),
                    asDouble(map.get("fade-out"), NodeTitle.DEFAULT_FADE_OUT)
            );
        }
        if (value instanceof String string) {
            return NodeTitle.EMPTY.withText(string);
        }
        return NodeTitle.EMPTY;
    }

    private static double asDouble(Object value, double fallback) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        if (value != null) {
            try {
                return Double.parseDouble(value.toString());
            } catch (NumberFormatException ignored) {
                // fall through
            }
        }
        return fallback;
    }

    public void save() {
        YamlConfiguration yaml = new YamlConfiguration();
        for (CameraTrack track : this.tracks.values()) {
            String base = "tracks." + track.name();
            yaml.set(base + ".loop", track.loop());
            yaml.set(base + ".flow", track.flow());
            yaml.set(base + ".return-to-start", track.returnToStart());
            List<Map<String, Object>> list = new ArrayList<>(track.size());
            for (CameraNode node : track.nodes()) {
                Map<String, Object> entry = new LinkedHashMap<>();
                entry.put("world", node.world());
                entry.put("x", round(node.x()));
                entry.put("y", round(node.y()));
                entry.put("z", round(node.z()));
                entry.put("yaw", round(node.yaw()));
                entry.put("pitch", round(node.pitch()));
                entry.put("hold", round(node.hold()));
                entry.put("duration", round(node.duration()));
                entry.put("ease", node.ease().id());
                if (node.path() != PathMode.AUTO) {
                    entry.put("path", node.path().id());
                }
                if (node.type() != NodeType.DEFAULT) {
                    entry.put("type", node.type().id());
                }
                if (!node.command().isBlank()) {
                    entry.put("command", node.command());
                }
                NodeTitle title = node.title();
                if (title != null && !title.isEmpty()) {
                    Map<String, Object> titleMap = new LinkedHashMap<>();
                    titleMap.put("text", title.text());
                    if (!title.subtitle().isBlank()) {
                        titleMap.put("subtitle", title.subtitle());
                    }
                    titleMap.put("fade-in", round(title.fadeIn()));
                    titleMap.put("stay", round(title.stay()));
                    titleMap.put("fade-out", round(title.fadeOut()));
                    entry.put("title", titleMap);
                }
                list.add(entry);
            }
            yaml.set(base + ".nodes", list);
        }
        try {
            File folder = this.file.getParentFile();
            if (folder != null && !folder.isDirectory() && !folder.mkdirs()) {
                throw new IOException("无法创建插件数据目录: " + folder);
            }
            File temp = new File(folder, "tracks.yml.tmp");
            yaml.save(temp);
            Files.move(temp.toPath(), this.file.toPath(), StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException exception) {
            this.plugin.getLogger().log(Level.SEVERE, "保存 tracks.yml 失败", exception);
        }
    }

    private static double round(double value) {
        return Math.round(value * 1000.0D) / 1000.0D;
    }

    public CameraTrack get(String name) {
        return this.tracks.get(name.toLowerCase(Locale.ROOT));
    }

    public CameraTrack create(String name) {
        String key = name.toLowerCase(Locale.ROOT);
        CameraTrack track = new CameraTrack(key);
        this.tracks.put(key, track);
        return track;
    }

    public boolean remove(String name) {
        return this.tracks.remove(name.toLowerCase(Locale.ROOT)) != null;
    }

    public boolean rename(String from, String to) {
        CameraTrack old = this.tracks.remove(from.toLowerCase(Locale.ROOT));
        if (old == null) {
            return false;
        }
        CameraTrack renamed = new CameraTrack(to.toLowerCase(Locale.ROOT));
        renamed.setLoop(old.loop());
        renamed.setFlow(old.flow());
        renamed.setReturnToStart(old.returnToStart());
        for (CameraNode node : old.nodes()) {
            renamed.add(node);
        }
        this.tracks.put(to.toLowerCase(Locale.ROOT), renamed);
        return true;
    }

    public Set<String> names() {
        return new TreeSet<>(this.tracks.keySet());
    }

    public Set<CameraTrack> tracks() {
        return new LinkedHashSet<>(this.tracks.values());
    }
}
