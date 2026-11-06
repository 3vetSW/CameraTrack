package dev.cameratrack.playback;

import dev.cameratrack.track.CameraNode;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.List;
import java.util.UUID;

/**
 * 一次播放会话：负责时间轴、节点命令、玩家状态保存/恢复，具体的镜头驱动交给 {@link CameraRig}。
 */
final class PlaybackSession {

    private final JavaPlugin plugin;
    private final PlaybackManager manager;
    private final UUID playerId;
    private final List<CameraNode> nodes;
    private final PathSampler sampler;
    private final double speed;
    private final boolean loop;
    /** true = 播完停在终点；false = 播完回到播放前的 origin。传给 end() 时会取反。 */
    private final boolean stay;
    private final String worldName;
    private final PlaybackMode mode;

    private CameraRig rig;
    private BukkitTask task;
    private int elapsedTicks;
    private int cuePointer;
    private boolean ended;

    private Location origin;
    private GameMode previousMode;
    private boolean previousAllowFlight;
    private boolean previousFlying;
    private boolean previousInvulnerable;
    private float previousFlySpeed;
    private float previousWalkSpeed;

    PlaybackSession(JavaPlugin plugin, PlaybackManager manager, Player player,
                    List<CameraNode> nodes, double speed, boolean loop, boolean stay,
                    PlaybackMode mode, boolean flow) {
        this.plugin = plugin;
        this.manager = manager;
        this.playerId = player.getUniqueId();
        this.nodes = nodes;
        this.sampler = new PathSampler(nodes, manager.axisTolerance(), flow,
                manager.arcStrength(), manager.autoArc());
        this.speed = speed;
        this.loop = loop;
        this.stay = stay;
        this.mode = mode;
        this.worldName = nodes.get(0).world();
    }

    UUID playerId() {
        return this.playerId;
    }

    PlaybackMode mode() {
        return this.mode;
    }

    void begin() {
        Player player = Bukkit.getPlayer(this.playerId);
        if (player == null || !player.isOnline()) {
            return;
        }

        this.origin = player.getLocation().clone();
        this.previousMode = player.getGameMode();
        this.previousAllowFlight = player.getAllowFlight();
        this.previousFlying = player.isFlying();
        this.previousInvulnerable = player.isMarkedInvulnerable();
        this.previousFlySpeed = player.getFlySpeed();
        this.previousWalkSpeed = player.getWalkSpeed();

        if (this.origin.getWorld() != null) {
            this.manager.pendingReturns().remember(this.playerId, new PendingReturn(
                    this.origin.getWorld().getName(),
                    this.origin.getX(), this.origin.getY(), this.origin.getZ(),
                    this.origin.getYaw(), this.origin.getPitch(),
                    this.previousMode.name(),
                    this.previousAllowFlight, this.previousFlying, this.previousInvulnerable,
                    this.previousFlySpeed, this.previousWalkSpeed,
                    "", System.currentTimeMillis()));
        }

        player.setGameMode(GameMode.SPECTATOR);
        player.setAllowFlight(true);
        player.setFlying(true);
        player.setInvulnerable(true);
        player.setFlySpeed(0.0F);
        player.setWalkSpeed(0.0F);

        Pose start = this.sampler.at(0.0D);
        this.rig = createRig();
        try {
            this.rig.attach(player, start);
        } catch (RuntimeException exception) {
            this.plugin.getLogger().warning("实体运镜初始化失败，回退到逐 tick 传送模式: " + exception.getMessage());
            try {
                this.rig.abort();
            } catch (RuntimeException ignored) {
                // 清理失败不影响回退
            }
            this.rig = new TeleportRig(this.worldName);
            this.rig.attach(player, start);
        }

        fireCues(player, 0.0D);
        this.task = Bukkit.getScheduler().runTaskTimer(this.plugin, this::tick, 1L, 1L);
    }

    private CameraRig createRig() {
        if (this.mode == PlaybackMode.PLAYER_TELEPORT) {
            return new TeleportRig(this.worldName);
        }
        return new SpectatorRig(this.worldName);
    }

    private void tick() {
        if (this.ended) {
            return;
        }
        Player player = Bukkit.getPlayer(this.playerId);
        if (player == null || !player.isOnline()) {
            abort();
            return;
        }
        World world = Bukkit.getWorld(this.worldName);
        if (world == null) {
            end(!this.stay);
            return;
        }

        double total = this.sampler.total();
        if (total <= 0.0D) {
            end(!this.stay);
            return;
        }

        this.elapsedTicks++;
        double time = this.elapsedTicks / 20.0D * this.speed;
        if (time >= total) {
            if (this.loop) {
                time = time % total;
                this.cuePointer = 0;
            } else {
                end(!this.stay);
                return;
            }
        }

        fireCues(player, time);
        this.rig.apply(player, this.sampler.at(time));
    }

    /**
     * 到达节点时先显示屏幕上标题，再执行节点上挂的命令（音效、粒子……），用于开场动画的演出编排。
     */
    private void fireCues(Player player, double time) {
        double[] starts = this.sampler.starts();
        while (this.cuePointer < this.nodes.size() && time >= starts[this.cuePointer]) {
            CameraNode node = this.nodes.get(this.cuePointer);
            int index = this.cuePointer + 1;
            this.cuePointer++;
            showTitle(player, node, index);
            String command = node.command();
            if (command == null || command.isBlank()) {
                continue;
            }
            try {
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command);
            } catch (RuntimeException exception) {
                this.plugin.getLogger().warning("节点命令执行失败: " + command + " (" + exception.getMessage() + ")");
            }
        }
    }

    /**
     * 显示节点标题：两行文本分别可以是 MiniMessage 或原版 JSON 文本组件，
     * 淡入 / 停留 / 淡出由节点单独配置（秒）。
     */
    private void showTitle(Player player, CameraNode node, int index) {
        TitleRenderer.show(player, node.title(), index, this.plugin.getLogger()::warning);
    }

    /** 正常结束：先卸载镜头，再恢复玩家状态，可选是否回到播放前的位置。 */
    void end(boolean restorePosition) {
        if (this.ended) {
            return;
        }
        this.ended = true;
        if (this.task != null) {
            this.task.cancel();
            this.task = null;
        }
        this.manager.finished(this);
        this.manager.pendingReturns().remove(this.playerId);

        Player player = Bukkit.getPlayer(this.playerId);
        if (this.rig != null) {
            if (player != null && player.isOnline()) {
                this.rig.detach(player);
            } else {
                this.rig.abort();
            }
        }
        if (player == null || !player.isOnline()) {
            return;
        }
        if (restorePosition && this.origin != null) {
            player.teleport(this.origin);
        }
        restoreState(player);
    }

    /** 下线/中断：清理镜头、立刻还原玩家状态；位置快照保留，等下次进服再传送回起点。 */
    void abort() {
        if (this.ended) {
            return;
        }
        this.ended = true;
        if (this.task != null) {
            this.task.cancel();
            this.task = null;
        }
        this.manager.finished(this);

        Player player = Bukkit.getPlayer(this.playerId);
        if (player != null && player.isOnline()) {
            // 退出瞬间先把游戏模式/速度还原，避免这些值被写进玩家数据；
            // 位置恢复交给 pending-returns.yml + 下次进服处理。
            try {
                this.rig.detach(player);
            } catch (RuntimeException ignored) {
                // 玩家正在退出，附身清理失败不影响状态还原
            }
            restoreState(player);
        } else if (this.rig != null) {
            this.rig.abort();
        }
    }

    private void restoreState(Player player) {
        player.setGameMode(this.previousMode);
        player.setAllowFlight(this.previousAllowFlight);
        player.setFlying(this.previousAllowFlight && this.previousFlying);
        player.setInvulnerable(this.previousInvulnerable);
        player.setFlySpeed(clampSpeed(this.previousFlySpeed));
        player.setWalkSpeed(clampSpeed(this.previousWalkSpeed));
    }

    private static float clampSpeed(float value) {
        return Math.max(0.0F, Math.min(1.0F, value));
    }
}

