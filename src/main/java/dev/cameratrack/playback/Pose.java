package dev.cameratrack.playback;

import org.bukkit.Location;
import org.bukkit.World;

/**
 * 某一时刻的相机位姿（位置 + 朝向）。
 */
public record Pose(double x, double y, double z, float yaw, float pitch) {

    public Location toLocation(World world) {
        return new Location(world, this.x, this.y, this.z, this.yaw, this.pitch);
    }
}
