package dev.cameratrack.playback;

import dev.cameratrack.text.TextComponents;
import dev.cameratrack.track.NodeTitle;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;
import org.bukkit.entity.Player;

import java.time.Duration;
import java.util.function.Consumer;

/**
 * 把节点上的 {@link NodeTitle} 渲染成 Adventure {@link Title} 并显示给玩家。
 *
 * <p>两行文本分别解析（MiniMessage 或原版 JSON 文本组件），三段时长由节点单独配置。
 * 文本无法解析时不显示，并通过 {@code warning} 回调报告一次。</p>
 */
public final class TitleRenderer {

    private TitleRenderer() {
    }

    /**
     * @return 是否真的发送了标题
     */
    public static boolean show(Player player, NodeTitle setting, int index, Consumer<String> warning) {
        if (player == null || setting == null || setting.isEmpty()) {
            return false;
        }
        Component main = TextComponents.parse(setting.text(), player.getName());
        Component subtitle = TextComponents.parse(setting.subtitle(), player.getName());
        if (main == null && subtitle == null) {
            if (warning != null) {
                warning.accept("节点 #" + index + " 的标题文本无法解析，已跳过: "
                        + (setting.text().isBlank() ? setting.subtitle() : setting.text()));
            }
            return false;
        }
        Title title = Title.title(
                main == null ? Component.empty() : main,
                subtitle == null ? Component.empty() : subtitle,
                Title.Times.times(
                        seconds(setting.fadeIn()),
                        seconds(setting.stay()),
                        seconds(setting.fadeOut())));
        player.showTitle(title);
        return true;
    }

    private static Duration seconds(double value) {
        return Duration.ofMillis(Math.max(0L, Math.round(value * 1000.0D)));
    }
}
