package dev.cameratrack.track;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.util.Vector;

import java.util.Objects;

/**
 * 摄像轨道上的一个镜头节点（关键帧）。
 *
 * @param world    所在世界名
 * @param x        相机 X
 * @param y        相机 Y（眼睛高度）
 * @param z        相机 Z
 * @param yaw      水平朝向
 * @param pitch    垂直朝向
 * @param hold     到达该节点后停留的秒数
 * @param duration 从该节点飞到下一个节点所需的秒数
 * @param ease     这一段的缓动曲线
 * @param path     这一段的路径插值方式（自动 / 直线 / 样条）
 * @param command  到达该节点时以控制台身份执行的指令（可留空，用于音效/粒子等演出）
 * @param title    到达该节点时显示在屏幕上的标题（可留空，见 {@link NodeTitle}）
 */
public record CameraNode(
        String world,
        double x, double y, double z,
        float yaw, float pitch,
        double hold,
        double duration,
        Ease ease,
        PathMode path,
        String command,
        NodeTitle title
) {

    public static final double DEFAULT_HOLD = 0.5D;
    public static final double DEFAULT_DURATION = 3.0D;

    public CameraNode {
        Objects.requireNonNull(world, "world");
        Objects.requireNonNull(ease, "ease");
        Objects.requireNonNull(path, "path");
        if (command == null) {
            command = "";
        }
        if (title == null) {
            title = NodeTitle.EMPTY;
        }
        hold = Math.max(0.0D, hold);
        duration = Math.max(0.0D, duration);
    }

    public static CameraNode at(Location location, double hold, double duration, Ease ease, String command) {
        Objects.requireNonNull(location.getWorld(), "location.world");
        return new CameraNode(
                location.getWorld().getName(),
                location.getX(), location.getY(), location.getZ(),
                location.getYaw(), location.getPitch(),
                hold, duration, ease, PathMode.AUTO, command, NodeTitle.EMPTY
        );
    }

    public Vector position() {
        return new Vector(this.x, this.y, this.z);
    }

    public Location toLocation(World world) {
        return new Location(world, this.x, this.y, this.z, this.yaw, this.pitch);
    }

    /** 保留其它设置，只把位置/朝向替换成给定位置（用于“更新为当前视角”）。 */
    public CameraNode withPose(Location location) {
        Objects.requireNonNull(location.getWorld(), "location.world");
        return new CameraNode(location.getWorld().getName(),
                location.getX(), location.getY(), location.getZ(),
                location.getYaw(), location.getPitch(),
                this.hold, this.duration, this.ease, this.path, this.command, this.title);
    }

    public CameraNode withDuration(double value) {
        return new CameraNode(this.world, this.x, this.y, this.z, this.yaw, this.pitch,
                this.hold, value, this.ease, this.path, this.command, this.title);
    }

    public CameraNode withHold(double value) {
        return new CameraNode(this.world, this.x, this.y, this.z, this.yaw, this.pitch,
                value, this.duration, this.ease, this.path, this.command, this.title);
    }

    public CameraNode withEase(Ease value) {
        return new CameraNode(this.world, this.x, this.y, this.z, this.yaw, this.pitch,
                this.hold, this.duration, value, this.path, this.command, this.title);
    }

    public CameraNode withPath(PathMode value) {
        return new CameraNode(this.world, this.x, this.y, this.z, this.yaw, this.pitch,
                this.hold, this.duration, this.ease, value, this.command, this.title);
    }

    public CameraNode withCommand(String value) {
        return new CameraNode(this.world, this.x, this.y, this.z, this.yaw, this.pitch,
                this.hold, this.duration, this.ease, this.path, value, this.title);
    }

    public CameraNode withTitle(NodeTitle value) {
        return new CameraNode(this.world, this.x, this.y, this.z, this.yaw, this.pitch,
                this.hold, this.duration, this.ease, this.path, this.command,
                value == null ? NodeTitle.EMPTY : value);
    }

    public CameraNode withRotation(float newYaw, float newPitch) {
        return new CameraNode(this.world, this.x, this.y, this.z, newYaw, newPitch,
                this.hold, this.duration, this.ease, this.path, this.command, this.title);
    }
}
