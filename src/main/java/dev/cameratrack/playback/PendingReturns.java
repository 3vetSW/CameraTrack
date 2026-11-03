package dev.cameratrack.playback;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;

/**
 * 保存“播放途中退出/崩溃”的恢复快照，持久化到 {@code pending-returns.yml}。
 *
 * <p>快照在播放开始时写入，正常结束（或 /camera stop）后删除。玩家退出时若仍有快照，
 * 下次进入服务器会被送回播放起点并还原游戏模式/飞行/速度等状态。</p>
 */
public final class PendingReturns {

    private final JavaPlugin plugin;
    private final File file;
    private final Map<UUID, PendingReturn> entries = new LinkedHashMap<>();

    public PendingReturns(JavaPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "pending-returns.yml");
    }

    public void load() {
        this.entries.clear();
        if (!this.file.isFile()) {
            return;
        }
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(this.file);
        ConfigurationSection root = yaml.getConfigurationSection("returns");
        if (root == null) {
            return;
        }
        for (String key : root.getKeys(false)) {
            UUID id;
            try {
                id = UUID.fromString(key);
            } catch (IllegalArgumentException exception) {
                continue;
            }
            ConfigurationSection section = root.getConfigurationSection(key);
            if (section == null) {
                continue;
            }
            this.entries.put(id, new PendingReturn(
                    section.getString("world", "world"),
                    section.getDouble("x"),
                    section.getDouble("y"),
                    section.getDouble("z"),
                    (float) section.getDouble("yaw"),
                    (float) section.getDouble("pitch"),
                    section.getString("gamemode", "SURVIVAL"),
                    section.getBoolean("allow-flight"),
                    section.getBoolean("flying"),
                    section.getBoolean("invulnerable"),
                    (float) section.getDouble("fly-speed", 0.1D),
                    (float) section.getDouble("walk-speed", 0.2D),
                    section.getString("track", ""),
                    section.getLong("created-at", System.currentTimeMillis())
            ));
        }
    }

    public void save() {
        YamlConfiguration yaml = new YamlConfiguration();
        for (Map.Entry<UUID, PendingReturn> entry : this.entries.entrySet()) {
            String base = "returns." + entry.getKey();
            PendingReturn value = entry.getValue();
            yaml.set(base + ".world", value.world());
            yaml.set(base + ".x", value.x());
            yaml.set(base + ".y", value.y());
            yaml.set(base + ".z", value.z());
            yaml.set(base + ".yaw", value.yaw());
            yaml.set(base + ".pitch", value.pitch());
            yaml.set(base + ".gamemode", value.gameMode());
            yaml.set(base + ".allow-flight", value.allowFlight());
            yaml.set(base + ".flying", value.flying());
            yaml.set(base + ".invulnerable", value.invulnerable());
            yaml.set(base + ".fly-speed", value.flySpeed());
            yaml.set(base + ".walk-speed", value.walkSpeed());
            yaml.set(base + ".track", value.track());
            yaml.set(base + ".created-at", value.createdAt());
        }
        try {
            File folder = this.file.getParentFile();
            if (folder != null && !folder.isDirectory() && !folder.mkdirs()) {
                throw new IOException("无法创建插件数据目录: " + folder);
            }
            File temp = new File(folder, "pending-returns.yml.tmp");
            yaml.save(temp);
            Files.move(temp.toPath(), this.file.toPath(), StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException exception) {
            this.plugin.getLogger().log(Level.SEVERE, "保存 pending-returns.yml 失败", exception);
        }
    }

    public PendingReturn get(UUID playerId) {
        return this.entries.get(playerId);
    }

    public void remember(UUID playerId, PendingReturn value) {
        this.entries.put(playerId, value);
        save();
    }

    public void remove(UUID playerId) {
        if (this.entries.remove(playerId) != null) {
            save();
        }
    }
}

