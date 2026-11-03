package dev.cameratrack.playback;

import dev.cameratrack.track.CameraNode;

import java.util.ArrayList;
import java.util.List;

/**
 * 把节点列表采样成连续的相机轨迹。
 *
 * <p>两种时间模型：</p>
 * <ul>
 *     <li><b>flow（平滑巡航，默认）</b>：位置和朝向都用“以时间为参数的三次 Hermite 样条”连接，
 *     节点只是曲线上的通过点，速度在节点处连续；只在起点、终点和带停留（hold）的节点处
 *     自然减速到 0。这样曲线轨道不会在每个节点“停一下再走”，转动也不会一顿一顿。</li>
 *     <li><b>segmented（逐段缓动）</b>：每一段单独应用 {@code ease}，朝向用 smoothstep，
 *     段与段之间会有速度归零的“节点停顿”感；需要精确控制每一段节奏时使用。</li>
 * </ul>
 *
 * <p>位置在同平面段（或 {@code path line}）上仍然保持直线；朝向始终按最短弧展开后做样条，
 * 避免 ±180° 处的绕圈。</p>
 */
public final class PathSampler {

    private final List<CameraNode> nodes;
    /** starts[i] = 到达节点 i 并开始停留的绝对时间（秒）。 */
    private final double[] starts;
    private final double total;
    private final double axisTolerance;
    private final boolean flow;

    // 仅在 flow 模式下使用
    private final double[] unwrappedYaw;
    private final double[] slopeX;
    private final double[] slopeY;
    private final double[] slopeZ;
    private final double[] slopeYaw;
    private final double[] slopePitch;

    public PathSampler(List<CameraNode> nodes, double axisTolerance, boolean flow) {
        this.nodes = List.copyOf(nodes);
        this.axisTolerance = Math.max(0.0D, axisTolerance);
        this.flow = flow;
        int count = this.nodes.size();
        this.starts = new double[count];
        double cursor = 0.0D;
        for (int i = 0; i < count; i++) {
            this.starts[i] = cursor;
            cursor += this.nodes.get(i).hold();
            if (i < count - 1) {
                cursor += Math.max(0.0D, this.nodes.get(i).duration());
            }
        }
        this.total = cursor;

        if (flow && count > 1) {
            this.unwrappedYaw = new double[count];
            this.slopeX = new double[count];
            this.slopeY = new double[count];
            this.slopeZ = new double[count];
            this.slopeYaw = new double[count];
            this.slopePitch = new double[count];
            computeFlowSlopes();
        } else {
            this.unwrappedYaw = null;
            this.slopeX = null;
            this.slopeY = null;
            this.slopeZ = null;
            this.slopeYaw = null;
            this.slopePitch = null;
        }
    }

    public PathSampler(List<CameraNode> nodes, double axisTolerance) {
        this(nodes, axisTolerance, false);
    }

    private void computeFlowSlopes() {
        int count = this.nodes.size();
        this.unwrappedYaw[0] = this.nodes.get(0).yaw();
        for (int i = 1; i < count; i++) {
            double previous = this.nodes.get(i - 1).yaw();
            double current = this.nodes.get(i).yaw();
            this.unwrappedYaw[i] = this.unwrappedYaw[i - 1] + shortestDelta(previous, current);
        }
        for (int i = 0; i < count; i++) {
            CameraNode node = this.nodes.get(i);
            boolean stop = i == 0 || i == count - 1 || node.hold() > 1.0E-9D;
            if (stop) {
                continue;
            }
            double span = this.starts[i + 1] - this.starts[i - 1];
            if (span <= 1.0E-9D) {
                continue;
            }
            CameraNode before = this.nodes.get(i - 1);
            CameraNode after = this.nodes.get(i + 1);
            this.slopeX[i] = (after.x() - before.x()) / span;
            this.slopeY[i] = (after.y() - before.y()) / span;
            this.slopeZ[i] = (after.z() - before.z()) / span;
            this.slopeYaw[i] = (this.unwrappedYaw[i + 1] - this.unwrappedYaw[i - 1]) / span;
            this.slopePitch[i] = (after.pitch() - before.pitch()) / span;
        }
    }

    public List<CameraNode> nodes() {
        return this.nodes;
    }

    public double total() {
        return this.total;
    }

    public double[] starts() {
        return this.starts;
    }

    public double axisTolerance() {
        return this.axisTolerance;
    }

    public boolean flow() {
        return this.flow;
    }

    /**
     * 采样 {@code seconds} 时刻的相机位姿。
     */
    public Pose at(double seconds) {
        int count = this.nodes.size();
        if (count == 0) {
            return new Pose(0.0D, 0.0D, 0.0D, 0.0F, 0.0F);
        }
        if (count == 1) {
            return poseOf(this.nodes.get(0));
        }
        double time = Math.max(0.0D, Math.min(seconds, this.total));

        int index = 0;
        for (int i = count - 2; i >= 0; i--) {
            if (time >= this.starts[i]) {
                index = i;
                break;
            }
        }

        CameraNode node = this.nodes.get(index);
        double holdEnd = this.starts[index] + node.hold();
        if (time < holdEnd) {
            return poseOf(node);
        }
        double duration = Math.max(1.0E-6D, node.duration());
        double progress = Math.min(1.0D, (time - holdEnd) / duration);
        if (this.flow) {
            return flowPose(index, progress, duration);
        }
        double eased = node.ease().apply(progress);
        return segmentedPose(index, eased);
    }

    // ------------------------------------------------------------------ flow：时间 Hermite

    private Pose flowPose(int index, double u, double h) {
        CameraNode p1 = this.nodes.get(index);
        CameraNode p2 = this.nodes.get(index + 1);

        double x;
        double y;
        double z;
        if (isStraight(index)) {
            x = p1.x() + (p2.x() - p1.x()) * u;
            y = p1.y() + (p2.y() - p1.y()) * u;
            z = p1.z() + (p2.z() - p1.z()) * u;
        } else {
            x = hermite(p1.x(), p2.x(), this.slopeX[index], this.slopeX[index + 1], h, u);
            y = hermite(p1.y(), p2.y(), this.slopeY[index], this.slopeY[index + 1], h, u);
            z = hermite(p1.z(), p2.z(), this.slopeZ[index], this.slopeZ[index + 1], h, u);
        }

        double yaw = hermite(this.unwrappedYaw[index], this.unwrappedYaw[index + 1],
                this.slopeYaw[index], this.slopeYaw[index + 1], h, u);
        double pitch = hermite(p1.pitch(), p2.pitch(),
                this.slopePitch[index], this.slopePitch[index + 1], h, u);
        return new Pose(x, y, z, (float) yaw, (float) Math.max(-90.0D, Math.min(90.0D, pitch)));
    }

    private static double hermite(double p0, double p1, double m0, double m1, double h, double u) {
        double u2 = u * u;
        double u3 = u2 * u;
        double h00 = 2.0D * u3 - 3.0D * u2 + 1.0D;
        double h10 = u3 - 2.0D * u2 + u;
        double h01 = -2.0D * u3 + 3.0D * u2;
        double h11 = u3 - u2;
        return h00 * p0 + h10 * h * m0 + h01 * p1 + h11 * h * m1;
    }

    // ------------------------------------------------------------------ segmented：逐段缓动

    private Pose segmentedPose(int index, double s) {
        CameraNode p1 = this.nodes.get(index);
        CameraNode p2 = this.nodes.get(index + 1);

        double x;
        double y;
        double z;
        if (isStraight(index)) {
            x = p1.x() + (p2.x() - p1.x()) * s;
            y = p1.y() + (p2.y() - p1.y()) * s;
            z = p1.z() + (p2.z() - p1.z()) * s;
        } else {
            CameraNode p0 = this.nodes.get(Math.max(0, index - 1));
            CameraNode p3 = this.nodes.get(Math.min(this.nodes.size() - 1, index + 2));
            x = catmullRom(p0.x(), p1.x(), p2.x(), p3.x(), s);
            y = catmullRom(p0.y(), p1.y(), p2.y(), p3.y(), s);
            z = catmullRom(p0.z(), p1.z(), p2.z(), p3.z(), s);
        }

        double rotation = smoothStep(s);
        float yaw = lerpAngle(p1.yaw(), p2.yaw(), rotation);
        float pitch = (float) (p1.pitch() + (p2.pitch() - p1.pitch()) * rotation);
        return new Pose(x, y, z, yaw, Math.max(-90.0F, Math.min(90.0F, pitch)));
    }

    private static double catmullRom(double p0, double p1, double p2, double p3, double t) {
        double t2 = t * t;
        double t3 = t2 * t;
        return 0.5D * (
                2.0D * p1
                        + (-p0 + p2) * t
                        + (2.0D * p0 - 5.0D * p1 + 4.0D * p2 - p3) * t2
                        + (-p0 + 3.0D * p1 - 3.0D * p2 + p3) * t3
        );
    }

    private static double smoothStep(double t) {
        return t * t * (3.0D - 2.0D * t);
    }

    /** 按最短弧插值角度，避免从 179° 转到 -179° 时绕一整圈。 */
    public static float lerpAngle(float from, float to, double t) {
        return (float) (from + shortestDelta(from, to) * t);
    }

    private static double shortestDelta(double from, double to) {
        return ((to - from) % 360.0D + 540.0D) % 360.0D - 180.0D;
    }

    private static Pose poseOf(CameraNode node) {
        return new Pose(node.x(), node.y(), node.z(), node.yaw(), node.pitch());
    }

    /**
     * 这一段是否走直线：显式 line / curve，或者自动模式下三轴有一轴同上。
     */
    public boolean isStraight(int index) {
        if (index < 0 || index >= this.nodes.size() - 1) {
            return false;
        }
        CameraNode from = this.nodes.get(index);
        return switch (from.path()) {
            case LINE -> true;
            case CURVE -> false;
            case AUTO -> {
                CameraNode to = this.nodes.get(index + 1);
                yield same(from.x(), to.x()) || same(from.y(), to.y()) || same(from.z(), to.z());
            }
        };
    }

    /** 同一平面判定；配置容差为 0 时仍保留一个极小阈值，避免浮点误差。 */
    private boolean same(double a, double b) {
        double tolerance = Math.max(this.axisTolerance, 1.0E-6D);
        return Math.abs(a - b) <= tolerance;
    }

    /** 供预览等场景使用：按固定步长采样整条轨道。 */
    public List<Pose> sample(double stepSeconds) {
        List<Pose> result = new ArrayList<>();
        if (this.total <= 0.0D || stepSeconds <= 0.0D) {
            if (!this.nodes.isEmpty()) {
                result.add(poseOf(this.nodes.get(0)));
            }
            return result;
        }
        for (double t = 0.0D; t <= this.total; t += stepSeconds) {
            result.add(at(t));
        }
        return result;
    }
}
