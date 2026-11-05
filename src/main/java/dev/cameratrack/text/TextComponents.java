package dev.cameratrack.text;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.json.JSONComponentSerializer;

/**
 * 把节点上保存的标题文本解析成 Adventure {@link Component}。
 *
 * <p>支持两种写法，按首字符自动识别：</p>
 * <ul>
 *     <li><b>原版 JSON 文本组件</b>（以 {@code {} 或 {@code [} 开头），例如
 *     <code>{"translate":"multiplayer.player.joined","with":[{"text":"Steve"}],"color":"gold"}</code>。
 *     与 {@code /tellraw}、{@code /title} 的 JSON 参数完全一致，支持 {@code text}、{@code translate}、
 *     {@code with}、{@code extra}、颜色、{@code bold/italic/underlined/strikethrough/obfuscated} 等。</li>
 *     <li><b>MiniMessage</b>（其余情况），例如
 *     <code>&lt;gold&gt;第一章 · 黎明&lt;/gold&gt;</code>、<code>&lt;lang:multiplayer.player.joined:'&lt;player&gt;'&gt;</code>。</li>
 * </ul>
 *
 * <p>两种写法都支持占位符 {@code <player>}（显示为"正在观看的玩家名"）。
 * 解析失败时返回 {@code null}，由调用方决定是拒绝保存还是只跳过显示。</p>
 */
public final class TextComponents {

    private static final MiniMessage MINI = MiniMessage.miniMessage();
    private static final JSONComponentSerializer JSON = JSONComponentSerializer.json();
    private static final String PLAYER_TOKEN = "<player>";

    private TextComponents() {
    }

    /**
     * 解析标题文本；空文本返回 {@code null}（表示不显示）。
     *
     * @param raw        原始文本（MiniMessage 或 JSON）
     * @param playerName 用于替换 {@code <player>} 的玩家名，可为 {@code null}
     * @return 解析后的组件，失败或为空时返回 {@code null}
     */
    public static Component parse(String raw, String playerName) {
        String input = prepare(raw, playerName);
        if (input == null) {
            return null;
        }
        try {
            if (looksLikeJson(input)) {
                return JSON.deserialize(input);
            }
            return MINI.deserialize(input);
        } catch (RuntimeException exception) {
            return null;
        }
    }

    /** 供保存前校验：空文本合法；非空时要求能解析成功。 */
    public static boolean isValid(String raw, String playerName) {
        if (raw == null || raw.isBlank()) {
            return true;
        }
        return parse(raw, playerName) != null;
    }

    /** 是否为 JSON 文本组件写法。 */
    public static boolean looksLikeJson(String raw) {
        if (raw == null) {
            return false;
        }
        String trimmed = raw.trim();
        return trimmed.startsWith("{") || trimmed.startsWith("[");
    }

    private static String prepare(String raw, String playerName) {
        if (raw == null) {
            return null;
        }
        String value = raw.trim();
        if (value.isEmpty()) {
            return null;
        }
        if (playerName != null && !playerName.isEmpty()) {
            value = value.replace(PLAYER_TOKEN, playerName);
        }
        return value;
    }
}
