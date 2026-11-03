package dev.cameratrack.track;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 一条摄像轨道：按顺序排列的节点，相邻节点自动连接（无需手动连线）。
 */
public final class CameraTrack {

    private final String name;
    private final List<CameraNode> nodes = new ArrayList<>();
    private boolean loop;
    /** 平滑巡航：节点之间连续通过，只在起点/终点/停留处加减速。 */
    private boolean flow = true;
    /** 播放结束后是否把玩家送回播放前的位置。 */
    private boolean returnToStart = true;

    public CameraTrack(String name) {
        this.name = name;
    }

    public String name() {
        return this.name;
    }

    /** 只读视图，编辑请使用 {@link #add}/{@link #insert}/{@link #set}/{@link #remove}。 */
    public List<CameraNode> nodes() {
        return Collections.unmodifiableList(this.nodes);
    }

    /** 播放时使用的快照，避免播放途中被编辑导致跳变。 */
    public List<CameraNode> snapshot() {
        return List.copyOf(this.nodes);
    }

    public boolean loop() {
        return this.loop;
    }

    public void setLoop(boolean loop) {
        this.loop = loop;
    }

    public boolean flow() {
        return this.flow;
    }

    public void setFlow(boolean flow) {
        this.flow = flow;
    }

    public boolean returnToStart() {
        return this.returnToStart;
    }

    public void setReturnToStart(boolean returnToStart) {
        this.returnToStart = returnToStart;
    }

    public int size() {
        return this.nodes.size();
    }

    public void add(CameraNode node) {
        this.nodes.add(node);
    }

    public void insert(int index, CameraNode node) {
        this.nodes.add(index, node);
    }

    public CameraNode remove(int index) {
        return this.nodes.remove(index);
    }

    public CameraNode get(int index) {
        return this.nodes.get(index);
    }

    public void set(int index, CameraNode node) {
        this.nodes.set(index, node);
    }

    /** 整条轨道播放一遍的时长（含每个节点的停留）。 */
    public double totalDuration() {
        int count = this.nodes.size();
        if (count == 0) {
            return 0.0D;
        }
        double total = this.nodes.get(count - 1).hold();
        for (int i = 0; i < count - 1; i++) {
            total += this.nodes.get(i).hold() + Math.max(0.0D, this.nodes.get(i).duration());
        }
        return total;
    }
}
