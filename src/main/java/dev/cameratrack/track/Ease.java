package dev.cameratrack.track;

/**
 * 节点之间的速度曲线。{@code apply(t)} 把线性的 0..1 进度映射成缓动后的进度。
 */
public enum Ease {

    LINEAR("linear") {
        @Override
        public double apply(double t) {
            return t;
        }
    },
    /** 两端慢、中间快，最接近真实摄像机的起停手感。 */
    SMOOTH("smooth") {
        @Override
        public double apply(double t) {
            return t * t * (3.0 - 2.0 * t);
        }
    },
    EASE_IN("in") {
        @Override
        public double apply(double t) {
            return t * t * t;
        }
    },
    EASE_OUT("out") {
        @Override
        public double apply(double t) {
            double inv = 1.0 - t;
            return 1.0 - inv * inv * inv;
        }
    },
    EASE_IN_OUT("inout") {
        @Override
        public double apply(double t) {
            return t < 0.5 ? 4.0 * t * t * t : 1.0 - Math.pow(-2.0 * t + 2.0, 3.0) / 2.0;
        }
    };

    private final String id;

    Ease(String id) {
        this.id = id;
    }

    public String id() {
        return this.id;
    }

    public abstract double apply(double t);

    public static Ease byId(String id, Ease fallback) {
        if (id != null) {
            for (Ease ease : values()) {
                if (ease.id.equalsIgnoreCase(id) || ease.name().equalsIgnoreCase(id)) {
                    return ease;
                }
            }
        }
        return fallback;
    }

    public static String ids() {
        StringBuilder builder = new StringBuilder();
        for (Ease ease : values()) {
            if (builder.length() > 0) {
                builder.append(", ");
            }
            builder.append(ease.id);
        }
        return builder.toString();
    }
}
