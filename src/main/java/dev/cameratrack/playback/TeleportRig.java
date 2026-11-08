package dev.cameratrack.playback;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;

/**
 * 旧模式：每个 tick 把玩家本人传送到镜头位姿。
 *
 * <p>玩家位置包没有客户端插值，20 tps 下表现为 20 次/秒的“跳步”，
 * 只有在无法生成镜头实体时才退回使用。</p>
 */
final class TeleportRig implements CameraRig {

    private final String worldName;
    private double eyeOffset = 1.62D;
    private boolean hasLast;
    private double lastX;
    private double lastY;
    private double lastZ;
    private float lastYaw;
    private float lastPitch;
    private int idleTicks;

    TeleportRig(String worldName) {
        this.worldName = worldName;
    }

    @Override
    public void attach(Player player, Pose pose) {
        this.eyeOffset = player.getEyeHeight();
        World world = Bukkit.getWorld(this.worldName);
        if (world != null) {
            player.teleport(toFeet(pose, world));
            record(pose);
        }
    }

    @Override
    public void apply(Player player, Pose pose) {
        World world = Bukkit.getWorld(this.worldName);
        if (world == null) {
            return;
        }
        if (needsTeleport(pose)) {
            player.teleport(toFeet(pose, world));
            record(pose);
            this.idleTicks = 0;
        } else {
            this.idleTicks++;
        }
    }

    @Override
    public void snap(Player player, Pose pose) {
        World world = Bukkit.getWorld(this.worldName);
        if (world == null) {
            return;
        }
        player.teleport(toFeet(pose, world));
        record(pose);
        this.idleTicks = 0;
    }

    @Override
    public void detach(Player player) {
        // 无额外状态
    }

    @Override
    public void abort() {
        // 无额外状态
    }

    /** 停留阶段位姿不变时不必每 tick 都发包，但仍周期性重发一次以纠正客户端漂移。 */
    private boolean needsTeleport(Pose pose) {
        if (!this.hasLast) {
            return true;
        }
        if (this.idleTicks >= 20) {
            return true;
        }
        if (Math.abs(pose.x() - this.lastX) > 1.0E-3D
                || Math.abs(pose.y() - this.lastY) > 1.0E-3D
                || Math.abs(pose.z() - this.lastZ) > 1.0E-3D) {
            return true;
        }
        if (Math.abs(pose.pitch() - this.lastPitch) > 0.01F) {
            return true;
        }
        double deltaYaw = ((pose.yaw() - this.lastYaw) % 360.0D + 540.0D) % 360.0D - 180.0D;
        return Math.abs(deltaYaw) > 0.01D;
    }

    private void record(Pose pose) {
        this.hasLast = true;
        this.lastX = pose.x();
        this.lastY = pose.y();
        this.lastZ = pose.z();
        this.lastYaw = pose.yaw();
        this.lastPitch = pose.pitch();
    }

    private Location toFeet(Pose pose, World world) {
        Location location = pose.toLocation(world);
        location.setY(location.getY() - this.eyeOffset);
        return location;
    }
}
