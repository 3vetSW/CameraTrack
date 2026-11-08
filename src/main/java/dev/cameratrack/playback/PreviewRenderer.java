package dev.cameratrack.playback;

import dev.cameratrack.CameraTrackPlugin;
import dev.cameratrack.track.CameraNode;
import dev.cameratrack.track.CameraTrack;
import dev.cameratrack.track.NodeType;
import org.bukkit.Bukkit;
import org.bukkit.Color;
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
 * 轨迹粒子预览。
 *
 * <ul>
 *     <li>普通飞行段：亮绿色带颜色粒子，按固定距离采样。</li>
 *     <li>“切换”段：灰色粒子从切换节点直线连到下一个节点（表示硬切，不是飞过去）。</li>
 *     <li>节点标记：默认红色、切换黄色、贝塞尔蓝色。</li>
 * </ul>
 *
 * <p>粒子在客户端只能存活约 1 秒，所以预览在 {@code preview.duration-ticks} 期间
 * 每 {@code preview.refresh-ticks} 重新发射一轮。指令 {@code /camera preview}
 * 与手持工具左键共用；同一玩家再次预览会先取消上一次。</p>
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
     * @return 轨迹采样点数量（不含节点标记）
     */
    public int render(Player player, World world, CameraTrack track) {
        if (world == null || track == null) {
            return 0;
        }
        List<CameraNode> rawNodes = List.copyOf(track.nodes());
        PathSampler sampler = new PathSampler(rawNodes, this.plugin.axisTolerance(), track.flow(),
                this.plugin.arcStrength(), this.plugin.autoArc());

        List<Point> points = new ArrayList<>();
        for (int i = 0; i < sampler.segmentCount() && points.size() < this.settings.maxPoints(); i++) {
            if (sampler.isCutSegment(i)) {
                addStraightLine(points, sampler.nodes().get(i), sampler.nodes().get(i + 1),
                        this.settings.cutTrailColor(), this.settings.spacing(), this.settings.maxPoints());
            } else {
                addTravel(points, sampler, i, this.settings.trailColor(),
                        this.settings.spacing(), this.settings.maxPoints());
            }
        }

        if (player != null) {
            cancel(player.getUniqueId());
        }
        spawn(world, points, rawNodes);
        startRefresh(player, world.getName(), points, rawNodes);
        return points.size();
    }

    /** 停止所有仍在重发的预览（插件卸载时调用）。 */
    public void shutdown() {
        for (BukkitTask task : this.running.values()) {
            task.cancel();
        }
        this.running.clear();
    }

    private void startRefresh(Player player, String worldName, List<Point> points, List<CameraNode> nodes) {
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
                spawn(current, points, nodes);
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

    private void spawn(World world, List<Point> points, List<CameraNode> nodes) {
        Map<Color, Particle.DustOptions> trailOptions = new HashMap<>();
        for (Point point : points) {
            world.spawnParticle(Particle.DUST, point.x(), point.y(), point.z(),
                    1, 0.0D, 0.0D, 0.0D, 0.0D,
                    dust(trailOptions, point.color(), this.settings.trailSize()));
        }

        Map<Color, Particle.DustOptions> nodeOptions = new HashMap<>();
        double spread = this.settings.nodeSpread();
        for (CameraNode node : nodes) {
            world.spawnParticle(Particle.DUST, node.x(), node.y(), node.z(),
                    this.settings.nodeCount(), spread, spread, spread, 0.0D,
                    dust(nodeOptions, markerColor(node.type()), this.settings.nodeSize()));
        }
    }

    private Color markerColor(NodeType type) {
        return switch (type) {
            case CUT -> this.settings.cutNodeColor();
            case BEZIER -> this.settings.bezierNodeColor();
            case DEFAULT -> this.settings.nodeColor();
        };
    }

    private static Particle.DustOptions dust(Map<Color, Particle.DustOptions> cache, Color color, float size) {
        return cache.computeIfAbsent(color, key -> new Particle.DustOptions(key, size));
    }

    /** 沿一个飞行段按距离采样，加到预览点列表。 */
    private static void addTravel(List<Point> points, PathSampler sampler, int segment,
                                  Color color, double spacing, int maxPoints) {
        double start = sampler.segmentStart(segment);
        double end = sampler.segmentEnd(segment);
        double duration = end - start;
        if (duration <= 1.0E-9D) {
            return;
        }
        // 用少量采样估算这一段的曲线长度（贝塞尔/弧线比端点直线长），
        // 否则按端点距离算速度会让弯曲段的粒子间距被放大
        double length = 0.0D;
        Pose previous = sampler.at(start);
        for (int k = 1; k <= 8; k++) {
            Pose sample = sampler.at(start + duration * k / 8.0D);
            length += distance(previous, sample);
            previous = sample;
        }
        double speed = length / duration;
        double step = Math.max(0.005D, Math.min(0.05D, spacing / (Math.max(0.5D, speed) * 2.0D)));
        Pose last = null;
        for (double time = start; time <= end + 1.0E-9D && points.size() < maxPoints; time += step) {
            Pose pose = sampler.at(Math.min(time, end));
            if (last == null || distance(last, pose) >= spacing) {
                points.add(new Point(pose.x(), pose.y(), pose.z(), color));
                last = pose;
            }
        }
        Pose finish = sampler.at(end);
        if ((last == null || distance(last, finish) > 1.0E-4D) && points.size() < maxPoints) {
            points.add(new Point(finish.x(), finish.y(), finish.z(), color));
        }
    }

    /** “切换”段：用灰色粒子画一条直线，表示瞬间跳转。 */
    private static void addStraightLine(List<Point> points, CameraNode from, CameraNode to,
                                        Color color, double spacing, int maxPoints) {
        double dx = to.x() - from.x();
        double dy = to.y() - from.y();
        double dz = to.z() - from.z();
        double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (distance < 1.0E-6D) {
            return;
        }
        int steps = (int) Math.ceil(distance / Math.max(0.01D, spacing));
        for (int i = 0; i <= steps && points.size() < maxPoints; i++) {
            double t = (double) i / Math.max(1, steps);
            points.add(new Point(from.x() + dx * t, from.y() + dy * t, from.z() + dz * t, color));
        }
    }

    /**
     * 按固定距离对整条轨道采样（测试/兼容用）。预览本身按段绘制；
     * 这个方法的“切换”段会表现为一次跳变。
     */
    public static List<Pose> sampleTrail(PathSampler sampler, double spacing, int maxPoints) {
        double total = sampler.total();
        if (total <= 0.0D) {
            return List.of(sampler.at(0.0D));
        }
        double step = Math.max(0.005D, Math.min(0.05D, spacing / (fastestSpeed(sampler) * 2.0D)));
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

    /** 轨道上最快一段的速度（格/秒），用于决定细采样步长。 */
    private static double fastestSpeed(PathSampler sampler) {
        List<CameraNode> nodes = sampler.nodes();
        double fastest = 0.5D;
        for (int i = 0; i < nodes.size() - 1; i++) {
            if (sampler.isCutSegment(i)) {
                continue;
            }
            CameraNode from = nodes.get(i);
            CameraNode to = nodes.get(i + 1);
            double duration = Math.max(0.0D, from.duration());
            if (duration < 1.0E-6D) {
                continue;
            }
            double dx = to.x() - from.x();
            double dy = to.y() - from.y();
            double dz = to.z() - from.z();
            fastest = Math.max(fastest, Math.sqrt(dx * dx + dy * dy + dz * dz) / duration);
        }
        return fastest;
    }

    private static double distance(Pose first, Pose second) {
        double dx = first.x() - second.x();
        double dy = first.y() - second.y();
        double dz = first.z() - second.z();
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    private record Point(double x, double y, double z, Color color) {
    }
}
