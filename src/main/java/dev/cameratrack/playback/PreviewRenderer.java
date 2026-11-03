package dev.cameratrack.playback;

import dev.cameratrack.track.CameraNode;
import dev.cameratrack.track.CameraTrack;
import org.bukkit.Particle;
import org.bukkit.World;

import java.util.List;

/**
 * 轨迹粒子预览：沿整条轨道每 0.25 秒采样一次，在采样点放 END_ROD，在节点处放 FLAME。
 * 指令 {@code /camera preview} 与手持工具的“左键查看”共用同一套逻辑。
 */
public final class PreviewRenderer {

    private static final double STEP_SECONDS = 0.25D;
    private static final int MAX_POINTS = 600;
    private static final int NODE_PARTICLES = 8;

    private PreviewRenderer() {
    }

    public static int render(World world, CameraTrack track, double axisTolerance, boolean flow) {
        if (world == null || track.size() < 2) {
            return 0;
        }
        PathSampler sampler = new PathSampler(track.nodes(), axisTolerance, flow);
        List<Pose> poses = sampler.sample(STEP_SECONDS);
        int limit = Math.min(poses.size(), MAX_POINTS);
        for (int i = 0; i < limit; i++) {
            Pose pose = poses.get(i);
            world.spawnParticle(Particle.END_ROD, pose.x(), pose.y(), pose.z(), 1, 0.0D, 0.0D, 0.0D, 0.0D);
        }
        for (CameraNode node : track.nodes()) {
            world.spawnParticle(Particle.FLAME, node.x(), node.y(), node.z(), NODE_PARTICLES,
                    0.05D, 0.05D, 0.05D, 0.0D);
        }
        return limit;
    }
}
