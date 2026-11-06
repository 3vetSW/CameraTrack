package dev.cameratrack.track;

import java.util.Locale;

/**
 * 一段镜头路径的插值方式（挂在起点节点上，作用于“本节点 → 下一节点”这一段）。
 */
public enum PathMode {

    /**
     * 自动：当两个节点在 X / Y / Z 任意一个轴上坐标相等（允许配置的容差）时，
     * 说明这段镜头位于同一平面，直接用直线连接；否则使用 Catmull-Rom 样条。
     */
    AUTO("auto"),

    /** 强制直线：位置在两个节点之间线性插值，只有朝向在变化。 */
    LINE("line"),

    /** 强制样条：即使同平面也使用 Catmull-Rom 曲线（等高环绕镜头用这个）。 */
    CURVE("curve"),

    /**
     * 沿视线的弧线：用起点和终点的镜头朝向（yaw / pitch）作为曲线切线，
     * 让镜头“朝着自己看的方向飞出去”，在两点之间形成一段弧线。
     *
     * <p>适合斜向运镜、只有两个节点的镜头，或者想避免直线斜切的情况。
     * 若某个节点的视线与这一段的连线方向相反，切线中“向后”的分量会被去掉，
     * 避免镜头先冲过头再倒回来。</p>
     */
    ARC("arc");

    private final String id;

    PathMode(String id) {
        this.id = id;
    }

    public String id() {
        return this.id;
    }

    public static PathMode byId(String id, PathMode fallback) {
        if (id != null) {
            String lower = id.toLowerCase(Locale.ROOT);
            for (PathMode mode : values()) {
                if (mode.id.equals(lower)) {
                    return mode;
                }
            }
        }
        return fallback;
    }

    public static String ids() {
        StringBuilder builder = new StringBuilder();
        for (PathMode mode : values()) {
            if (builder.length() > 0) {
                builder.append(", ");
            }
            builder.append(mode.id);
        }
        return builder.toString();
    }
}
