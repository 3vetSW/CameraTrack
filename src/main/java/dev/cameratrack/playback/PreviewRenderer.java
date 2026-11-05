package dev.cameratrack.playback;

import dev.cameratrack.CameraTrackPlugin;
import dev.cameratrack.track.CameraNode;
import dev.cameratrack.track.CameraTrack;
import org.bukkit.Bukkit;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 轨迹粒子预览：沿整条轨道按固定<b>距离</b>采样（间距可配置），用亮绿色带颜色粒子画线，
 * 节点位置用更大的红色粒子标记。
 *
 * <p>粒子在客户端只能存活很短时间（约 1 秒），所以预览并不是“放一次就结束”：
 * 在 {@code preview.duration-ticks} 期间每 {@code preview.refresh-ticks} 重新发射一轮，
 * 让整条轨迹持续可见。指令 {@code /camera preview} 与手持工具左键共用这一套逻辑；
 * 同一玩家再次预览会先取消上一次，避免粒子叠加。</p>
 */
public final class PreviewRenderer {

    private final CameraTrackPlugin plugin;
    private final PreviewSettings settings;
    private final Map<UUID, BukkitTask> running = new HashMap<>();

    public PreviewRenderer(CameraTrackPlugin plugin, PreviewSettings settings) {
        this.plugin = plugin;
        this.settings = settings;
    }

    /**
     * 绘制一次预览，并在配置的时长内重复发射以维持可见。
     *
     * @param player 触发预览的玩家（用于取消同一玩家的上一次预览），控制台可为 {@code null}
     * @return 轨迹采样点数量
     */
    public int render(Player player, World world, CameraTrack track) {
        if (world == null || track == null || track.size() < 2) {
            return 0;
        }
        PathSampler sampler = new PathSampler(track.nodes(), this.plugin.axisTolerance(), track.flow());
        if (sampler.total() <= 0.0D) {
            return 0;
        }
        List<Pose> trail = sampleTrail(sampler, this.settings.spacing(), this.settings.maxPoints());
        List<CameraNode> nodes = List.copyOf(track.nodes());

        if (player != null) {
            cancel(player.getUniqueId());
        }
        spawn(world, trail, nodes);
        startRefresh(player, world.getName(), trail, nodes);
        return trail.size();
    }

    /** 停止所有仍在重发的预览（插件卸载时调用）。 */
    public void shutdown() {
        for (BukkitTask task : this.running.values()) {
            task.cancel();
        }
        this.running.clear();
    }

    private void startRefresh(Player player, String worldName, List<Pose> trail, List<CameraNode> nodes) {
        int duration = this.settings.durationTicks();
        int refresh = this.settings.refreshTicks();
        if (duration <= refresh) {
            return;
        }
        UUID owner = player == null ? null : player.getUniqueId();
        BukkitTask[] holder = new BukkitTask[1];
        holder[0] = Bukkit.getScheduler().runTaskTimer(this.plugin, new Runnable() {
            private int elapsed = refresh;

            @Override
            public void run() {
                World current = Bukkit.getWorld(worldName);
                if (current == null || this.elapsed >= duration) {
                    if (owner != null) {
                        running.remove(owner, holder[0]);
                    }
                    holder[0].cancel();
                    return;
                }
                spawn(current, trail, nodes);
                this.elapsed += refresh;
            }
        }, refresh, refresh);
        if (owner != null) {
            this.running.put(owner, holder[0]);
        }
    }

    private void cancel(UUID owner) {
        BukkitTask task = this.running.remove(owner);
        if (task != null) {
            task.cancel();
        }
    }

    private void spawn(World world, List<Pose> trail, List<CameraNode> nodes) {
        Particle.DustOptions trailOptions =
                new Particle.DustOptions(this.settings.trailColor(), this.settings.trailSize());
        for (Pose pose : trail) {
            world.spawnParticle(Particle.DUST, pose.x(), pose.y(), pose.z(),
                    1, 0.0D, 0.0D, 0.0D, 0.0D, trailOptions);
        }

        Particle.DustOptions nodeOptions =
                new Particle.DustOptions(this.settings.nodeColor(), this.settings.nodeSize());
        double spread = this.settings.nodeSpread();
        for (CameraNode node : nodes) {
            world.spawnParticle(Particle.DUST, node.x(), node.y(), node.z(),
                    this.settings.nodeCount(), spread, spread, spread, 0.0D, nodeOptions);
        }
    }

    /**
     * 按固定距离对轨迹采样：沿时间轴细采样，再按与上一个输出点的距离抽稀，
     * 因此轨迹上的粒子间距与镜头速度无关。
     *
     * @param spacing   相邻粒子间距（格）
     * @param maxPoints 最多输出多少个点
     */
    public static List<Pose> sampleTrail(PathSampler sampler, double spacing, int maxPoints) {
        double total = sampler.total();
        if (total <= 0.0D) {
            return List.of(sampler.at(0.0D));
        }
        double step = Math.max(0.005D, Math.min(0.05D, spacing / 4.0D));
        int guard = Math.max(1000, maxPoints * 40);
        List<Pose> points = new ArrayList<>();
        Pose last = null;
        double time = 0.0D;
        for (int i = 0; time <= total && points.size() < maxPoints && i < guard; i++, time += step) {
            Pose pose = sampler.at(time);
            if (last == null || distance(last, pose) >= spacing) {
                points.add(pose);
                last = pose;
            }
        }
        Pose end = sampler.at(total);
        if (last == null || distance(last, end) > 1.0E-4D) {
            if (points.size() < maxPoints) {
                points.add(end);
            }
        }
        return points;
    }

    private static double distance(Pose first, Pose second) {
        double dx = first.x() - second.x();
        double dy = first.y() - second.y();
        double dz = first.z() - second.z();
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }
}
