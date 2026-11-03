package dev.cameratrack.playback;

import java.util.Locale;

/**
 * 播放时驱动镜头的方式。
 */
public enum PlaybackMode {

    /**
     * 生成一个不可见、无 AI、无碰撞的标记盔甲架，把玩家切到旁观者模式并附身到它身上，
     * 再逐 tick 移动这个实体。客户端会对实体移动/旋转做插值，因此镜头按渲染帧率平滑移动，
     * 而不是像直接传送玩家那样每 tick 跳一格。
     */
    SPECTATOR_ENTITY("entity"),

    /** 旧方式：逐 tick 直接传送玩家本人。作为兜底与对照保留。 */
    PLAYER_TELEPORT("teleport");

    private final String id;

    PlaybackMode(String id) {
        this.id = id;
    }

    public String id() {
        return this.id;
    }

    public static PlaybackMode byId(String id) {
        if (id != null) {
            String lower = id.toLowerCase(Locale.ROOT);
            for (PlaybackMode mode : values()) {
                if (mode.id.equals(lower)) {
                    return mode;
                }
            }
        }
        return SPECTATOR_ENTITY;
    }
}
