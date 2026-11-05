package dev.cameratrack.track;

/**
 * 节点到达时显示在屏幕上的标题（title / subtitle）与时间控制。
 *
 * <p>{@code text} 与 {@code subtitle} 既可以是 <b>MiniMessage</b>（如
 * {@code "<gold><lang:multiplayer.player.joined:'<player>'>"}），也可以是原版
 * <b>JSON 文本组件</b>（以 <code>{</code> 或 <code>[</code> 开头，如
 * <code>{"translate":"multiplayer.player.joined","with":[{"text":"Steve"}],"color":"gold"}</code>）。
 * 空字符串表示该行不显示；两行都为空时不显示标题。</p>
 *
 * <p>三个时间单位都是秒：{@code fadeIn} 淡入、{@code stay} 停留、{@code fadeOut} 淡出。
 * 默认值与原版 {@code /title times} 一致（0.5 / 3.5 / 1.0）。</p>
 */
public record NodeTitle(String text, String subtitle, double fadeIn, double stay, double fadeOut) {

    public static final double DEFAULT_FADE_IN = 0.5D;
    public static final double DEFAULT_STAY = 3.5D;
    public static final double DEFAULT_FADE_OUT = 1.0D;
    /** 时间上限（秒），与节点的 hold / duration 上限保持一致。 */
    public static final double MAX_SECONDS = 600.0D;

    public static final NodeTitle EMPTY =
            new NodeTitle("", "", DEFAULT_FADE_IN, DEFAULT_STAY, DEFAULT_FADE_OUT);

    public NodeTitle {
        text = text == null ? "" : text;
        subtitle = subtitle == null ? "" : subtitle;
        fadeIn = clamp(fadeIn, DEFAULT_FADE_IN);
        stay = clamp(stay, DEFAULT_STAY);
        fadeOut = clamp(fadeOut, DEFAULT_FADE_OUT);
    }

    /** 两行都为空：不显示标题。 */
    public boolean isEmpty() {
        return this.text.isBlank() && this.subtitle.isBlank();
    }

    public boolean hasTimes() {
        return this.fadeIn > 0.0D || this.stay > 0.0D || this.fadeOut > 0.0D;
    }

    public NodeTitle withText(String value) {
        return new NodeTitle(value, this.subtitle, this.fadeIn, this.stay, this.fadeOut);
    }

    public NodeTitle withSubtitle(String value) {
        return new NodeTitle(this.text, value, this.fadeIn, this.stay, this.fadeOut);
    }

    public NodeTitle withFadeIn(double value) {
        return new NodeTitle(this.text, this.subtitle, value, this.stay, this.fadeOut);
    }

    public NodeTitle withStay(double value) {
        return new NodeTitle(this.text, this.subtitle, this.fadeIn, value, this.fadeOut);
    }

    public NodeTitle withFadeOut(double value) {
        return new NodeTitle(this.text, this.subtitle, this.fadeIn, this.stay, value);
    }

    private static double clamp(double value, double fallback) {
        if (!Double.isFinite(value)) {
            return fallback;
        }
        return Math.max(0.0D, Math.min(MAX_SECONDS, value));
    }
}
