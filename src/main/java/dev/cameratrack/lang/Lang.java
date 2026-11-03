package dev.cameratrack.lang;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 简单的 JSON 翻译表：{@code plugins/CameraTrack/lang/<locale>.json}。
 *
 * <p>值使用 MiniMessage 语法，占位符写成 {@code %name%}，调用时传 key/value 对。
 * 内置 zh_cn / en_us，磁盘文件只覆盖存在的键，升级后新增键会从 jar 里合并进来。</p>
 */
public final class Lang {

    private static final List<String> BUILTIN = List.of("zh_cn", "en_us");

    private final JavaPlugin plugin;
    private final String configured;
    private final boolean auto;
    private final Map<String, Map<String, String>> locales = new LinkedHashMap<>();
    private final MiniMessage mini = MiniMessage.miniMessage();

    public Lang(JavaPlugin plugin, String configured) {
        this.plugin = plugin;
        this.configured = configured == null || configured.isBlank()
                ? "zh_cn" : configured.toLowerCase(Locale.ROOT);
        this.auto = this.configured.equals("auto");
    }

    public void load() {
        File directory = new File(this.plugin.getDataFolder(), "lang");
        if (!directory.isDirectory() && !directory.mkdirs()) {
            this.plugin.getLogger().warning("无法创建语言目录: " + directory);
        }
        for (String locale : BUILTIN) {
            File file = new File(directory, locale + ".json");
            if (!file.isFile()) {
                this.plugin.saveResource("lang/" + locale + ".json", false);
            }
        }

        this.locales.clear();
        for (String locale : BUILTIN) {
            Map<String, String> entries = new LinkedHashMap<>();
            readBundled(locale, entries);
            readDisk(new File(directory, locale + ".json"), entries);
            this.locales.put(locale, entries);
        }

        File[] extra = directory.listFiles((dir, name) -> name.endsWith(".json"));
        if (extra != null) {
            for (File file : extra) {
                String locale = file.getName().substring(0, file.getName().length() - 5).toLowerCase(Locale.ROOT);
                if (this.locales.containsKey(locale)) {
                    continue;
                }
                Map<String, String> entries = new LinkedHashMap<>();
                readDisk(file, entries);
                this.locales.put(locale, entries);
            }
        }
    }

    private void readBundled(String locale, Map<String, String> target) {
        try (InputStream stream = this.plugin.getResource("lang/" + locale + ".json")) {
            if (stream == null) {
                return;
            }
            parse(new InputStreamReader(stream, StandardCharsets.UTF_8), target);
        } catch (Exception exception) {
            this.plugin.getLogger().warning("读取内置语言文件失败 " + locale + ": " + exception.getMessage());
        }
    }

    private void readDisk(File file, Map<String, String> target) {
        if (!file.isFile()) {
            return;
        }
        try (InputStreamReader reader = new InputStreamReader(Files.newInputStream(file.toPath()), StandardCharsets.UTF_8)) {
            parse(reader, target);
        } catch (Exception exception) {
            this.plugin.getLogger().warning("读取语言文件失败 " + file.getName() + ": " + exception.getMessage());
        }
    }

    private static void parse(InputStreamReader reader, Map<String, String> target) {
        JsonObject object = JsonParser.parseReader(reader).getAsJsonObject();
        for (Map.Entry<String, JsonElement> entry : object.entrySet()) {
            if (entry.getValue().isJsonPrimitive()) {
                target.put(entry.getKey(), entry.getValue().getAsString());
            }
        }
    }

    public Component component(CommandSender sender, String key, Object... placeholders) {
        String raw = lookup(localeOf(sender), key);
        if (raw == null) {
            raw = "<red>Missing lang key: " + key + "</red>";
        }
        return this.mini.deserialize(raw, resolvers(placeholders));
    }

    public void send(CommandSender sender, String key, Object... placeholders) {
        sender.sendMessage(prefix(sender).append(component(sender, key, placeholders)));
    }

    /** 不带前缀的续行（列表、节点明细等）。 */
    public void sendPlain(CommandSender sender, String key, Object... placeholders) {
        sender.sendMessage(component(sender, key, placeholders));
    }

    public Component prefix(CommandSender sender) {
        String raw = lookup(localeOf(sender), "prefix");
        return this.mini.deserialize(raw == null ? "" : raw);
    }

    private static TagResolver[] resolvers(Object... placeholders) {
        List<TagResolver> resolvers = new ArrayList<>();
        for (int i = 0; i + 1 < placeholders.length; i += 2) {
            String key = String.valueOf(placeholders[i]);
            Object value = placeholders[i + 1];
            if (value instanceof Component component) {
                resolvers.add(Placeholder.component(key, component));
            } else {
                resolvers.add(Placeholder.unparsed(key, String.valueOf(value)));
            }
        }
        return resolvers.toArray(TagResolver[]::new);
    }

    private String localeOf(CommandSender sender) {
        if (this.auto && sender instanceof Player player) {
            return player.locale().toString().toLowerCase(Locale.ROOT);
        }
        return this.configured.equals("auto") ? "zh_cn" : this.configured;
    }

    private String lookup(String locale, String key) {
        Map<String, String> entries = this.locales.get(locale);
        if (entries == null && locale.contains("_")) {
            entries = this.locales.get(locale.substring(0, locale.indexOf('_')));
        }
        if (entries != null && entries.containsKey(key)) {
            return entries.get(key);
        }
        Map<String, String> fallback = this.locales.get("zh_cn");
        return fallback == null ? null : fallback.get(key);
    }
}
