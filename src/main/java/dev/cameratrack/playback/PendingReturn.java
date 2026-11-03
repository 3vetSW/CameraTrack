package dev.cameratrack.playback;

/**
 * 一次未正常结束的播放的恢复快照：玩家在播放前的位置与状态。
 */
public record PendingReturn(
        String world,
        double x, double y, double z,
        float yaw, float pitch,
        String gameMode,
        boolean allowFlight,
        boolean flying,
        boolean invulnerable,
        float flySpeed,
        float walkSpeed,
        String track,
        long createdAt
) {
}
