package dev.cameratrack.tool;

import dev.cameratrack.CameraTrackPlugin;
import dev.cameratrack.lang.Lang;
import dev.cameratrack.playback.PreviewRenderer;
import dev.cameratrack.track.CameraNode;
import dev.cameratrack.track.CameraTrack;
import dev.cameratrack.track.Ease;
import dev.cameratrack.track.TrackStore;
import io.papermc.paper.datacomponent.DataComponentTypes;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 手持式摄像工具：烈焰棒外形，但用 item_model 组件显示成望远镜（spyglass）。
 *
 * <ul>
 *     <li>右键：把当前视角记录为当前轨道的新节点</li>
 *     <li>Shift + 右键：打开当前轨道的 Dialog 编辑界面</li>
 *     <li>左键：显示当前轨道信息，并像 /camera preview 一样用粒子画出整条轨迹</li>
 *     <li>Shift + 左键：切换到下一条轨道</li>
 * </ul>
 */
public final class CameraTool implements Listener {

    private static final int MAX_LISTED_NODES = 15;
    /** 同一次点击可能同时产生主手/副手两个事件，用短冷却去重。 */
    private static final long INTERACT_COOLDOWN_MS = 150L;

    private final CameraTrackPlugin plugin;
    private final TrackStore store;
    private final Lang lang;
    private final NamespacedKey toolKey;
    private final Map<UUID, String> currentTracks = new HashMap<>();
    private final Map<UUID, Long> lastInteract = new HashMap<>();

    public CameraTool(CameraTrackPlugin plugin, TrackStore store, Lang lang) {
        this.plugin = plugin;
        this.store = store;
        this.lang = lang;
        this.toolKey = new NamespacedKey(plugin, "camera_tool");
    }

    // ------------------------------------------------------------------ 物品

    public ItemStack createItem() {
        ItemStack stack = new ItemStack(Material.BLAZE_ROD);
        stack.setData(DataComponentTypes.ITEM_MODEL, spyglassModel());
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.displayName(Component.text("摄像工具", NamedTextColor.GOLD)
                    .decoration(TextDecoration.ITALIC, false));
            meta.lore(List.of(
                    Component.text("右键：添加节点", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false),
                    Component.text("Shift+右键：打开轨道界面", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false),
                    Component.text("左键：查看轨道并预览轨迹", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false),
                    Component.text("Shift+左键：切换轨道", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false)
            ));
            meta.setEnchantmentGlintOverride(true);
            meta.setMaxStackSize(1);
            meta.getPersistentDataContainer().set(this.toolKey, PersistentDataType.BYTE, (byte) 1);
            stack.setItemMeta(meta);
        }
        return stack;
    }

    private static Key spyglassModel() {
        // 直接复用原版望远镜的 item_model，避免手写 model 路径
        Key model = new ItemStack(Material.SPYGLASS).getData(DataComponentTypes.ITEM_MODEL);
        return model != null ? model : Key.key("minecraft:spyglass");
    }

    public boolean isTool(ItemStack stack) {
        if (stack == null || stack.getType() != Material.BLAZE_ROD) {
            return false;
        }
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) {
            return false;
        }
        Byte value = meta.getPersistentDataContainer().get(this.toolKey, PersistentDataType.BYTE);
        return value != null && value == 1;
    }

    private ItemStack heldTool(Player player) {
        ItemStack main = player.getInventory().getItemInMainHand();
        return isTool(main) ? main : null;
    }

    // ------------------------------------------------------------------ 交互

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        if (heldTool(player) == null) {
            return;
        }
        // 右键方块时 Paper 会为主手和副手各发一次事件，只处理主手
        EquipmentSlot hand = event.getHand();
        if (hand != null && hand != EquipmentSlot.HAND) {
            return;
        }
        long now = System.currentTimeMillis();
        Long last = this.lastInteract.get(player.getUniqueId());
        if (last != null && now - last < INTERACT_COOLDOWN_MS) {
            event.setCancelled(true);
            return;
        }
        this.lastInteract.put(player.getUniqueId(), now);

        switch (event.getAction()) {
            case LEFT_CLICK_BLOCK, LEFT_CLICK_AIR -> {
                event.setCancelled(true);
                if (player.isSneaking()) {
                    cycleTrack(player);
                } else {
                    showTrack(player);
                }
            }
            case RIGHT_CLICK_BLOCK, RIGHT_CLICK_AIR -> {
                event.setCancelled(true);
                if (player.isSneaking()) {
                    openTrackGui(player);
                } else {
                    addNode(player);
                }
            }
            default -> {
                // 其它动作不处理
            }
        }
    }

    /** 工具拿在主手时禁止破坏方块（创造模式左键会瞬间破坏）。 */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onBlockBreak(BlockBreakEvent event) {
        if (isTool(event.getPlayer().getInventory().getItemInMainHand())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        this.currentTracks.remove(event.getPlayer().getUniqueId());
        this.lastInteract.remove(event.getPlayer().getUniqueId());
    }

    // ------------------------------------------------------------------ 动作

    private void addNode(Player player) {
        CameraTrack track = currentTrack(player);
        if (track == null) {
            this.lang.send(player, "tool.no-track");
            return;
        }
        Location eye = player.getEyeLocation();
        track.add(CameraNode.at(eye, CameraNode.DEFAULT_HOLD, CameraNode.DEFAULT_DURATION, Ease.SMOOTH, ""));
        this.store.save();
        this.lang.send(player, "tool.add.success",
                "name", track.name(), "index", track.size(), "count", track.size());
    }

    private void cycleTrack(Player player) {
        List<String> names = new ArrayList<>(this.store.names());
        if (names.isEmpty()) {
            this.lang.send(player, "tool.no-track");
            return;
        }
        String current = this.currentTracks.get(player.getUniqueId());
        int index = current == null ? -1 : names.indexOf(current);
        String next = names.get((index + 1) % names.size());
        this.currentTracks.put(player.getUniqueId(), next);
        CameraTrack track = this.store.get(next);
        this.lang.send(player, "tool.switched", "name", next, "count", track == null ? 0 : track.size());
    }

    private void openTrackGui(Player player) {
        CameraTrack track = currentTrack(player);
        if (track == null) {
            this.lang.send(player, "tool.no-track");
            return;
        }
        if (this.plugin.playback().isPlaying(player)) {
            this.lang.send(player, "ui.busy");
            return;
        }
        this.plugin.dialogs().openTrack(player, track.name());
    }

    private void showTrack(Player player) {
        CameraTrack track = currentTrack(player);
        if (track == null) {
            this.lang.send(player, "tool.no-track");
            return;
        }
        this.lang.send(player, "tool.header",
                "name", track.name(),
                "count", track.size(),
                "duration", format(track.totalDuration()),
                "loop", this.lang.component(player, track.loop() ? "common.on" : "common.off"));
        if (track.size() == 0) {
            this.lang.sendPlain(player, "tool.empty");
            return;
        }
        int shown = Math.min(track.size(), MAX_LISTED_NODES);
        for (int i = 0; i < shown; i++) {
            CameraNode node = track.get(i);
            Component line = this.lang.component(player, "nodes.entry",
                    "index", i + 1,
                    "x", format(node.x()),
                    "y", format(node.y()),
                    "z", format(node.z()),
                    "yaw", format(node.yaw()),
                    "pitch", format(node.pitch()),
                    "hold", format(node.hold()),
                    "duration", format(node.duration()),
                    "ease", node.ease().id(),
                    "path", node.path().id());
            if (!node.command().isBlank()) {
                line = line.append(this.lang.component(player, "nodes.command", "command", node.command()));
            }
            player.sendMessage(line);
        }
        if (track.size() > shown) {
            this.lang.sendPlain(player, "tool.more", "count", track.size() - shown, "name", track.name());
        }
        World world = Bukkit.getWorld(track.get(0).world());
        int points = world == null ? 0
                : PreviewRenderer.render(world, track, this.plugin.axisTolerance(), track.flow());
        if (points > 0) {
            this.lang.sendPlain(player, "preview.success", "count", points);
        }
    }
    private CameraTrack currentTrack(Player player) {
        String name = this.currentTracks.get(player.getUniqueId());
        CameraTrack track = name == null ? null : this.store.get(name);
        if (track != null) {
            return track;
        }
        Set<String> names = this.store.names();
        if (names.isEmpty()) {
            return null;
        }
        String first = names.iterator().next();
        this.currentTracks.put(player.getUniqueId(), first);
        return this.store.get(first);
    }

    private static String format(double value) {
        return String.format(java.util.Locale.ROOT, "%.1f", value);
    }
}



