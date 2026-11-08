package dev.cameratrack.playback;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

import java.util.UUID;

/**
 * 实体运镜（推荐方式）。
 *
 * <p>生成一个“镜头实体”——标记模式盔甲架：
 * <ul>
 *     <li>不可见：{@code setInvisible} + marker，模型与碰撞箱都是 0 尺寸</li>
 *     <li>无 AI、无重力、静音、无敌、不持久化、不可推动/交互</li>
 * </ul>
 * 玩家切到旁观者模式后 {@code setSpectatorTarget} 附身到它身上，相机就变成它的视角。
 * 之后每个 tick 只移动这个实体：服务端发给客户端的是实体相对位移包
 * （{@code ClientboundMoveEntityPacket}），客户端会在渲染帧之间做插值，
 * 所以镜头是连续的，而不是逐 tick 传送玩家时 20 次/秒的跳步。</p>
 *
 * <p>玩家“身体”仍会每隔若干 tick 跟到镜头附近，用途只是加载区块、让镜头实体
 * 留在玩家的实体追踪范围内；相机完全由实体决定，身体怎么动都不影响画面。</p>
 */
final class SpectatorRig implements CameraRig {

    static final String TAG = "cameratrack_camera";

    /** 身体跟随间隔：只为区块加载，间隔太大会让实体离开追踪范围。 */
    private static final int BODY_FOLLOW_TICKS = 10;

    /** 刚附身时持续重发相机包，覆盖“实体 spawn 包晚于相机包被客户端丢弃”的情况。 */
    private static final int REINFORCE_TICKS = 20;

    private final String worldName;
    private final PlaybackManager manager;

    private ArmorStand camera;
    private UUID cameraId;
    private double eyeOffset;
    private int bodyTicks;
    private int reinforceTicks;

    SpectatorRig(String worldName, PlaybackManager manager) {
        this.worldName = worldName;
        this.manager = manager;
    }

    @Override
    public void attach(Player player, Pose pose) {
        World world = Bukkit.getWorld(this.worldName);
        if (world == null) {
            throw new IllegalStateException("世界 " + this.worldName + " 未加载");
        }
        // 先把玩家身体送过去：区块加载好，实体才不会“生成即被卸载”
        player.teleport(new Location(world, pose.x(), pose.y(), pose.z()));
        ensureCamera(world, pose);
        if (this.camera == null || this.camera.isDead()) {
            // 让 PlaybackSession 感知失败并回退 TeleportRig，而不是静默留在无相机状态
            throw new IllegalStateException("镜头实体所在区块尚未加载");
        }
        player.setSpectatorTarget(this.camera);
        this.reinforceTicks = REINFORCE_TICKS;
    }

    @Override
    public void apply(Player player, Pose pose) {
        World world = Bukkit.getWorld(this.worldName);
        if (world == null) {
            return;
        }

        if (this.camera == null || this.camera.isDead()) {
            ensureCamera(world, pose);
            if (this.camera != null) {
                // 相机是“晚生成”的：客户端很可能还没收到实体 spawn 包，必须重发相机包
                this.reinforceTicks = REINFORCE_TICKS;
            }
        }

        if (this.camera == null || this.camera.isDead()) {
            // 区块仍未就绪：本体每 tick 跟着位姿走，至少不会是 10 tick 一跳
            player.teleport(positionFor(pose, world));
            return;
        }

        this.camera.teleport(positionFor(pose, world));

        Entity target = player.getSpectatorTarget();
        boolean wrongTarget = target == null || !this.cameraId.equals(target.getUniqueId());
        if (wrongTarget) {
            // 玩家按 shift / 客户端提前退出附身时立刻抢回来
            player.setSpectatorTarget(this.camera);
            target = player.getSpectatorTarget();
            wrongTarget = target == null || !this.cameraId.equals(target.getUniqueId());
            if (wrongTarget) {
                // 附身被其他插件拒绝/取消时的兜底：至少让玩家本人跟着镜头走
                player.teleport(positionFor(pose, world));
            }
        }

        if (this.reinforceTicks > 0) {
            // 每 2 tick 强制做一次“停止旁观 → 重新旁观”，让服务端重新发相机包；
            // 这是修复“客户端丢了唯一一次相机包后永不恢复”的关键兜底。
            if (!wrongTarget && this.reinforceTicks % 2 == 0) {
                reassertCamera(player);
            }
            this.reinforceTicks--;
        }

        if (++this.bodyTicks >= BODY_FOLLOW_TICKS) {
            this.bodyTicks = 0;
            // 身体跟随只负责区块加载与实体追踪，不参与相机定位
            player.teleport(positionFor(pose, world));
        }
    }

    /**
     * 强制重发相机实体：服务端 {@code ServerPlayer#setCamera} 只在相机实体变化时发包，
     * 所以先切回自己、再切回镜头实体。期间由 {@link PlaybackManager} 放行“停止旁观”事件。
     */
    private void reassertCamera(Player player) {
        if (this.camera == null || this.camera.isDead()) {
            return;
        }
        if (player.getGameMode() != GameMode.SPECTATOR) {
            return;
        }
        this.manager.beginCameraReset(player);
        try {
            player.setSpectatorTarget(null);
            player.setSpectatorTarget(this.camera);
        } catch (RuntimeException ignored) {
            // 极少数情况下切换被拒绝；下一轮 reinforce 还会再试
        } finally {
            this.manager.endCameraReset(player);
        }
    }

    @Override
    public void snap(Player player, Pose pose) {
        World world = Bukkit.getWorld(this.worldName);
        if (world == null) {
            return;
        }
        // 身体先到目标位置：保证目标区块已加载，新实体不会生成即被卸载
        player.teleport(new Location(world, pose.x(), pose.y(), pose.z()));
        ArmorStand previous = this.camera;
        ArmorStand fresh;
        try {
            fresh = spawnCamera(world, pose);
        } catch (RuntimeException exception) {
            // 保护插件拒绝生成时退回普通跟随（不硬切，但至少继续播放）
            apply(player, pose);
            return;
        }
        if (fresh == null) {
            // 目标区块/保护限制导致无法生成新实体时，退回普通跟随
            apply(player, pose);
            return;
        }
        // 先让玩家旁观新实体，再移除旧实体，避免中间出现一帧自己的视角
        player.setSpectatorTarget(fresh);
        Entity target = player.getSpectatorTarget();
        if (target == null || !fresh.getUniqueId().equals(target.getUniqueId())) {
            // 其他插件拒绝了附身切换：清理新实体，保持原来的跟随逻辑
            fresh.remove();
            apply(player, pose);
            return;
        }
        this.camera = fresh;
        this.cameraId = fresh.getUniqueId();
        this.eyeOffset = fresh.getEyeHeight();
        this.reinforceTicks = REINFORCE_TICKS;
        if (previous != null && previous != fresh && !previous.isDead()) {
            previous.remove();
        }
    }

    @Override
    public void detach(Player player) {
        Entity target = player.getSpectatorTarget();
        if (target != null && this.cameraId != null && this.cameraId.equals(target.getUniqueId())) {
            player.setSpectatorTarget(null);
        }
        removeCamera();
    }

    @Override
    public void abort() {
        removeCamera();
    }

    private void ensureCamera(World world, Pose pose) {
        if (this.camera != null && !this.camera.isDead()) {
            return;
        }
        ArmorStand stand = spawnCamera(world, pose);
        if (stand == null) {
            return;
        }
        this.camera = stand;
        this.cameraId = stand.getUniqueId();
        this.eyeOffset = stand.getEyeHeight();
    }

    /** 在世界中生成一个新的镜头实体；区块未加载或生成失败时返回 null。 */
    private ArmorStand spawnCamera(World world, Pose pose) {
        int chunkX = Location.locToBlock(pose.x()) >> 4;
        int chunkZ = Location.locToBlock(pose.z()) >> 4;
        if (!world.isChunkLoaded(chunkX, chunkZ)) {
            // 区块还没加载时先生成会被立刻卸载，等身体跟上来再生成
            return null;
        }
        Location spawn = new Location(world, pose.x(), pose.y(), pose.z(), pose.yaw(), pose.pitch());
        ArmorStand stand = world.spawn(spawn, ArmorStand.class, entity -> {
            entity.setMarker(true);
            entity.setInvisible(true);
            entity.setGravity(false);
            entity.setInvulnerable(true);
            entity.setSilent(true);
            entity.setCollidable(false);
            entity.setPersistent(false);
            entity.setArms(false);
            entity.setBasePlate(false);
            entity.setSmall(false);
            entity.setCustomNameVisible(false);
            entity.addScoreboardTag(TAG);
        });
        if (stand == null) {
            throw new IllegalStateException("服务器或保护插件阻止了镜头实体生成");
        }
        stand.teleport(positionFor(pose, world));
        return stand;
    }

    private Location positionFor(Pose pose, World world) {
        Location location = pose.toLocation(world);
        location.setY(location.getY() - this.eyeOffset);
        return location;
    }

    private void removeCamera() {
        if (this.camera != null && !this.camera.isDead()) {
            this.camera.remove();
        }
        this.camera = null;
        this.cameraId = null;
    }
}
