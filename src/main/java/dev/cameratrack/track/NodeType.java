package dev.cameratrack.track;

import java.util.Locale;

/**
 * 节点类型：决定这个节点如何接续到下一个节点。
 */
public enum NodeType {

    /** 默认：镜头按路径正常经过，连接方式由 {@link PathMode} 决定。 */
    DEFAULT("default"),

    /**
     * 切换：在这个镜头停留结束后，直接硬切到下一个节点并继续播放，
     * 不沿轨道飞行（类似切换摄像机机位）。
     */
    CUT("cut"),

    /**
     * 贝塞尔：不参与播放、不会被经过，只作为前后两个“默认”节点之间的控制点，
     * 把两端默认节点的连接变成一条二次贝塞尔曲线。
     */
    BEZIER("bezier");

    private final String id;

    NodeType(String id) {
        this.id = id;
    }

    public String id() {
        return this.id;
    }

    public static NodeType byId(String id, NodeType fallback) {
        if (id != null) {
            String lower = id.toLowerCase(Locale.ROOT);
            for (NodeType type : values()) {
                if (type.id.equals(lower)) {
                    return type;
                }
            }
        }
        return fallback;
    }

    public static String ids() {
        StringBuilder builder = new StringBuilder();
        for (NodeType type : values()) {
            if (builder.length() > 0) {
                builder.append(", ");
            }
            builder.append(type.id);
        }
        return builder.toString();
    }
}
