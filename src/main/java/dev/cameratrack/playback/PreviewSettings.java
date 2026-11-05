package dev.cameratrack.playback;

import org.bukkit.Color;
import org.bukkit.configuration.ConfigurationSection;

/**
 * 轨迹预览的粒子参数，来自 {@code config.yml} 的 {@code preview} 段。
 *
 * <p>轨迹线用带颜色的 {@link org.bukkit.Particle#DUST}（亮绿色、细间距），
 * 节点位置用更大的红色 DUST 标记；整条预览会按 {@code duration-ticks} 持续重发，
 * 避免粒子在客户端约 1 秒后消失。</p>
 */
public record PreviewSettings(
        Color trailColor, float trailSize, double spacing, int maxPoints,
        Color nodeColor, float nodeSize, int nodeCount, double nodeSpread,
        int durationTicks, int refreshTicks) {

    public static final Color DEFAULT_TRAIL_COLOR = Color.fromRGB(0x00FF00);
    public static final Color DEFAULT_NODE_COLOR = Color.fromRGB(0xFF0000);

    public static final float DEFAULT_TRAIL_SIZE = 1.0F;
    public static final double DEFAULT_SPACING = 0.15D;
    public static final int DEFAULT_MAX_POINTS = 1200;

    public static final float DEFAULT_NODE_SIZE = 2.0F;
    public static final int DEFAULT_NODE_COUNT = 12;
    public static final double DEFAULT_NODE_SPREAD = 0.06D;

    public static final int DEFAULT_DURATION_TICKS = 200;
    public static final int DEFAULT_REFRESH_TICKS = 10;

    public static PreviewSettings from(ConfigurationSection config) {
        ConfigurationSection trail = config == null ? null : config.getConfigurationSection("preview.trail");
        ConfigurationSection node = config == null ? null : config.getConfigurationSection("preview.node");
        ConfigurationSection preview = config == null ? null : config.getConfigurationSection("preview");

        return new PreviewSettings(
                color(trail == null ? null : trail.getString("color"), DEFAULT_TRAIL_COLOR),
                (float) clamp(trail == null ? DEFAULT_TRAIL_SIZE : trail.getDouble("size", DEFAULT_TRAIL_SIZE), 0.1D, 10.0D),
                clamp(trail == null ? DEFAULT_SPACING : trail.getDouble("spacing", DEFAULT_SPACING), 0.01D, 4.0D),
                (int) clamp(trail == null ? DEFAULT_MAX_POINTS : trail.getInt("max-points", DEFAULT_MAX_POINTS), 10, 5000),
                color(node == null ? null : node.getString("color"), DEFAULT_NODE_COLOR),
                (float) clamp(node == null ? DEFAULT_NODE_SIZE : node.getDouble("size", DEFAULT_NODE_SIZE), 0.1D, 10.0D),
                (int) clamp(node == null ? DEFAULT_NODE_COUNT : node.getInt("count", DEFAULT_NODE_COUNT), 1, 100),
                clamp(node == null ? DEFAULT_NODE_SPREAD : node.getDouble("spread", DEFAULT_NODE_SPREAD), 0.0D, 2.0D),
                (int) clamp(preview == null ? DEFAULT_DURATION_TICKS : preview.getInt("duration-ticks", DEFAULT_DURATION_TICKS), 0, 2400),
                (int) clamp(preview == null ? DEFAULT_REFRESH_TICKS : preview.getInt("refresh-ticks", DEFAULT_REFRESH_TICKS), 1, 200)
        );
    }

    /** 预览持续秒数（用于聊天提示）。 */
    public double durationSeconds() {
        return this.durationTicks / 20.0D;
    }

    /** {@code #RRGGBB} / {@code RRGGBB}；非法值回退默认色。 */
    private static Color color(String raw, Color fallback) {
        if (raw == null) {
            return fallback;
        }
        String value = raw.trim();
        if (value.startsWith("#")) {
            value = value.substring(1);
        }
        if (!value.matches("[0-9a-fA-F]{6}")) {
            return fallback;
        }
        try {
            return Color.fromRGB(Integer.parseInt(value, 16));
        } catch (IllegalArgumentException exception) {
            return fallback;
        }
    }

    private static double clamp(double value, double min, double max) {
        if (!Double.isFinite(value)) {
            return min;
        }
        return Math.max(min, Math.min(max, value));
    }

    private static long clamp(long value, long min, long max) {
        return Math.max(min, Math.min(max, value));
    }
}
