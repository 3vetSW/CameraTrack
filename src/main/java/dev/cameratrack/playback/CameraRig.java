package dev.cameratrack.playback;

import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

/**
 * 镜头驱动器：负责把采样出的位姿作用到玩家（或玩家正在旁观的实体）身上。
 */
public interface CameraRig {

    /** 会话开始、玩家状态已切换后调用一次。 */
    void attach(Player player, Pose pose);

    /** 每个 tick 调用。 */
    void apply(Player player, Pose pose);

    /**
     * 硬切到新位姿（“切换”节点）：不做插值，镜头立即跳到目标位置。
     * 实体运镜会换一个全新的镜头实体，避免客户端把位移插值成一段滑动。
     */
    void snap(Player player, Pose pose);

    /** 正常结束：先解除镜头控制，再由会话恢复玩家状态。 */
    void detach(Player player);

    /** 这个实体是否就是本会话的镜头实体（用于拒绝旁观者切到别的实体）。 */
    boolean isCamera(Entity entity);

    /** 玩家已离线 / 插件卸载：只清理世界中的临时对象。 */
    void abort();
}
