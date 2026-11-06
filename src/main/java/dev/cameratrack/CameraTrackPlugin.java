package dev.cameratrack;

import dev.cameratrack.command.CameraCommand;
import dev.cameratrack.lang.Lang;
import dev.cameratrack.playback.PathSampler;
import dev.cameratrack.playback.PendingReturns;
import dev.cameratrack.playback.PlaybackManager;
import dev.cameratrack.playback.PlaybackMode;
import dev.cameratrack.playback.PreviewRenderer;
import dev.cameratrack.playback.PreviewSettings;
import dev.cameratrack.track.TrackStore;
import dev.cameratrack.tool.CameraTool;
import dev.cameratrack.ui.CameraDialogs;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * CameraTrack - 自定义摄像轨道插件。
 *
 * <p>用指令在游戏里摆放镜头节点，节点之间自动连接（同平面段走直线，其余走 Catmull-Rom 样条）；
 * 播放时把玩家切到旁观者模式，并附身到一个不可见的镜头实体上，
 * 由实体移动驱动相机，从而得到接近原生运镜的平滑开场动画。</p>
 */
public final class CameraTrackPlugin extends JavaPlugin {

    private TrackStore store;
    private PlaybackManager playback;
    private CameraDialogs dialogs;
    private CameraTool tool;
    private PendingReturns pendingReturns;
    private Lang lang;
    private PreviewSettings previewSettings;
    private PreviewRenderer preview;
    private PlaybackMode defaultPlaybackMode = PlaybackMode.SPECTATOR_ENTITY;
    private double axisTolerance = 0.05D;
    private double arcStrength = PathSampler.DEFAULT_ARC_STRENGTH;
    private boolean autoArc = PathSampler.DEFAULT_AUTO_ARC;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        this.lang = new Lang(this, getConfig().getString("language", "zh_cn"));
        this.lang.load();
        this.defaultPlaybackMode = PlaybackMode.byId(getConfig().getString("playback-mode", "entity"));
        this.axisTolerance = Math.max(0.0D, getConfig().getDouble("path.axis-tolerance", 0.05D));
        this.arcStrength = Math.max(0.0D, Math.min(0.5D,
                getConfig().getDouble("path.arc-strength", PathSampler.DEFAULT_ARC_STRENGTH)));
        this.autoArc = getConfig().getBoolean("path.auto-arc", PathSampler.DEFAULT_AUTO_ARC);
        this.previewSettings = PreviewSettings.from(getConfig());
        this.preview = new PreviewRenderer(this, this.previewSettings);

        this.store = new TrackStore(this);
        this.store.load();

        this.pendingReturns = new PendingReturns(this);
        this.pendingReturns.load();

        this.playback = new PlaybackManager(this, this.store, this.axisTolerance,
                this.arcStrength, this.autoArc, this.pendingReturns, this.lang);
        getServer().getPluginManager().registerEvents(this.playback, this);
        this.dialogs = new CameraDialogs(this, this.store, this.playback, this.lang);
        this.tool = new CameraTool(this, this.store, this.lang);
        getServer().getPluginManager().registerEvents(this.tool, this);

        CameraCommand executor = new CameraCommand(this, this.store, this.playback, this.lang);
        PluginCommand command = getCommand("camera");
        if (command == null) {
            getLogger().severe("plugin.yml 中缺少 camera 指令，插件将被禁用。");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        command.setExecutor(executor);
        command.setTabCompleter(executor);

        getLogger().info("CameraTrack 已启用，已载入 " + this.store.tracks().size()
                + " 条摄像轨道，默认播放模式: " + this.defaultPlaybackMode.id()
                + "，同平面判定容差: " + this.axisTolerance);
    }

    @Override
    public void onDisable() {
        if (this.playback != null) {
            this.playback.stopAll();
        }
        if (this.preview != null) {
            this.preview.shutdown();
        }
        if (this.store != null) {
            this.store.save();
        }
        getLogger().info("CameraTrack 已卸载。");
    }

    public TrackStore store() {
        return this.store;
    }

    public PlaybackManager playback() {
        return this.playback;
    }

    public CameraDialogs dialogs() {
        return this.dialogs;
    }

    public CameraTool tool() {
        return this.tool;
    }

    public PreviewRenderer preview() {
        return this.preview;
    }

    public PreviewSettings previewSettings() {
        return this.previewSettings;
    }

    public PlaybackMode defaultPlaybackMode() {
        return this.defaultPlaybackMode;
    }

    public double axisTolerance() {
        return this.axisTolerance;
    }

    public double arcStrength() {
        return this.arcStrength;
    }

    public boolean autoArc() {
        return this.autoArc;
    }
}



