package dev.cameratrack.playback;

import dev.cameratrack.track.CameraNode;
import dev.cameratrack.track.NodeType;
import dev.cameratrack.track.PathMode;

import java.util.ArrayList;
import java.util.List;

/**
 * 把节点列表编译成可播放的时间轴并采样相机位姿。
 *
 * <p>节点类型会改变时间轴：</p>
 * <ul>
 *     <li><b>默认</b>：正常经过，按 {@link PathMode} 连接下一个可播放节点。</li>
 *     <li><b>切换</b>：停留结束后直接切到下一个节点，不占飞行时间，预览为灰色直线。</li>
 *     <li><b>贝塞尔</b>：不参与播放，只作为前后两个默认节点之间的二次贝塞尔控制点。</li>
 * </ul>
 *
 * <p>两种时间模型：flow（平滑巡航，默认，位置/朝向用时间 Hermite 样条）与
 * segmented（逐段缓动，每段应用 ease）。位置在同平面段（或 {@code path line}）
 * 上保持直线；两点轨道的非共面自动段会补一段单侧弧线。</p>
 */
public final class PathSampler {

    /** 弧顶偏移 = 段长 × 该系数；0 表示不弯曲。 */
    public static final double DEFAULT_ARC_STRENGTH = 0.15D;
    /** 默认让两点轨道（非共面）自动补一段弧线。 */
    public static final boolean DEFAULT_AUTO_ARC = true;

    /** 实际参与播放的节点（默认 + 切换，按原顺序）。 */
    private final List<CameraNode> nodes;
    /** bezierControls[i] = 第 i→i+1 段可选的贝塞尔控制点，null 表示普通连接。 */
    private final double[][] bezierControls;
    /** cutSegments[i] = 第 i 个可播放节点是否用“切换”接续下一节点。 */
    private final boolean[] cutSegments;

    /** starts[i] = 到达节点 i 并开始停留的绝对时间（秒）。 */
    private final double[] starts;
    private final double total;
    private final double axisTolerance;
    private final boolean flow;
    private final double arcStrength;
    private final boolean autoArc;

    // 仅在 flow 模式下使用
    private final double[] unwrappedYaw;
    private final double[] slopeX;
    private final double[] slopeY;
    private final double[] slopeZ;
    private final double[] slopeYaw;
    private final double[] slopePitch;

    public PathSampler(List<CameraNode> rawNodes, double axisTolerance, boolean flow,
                       double arcStrength, boolean autoArc) {
        this.axisTolerance = Math.max(0.0D, axisTolerance);
        this.flow = flow;
        this.arcStrength = Math.max(0.0D, Math.min(0.5D, arcStrength));
        this.autoArc = autoArc;

        List<CameraNode> stops = new ArrayList<>();
        List<double[]> controls = new ArrayList<>();
        boolean pendingBezier = false;
        double[] pendingControl = null;
        for (CameraNode node : rawNodes) {
            if (node.type() == NodeType.BEZIER) {
                pendingBezier = true;
                pendingControl = new double[]{node.x(), node.y(), node.z()};
                continue;
            }
            if (!stops.isEmpty()) {
                controls.add(pendingBezier ? pendingControl : null);
            }
            stops.add(node);
            pendingBezier = false;
            pendingControl = null;
        }
        this.nodes = List.copyOf(stops);
        this.bezierControls = controls.toArray(new double[0][]);

        int count = this.nodes.size();
        this.cutSegments = new boolean[count];
        for (int i = 0; i < count - 1; i++) {
            this.cutSegments[i] = this.nodes.get(i).type() == NodeType.CUT;
        }

        this.starts = new double[count];
        double cursor = 0.0D;
        for (int i = 0; i < count; i++) {
            this.starts[i] = cursor;
            cursor += this.nodes.get(i).hold();
            if (i < count - 1 && !this.cutSegments[i]) {
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

    public PathSampler(List<CameraNode> rawNodes, double axisTolerance, boolean flow) {
        this(rawNodes, axisTolerance, flow, DEFAULT_ARC_STRENGTH, DEFAULT_AUTO_ARC);
    }

    public PathSampler(List<CameraNode> rawNodes, double axisTolerance) {
        this(rawNodes, axisTolerance, false);
    }

    private void computeFlowSlopes() {
        int count = this.nodes.size();
        this.unwrappedYaw[0] = this.nodes.get(0).yaw();
        for (int i = 1; i < count; i++) {
            this.unwrappedYaw[i] = this.unwrappedYaw[i - 1]
                    + shortestDelta(this.nodes.get(i - 1).yaw(), this.nodes.get(i).yaw());
        }
        for (int i = 0; i < count; i++) {
            CameraNode node = this.nodes.get(i);
            boolean stop = i == 0 || i == count - 1 || node.hold() > 1.0E-9D
                    || node.type() == NodeType.CUT;
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

    public int segmentCount() {
        return Math.max(0, this.nodes.size() - 1);
    }

    public boolean isCutSegment(int index) {
        return index >= 0 && index < this.cutSegments.length && this.cutSegments[index];
    }

    public boolean isBezierSegment(int index) {
        return index >= 0 && index < this.bezierControls.length && this.bezierControls[index] != null;
    }

    /** 第 index 段飞行/切换开始的时间（即起点节点停留结束）。 */
    public double segmentStart(int index) {
        if (index < 0 || index >= segmentCount()) {
            return this.total;
        }
        return this.starts[index] + this.nodes.get(index).hold();
    }

    /** 第 index 段结束、到达下一节点的时间。 */
    public double segmentEnd(int index) {
        if (index < 0 || index >= segmentCount()) {
            return this.total;
        }
        return this.starts[index + 1];
    }

    /** (from, to] 区间内是否跨过至少一次“切换”。 */
    public boolean crossesCut(double from, double to) {
        if (to <= from) {
            return false;
        }
        for (int i = 0; i < segmentCount(); i++) {
            if (!this.cutSegments[i]) {
                continue;
            }
            double boundary = this.starts[i + 1];
            if (boundary > from + 1.0E-9D && boundary <= to + 1.0E-9D) {
                return true;
            }
        }
        return false;
    }

    public double axisTolerance() {
        return this.axisTolerance;
    }

    public boolean flow() {
        return this.flow;
    }

    public double arcStrength() {
        return this.arcStrength;
    }

    public boolean autoArc() {
        return this.autoArc;
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
        for (int i = count - 1; i >= 0; i--) {
            if (time >= this.starts[i]) {
                index = i;
                break;
            }
        }

        CameraNode node = this.nodes.get(index);
        double holdEnd = this.starts[index] + node.hold();
        if (time < holdEnd || index >= count - 1 || this.cutSegments[index]) {
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
        if (this.bezierControls[index] != null) {
            double[] bezier = bezierPosition(index, u);
            x = bezier[0];
            y = bezier[1];
            z = bezier[2];
        } else if (effectiveMode(index) == PathMode.ARC) {
            double[] arc = arcPosition(index, u);
            x = arc[0];
            y = arc[1];
            z = arc[2];
        } else if (isStraight(index)) {
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
        if (this.bezierControls[index] != null) {
            double[] bezier = bezierPosition(index, s);
            x = bezier[0];
            y = bezier[1];
            z = bezier[2];
        } else if (effectiveMode(index) == PathMode.ARC) {
            double[] arc = arcPosition(index, s);
            x = arc[0];
            y = arc[1];
            z = arc[2];
        } else if (isStraight(index)) {
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

    private double[] bezierPosition(int index, double u) {
        CameraNode from = this.nodes.get(index);
        CameraNode to = this.nodes.get(index + 1);
        double[] control = this.bezierControls[index];
        double v = 1.0D - u;
        return new double[]{
                v * v * from.x() + 2.0D * v * u * control[0] + u * u * to.x(),
                v * v * from.y() + 2.0D * v * u * control[1] + u * u * to.y(),
                v * v * from.z() + 2.0D * v * u * control[2] + u * u * to.z()
        };
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
     * 这一段实际生效的路径模式。
     *
     * <p>{@code auto} 只在“本来就会退化成直线”的两点轨道上自动补弧线。
     * 多点连接时位置由邻居切线样条负责，本身就是一条完整曲线；如果再逐段套
     * “两端各自沿视线”的弧线，每段会在起点鼓向一侧、终点鼓向另一侧，形成 S 形。</p>
     */
    private PathMode effectiveMode(int index) {
        PathMode mode = this.nodes.get(index).path();
        if (mode == PathMode.AUTO && this.autoArc && shouldAutoArc(index)) {
            return PathMode.ARC;
        }
        return mode;
    }

    /** 只有“两点轨道”的非共面段会退化成正线，才需要自动补弧线。 */
    private boolean shouldAutoArc(int index) {
        if (this.nodes.size() != 2 || this.bezierControls[index] != null || isSamePlane(index)) {
            return false;
        }
        CameraNode from = this.nodes.get(index);
        CameraNode to = this.nodes.get(index + 1);
        // 带停留的段要平滑停住，不插弧线
        return from.hold() <= 1.0E-9D && to.hold() <= 1.0E-9D;
    }

    /** 两节点是否在 X / Y / Z 任一轴上坐标相同（同一平面）。 */
    private boolean isSamePlane(int index) {
        CameraNode from = this.nodes.get(index);
        CameraNode to = this.nodes.get(index + 1);
        return same(from.x(), to.x()) || same(from.y(), to.y()) || same(from.z(), to.z());
    }

    /**
     * 单侧弧线：二次 Bézier。控制点位于连线中点、朝“视线反方向”偏移，
     * 整段只有一个鼓包；不会像两端独立的 Hermite 切线那样出现 S 形。
     *
     * <p>偏移量（弧顶）= 段长 × {@code arc-strength}；视线正好沿着连线时没有侧向，
     * 保持直线；任一端带 {@code hold} 时也保持平滑停靠，不插弧线。</p>
     *
     * @param u 0..1 的段内进度
     */
    private double[] arcPosition(int index, double u) {
        CameraNode from = this.nodes.get(index);
        CameraNode to = this.nodes.get(index + 1);
        double dx = to.x() - from.x();
        double dy = to.y() - from.y();
        double dz = to.z() - from.z();
        double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (distance < 1.0E-9D || from.hold() > 1.0E-9D || to.hold() > 1.0E-9D) {
            return smoothLine(from, to, u);
        }
        double cx = dx / distance;
        double cy = dy / distance;
        double cz = dz / distance;
        double[] view = averageView(from, to);
        double along = view[0] * cx + view[1] * cy + view[2] * cz;
        double px = view[0] - cx * along;
        double py = view[1] - cy * along;
        double pz = view[2] - cz * along;
        double perpendicular = Math.sqrt(px * px + py * py + pz * pz);
        if (perpendicular < 1.0E-6D) {
            return smoothLine(from, to, u);
        }
        double nx = -px / perpendicular;
        double ny = -py / perpendicular;
        double nz = -pz / perpendicular;
        double bulge = 4.0D * distance * this.arcStrength * u * (1.0D - u);
        return new double[]{
                from.x() + dx * u + nx * bulge,
                from.y() + dy * u + ny * bulge,
                from.z() + dz * u + nz * bulge
        };
    }

    /** 无弧线时的平滑直线（与 flow 的零切线 Hermite 一致）。 */
    private static double[] smoothLine(CameraNode from, CameraNode to, double u) {
        return new double[]{
                hermite(from.x(), to.x(), 0.0D, 0.0D, 1.0D, u),
                hermite(from.y(), to.y(), 0.0D, 0.0D, 1.0D, u),
                hermite(from.z(), to.z(), 0.0D, 0.0D, 1.0D, u)
        };
    }

    /** 两端镜头朝向的平均方向（相反时退回起点朝向）。 */
    private static double[] averageView(CameraNode from, CameraNode to) {
        double[] a = view(from);
        double[] b = view(to);
        double x = a[0] + b[0];
        double y = a[1] + b[1];
        double z = a[2] + b[2];
        double length = Math.sqrt(x * x + y * y + z * z);
        if (length < 1.0E-6D) {
            return a;
        }
        return new double[]{x / length, y / length, z / length};
    }

    /** 节点视线方向的单位向量（yaw / pitch）。 */
    private static double[] view(CameraNode node) {
        double yaw = Math.toRadians(node.yaw());
        double pitch = Math.toRadians(node.pitch());
        return new double[]{
                -Math.sin(yaw) * Math.cos(pitch),
                -Math.sin(pitch),
                Math.cos(yaw) * Math.cos(pitch)
        };
    }

    /**
     * 这一段是否走直线：显式 line / curve / arc，或者自动模式下三轴有一轴同上。
     */
    public boolean isStraight(int index) {
        if (index < 0 || index >= this.nodes.size() - 1) {
            return false;
        }
        return switch (effectiveMode(index)) {
            case LINE -> true;
            case CURVE, ARC -> false;
            case AUTO -> isSamePlane(index);
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
