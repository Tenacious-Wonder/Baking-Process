package org.bakingprocess.processing.baking;

/**
 * 能发热的东西（热量源）：供烘烤问"你有多热、烧着没有"。
 * 真实的燃烧柴火堆和烤架里的虚拟柴火堆都算热量源。
 */
public interface HeatSource {

    /**
     * 当前热量等级：0 无热量，1 低热量（保温），2 高热量（满火）。
     */
    int getHeatLevel();

    /**
     * 当前是否有热量（等价于热量等级大于 0）。
     */
    boolean isCombusting();
}
