package dev.cameratrack.playback;

import com.destroystokyo.paper.event.player.PlayerStopSpectatingEntityEvent;
import dev.cameratrack.lang.Lang;
import dev.cameratrack.track.CameraTrack;
import dev.cameratrack.track.TrackStore;
import dev.cameratrack.track.TrackValidator;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerGameModeChangeEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 播放会话的注册、调度与保护（防止播放中被伤害、被切换游戏模式、或退出附身打断）。
 */
public final class PlaybackManager implements Listener {

    /** 恢复快照超过这个时间就不再处理（避免很久以后误传）。 */
    private static final long PENDING_EXPIRY_MS = 7L * 24L * 60L * 60L * 1000L;

    private final JavaPlugin plugin;
    private final TrackStore store;
    private final double axisTolerance;
    private final double arcStrength;
    private final boolean autoArc;
    private final PendingReturns pendingReturns;
    private final Lang lang;
    private final Map<UUID, PlaybackSession> sessions = new HashMap<>();

    public PlaybackManager(JavaPlugin plugin, TrackStore store, double axisTolerance,
                           double arcStrength, boolean autoArc,
                           PendingReturns pendingReturns, Lang lang) {
        this.plugin = plugin;
        this.store = store;
        this.axisTolerance = Math.max(0.0D, axisTolerance);
        this.arcStrength = Math.max(0.0D, Math.min(2.0D, arcStrength));
        this.autoArc = autoArc;
        this.pendingReturns = pendingReturns;
        this.lang = lang;
    }

    public PendingReturns pendingReturns() {
        return this.pendingReturns;
    }

    public double axisTolerance() {
        return this.axisTolerance;
    }

    public double arcStrength() {
        return this.arcStrength;
    }

    public boolean autoArc() {
        return this.autoArc;
    }

    public boolean isPlaying(Player player) {
        return this.sessions.containsKey(player.getUniqueId());
    }

    public PlaybackMode modeOf(Player player) {
        PlaybackSession session = this.sessions.get(player.getUniqueId());
        return session == null ? null : session.mode();
    }

    public boolean start(Player player, CameraTrack track, double speed, boolean loop, boolean stay,
                         PlaybackMode mode, boolean flow) {
        if (track.size() < 2 || TrackValidator.validate(track) != null) {
            return false;
        }
        stop(player);
        PlaybackSession session = new PlaybackSession(
                this.plugin, this, player, track.snapshot(), speed, loop, stay, mode, flow);
        this.sessions.put(player.getUniqueId(), session);
        session.begin();
        return true;
    }

    public boolean stop(Player player) {
        PlaybackSession session = this.sessions.remove(player.getUniqueId());
        if (session == null) {
            return false;
        }
        // 手动停止时清掉可能还在显示的节点标题；正常播完则保留最后一个标题自然淡出
        if (player.isOnline()) {
            player.clearTitle();
        }
        session.end(true);
        return true;
    }

    public void stopAll() {
        for (PlaybackSession session : new ArrayList<>(this.sessions.values())) {
            session.end(true);
        }
        this.sessions.clear();
    }

    /** 会话结束后的回调用，避免 normal end / abort 之后残留引用。 */
    void finished(PlaybackSession session) {
        this.sessions.remove(session.playerId(), session);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        PlaybackSession session = this.sessions.remove(event.getPlayer().getUniqueId());
        if (session != null) {
            session.abort();
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (event.getEntity().getScoreboardTags().contains(SpectatorRig.TAG)) {
            event.setCancelled(true);
            return;
        }
        if (event.getEntity() instanceof Player player && isPlaying(player)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onGameModeChange(PlayerGameModeChangeEvent event) {
        if (event.getNewGameMode() == GameMode.SPECTATOR) {
            return;
        }
        Player player = event.getPlayer();
        if (!isPlaying(player)) {
            return;
        }
        event.setCancelled(true);
        this.lang.send(player, "game-mode-locked");
    }

    /** 防止玩家按 shift 退出对镜头实体的附身。 */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onSneak(PlayerToggleSneakEvent event) {
        if (event.isSneaking() && isPlaying(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    /** 双保险：即使客户端/其他插件试图取消附身，也拒绝。 */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onStopSpectating(PlayerStopSpectatingEntityEvent event) {
        if (isPlaying(event.getPlayer())
                && event.getSpectatorTarget() != null
                && event.getSpectatorTarget().getScoreboardTags().contains(SpectatorRig.TAG)) {
            event.setCancelled(true);
        }
    }

    /**
     * 玩家进入服务器时，如果存在未正常结束的播放快照，就把他送回播放起点并还原状态。
     * 延迟 1 tick，等玩家完全进入世界后再传送。
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        PendingReturn pending = this.pendingReturns.get(event.getPlayer().getUniqueId());
        if (pending == null) {
            return;
        }
        Bukkit.getScheduler().runTask(this.plugin, () -> applyPending(event.getPlayer(), pending));
    }

    private void applyPending(Player player, PendingReturn pending) {
        if (!player.isOnline()) {
            return;
        }
        if (System.currentTimeMillis() - pending.createdAt() > PENDING_EXPIRY_MS) {
            this.pendingReturns.remove(player.getUniqueId());
            return;
        }
        if (player.getSpectatorTarget() != null) {
            player.setSpectatorTarget(null);
        }

        World world = Bukkit.getWorld(pending.world());
        boolean moved = false;
        if (world != null) {
            moved = player.teleport(new Location(world,
                    pending.x(), pending.y(), pending.z(), pending.yaw(), pending.pitch()));
        }

        GameMode mode;
        try {
            mode = GameMode.valueOf(pending.gameMode());
        } catch (IllegalArgumentException exception) {
            mode = GameMode.SURVIVAL;
        }
        player.setGameMode(mode);
        player.setAllowFlight(pending.allowFlight());
        if (pending.allowFlight()) {
            player.setFlying(pending.flying());
        }
        player.setInvulnerable(pending.invulnerable());
        player.setFlySpeed(clampSpeed(pending.flySpeed()));
        player.setWalkSpeed(clampSpeed(pending.walkSpeed()));

        this.pendingReturns.remove(player.getUniqueId());
        this.lang.send(player, moved ? "join.restored" : "join.restored-no-world");
    }

    private static float clampSpeed(float value) {
        return Math.max(0.0F, Math.min(1.0F, value));
    }
}


