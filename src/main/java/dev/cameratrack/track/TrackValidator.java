package dev.cameratrack.track;

import java.util.List;

/**
 * 播放前的轨道结构校验。
 *
 * <p>贝塞尔节点不是镜头，只是前后两个“默认”节点之间的控制点；因此它不能位于
 * 轨道两端、不能与切换节点相邻、也不能两个贝塞尔节点连续出现。违反这些结构时
 * 轨道无法正常播放，需要在播放前拒绝并给出提示。</p>
 */
public final class TrackValidator {

    /** 一条校验问题：{@code key} 是语言键，{@code value} 是节点序号（1 基）或节点数。 */
    public record Problem(String key, int value) {
    }

    private TrackValidator() {
    }

    /**
     * @return 可播放时为 {@code null}，否则返回第一条问题
     */
    public static Problem validate(CameraTrack track) {
        if (track == null) {
            return new Problem("play.too-few", 0);
        }
        List<CameraNode> nodes = track.nodes();
        if (nodes.size() < 2) {
            return new Problem("play.too-few", nodes.size());
        }

        boolean anyDefault = false;
        boolean anyBezier = false;
        for (CameraNode node : nodes) {
            if (node.type() == NodeType.DEFAULT) {
                anyDefault = true;
            }
            if (node.type() == NodeType.BEZIER) {
                anyBezier = true;
            }
        }
        if (anyBezier && !anyDefault) {
            return new Problem("play.invalid.bezier-only", -1);
        }

        for (int i = 0; i < nodes.size(); i++) {
            if (nodes.get(i).type() != NodeType.BEZIER) {
                continue;
            }
            boolean previousDefault = i > 0 && nodes.get(i - 1).type() == NodeType.DEFAULT;
            boolean nextDefault = i + 1 < nodes.size() && nodes.get(i + 1).type() == NodeType.DEFAULT;
            if (!previousDefault || !nextDefault) {
                return new Problem("play.invalid.bezier", i + 1);
            }
        }

        int playable = track.playableNodes().size();
        if (playable < 2) {
            return new Problem("play.too-few", playable);
        }
        return null;
    }
}
