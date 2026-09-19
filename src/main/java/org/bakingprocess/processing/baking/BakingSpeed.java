package org.bakingprocess.processing.baking;

import java.util.Collection;

/**
 * 由柴火堆的热量算出烘烤进度每 tick 该涨多少。
 * 一个柴火堆：基础速度 10 + 火力加成；多个柴火堆一起烧时按收益递减合并，
 * 避免柴火堆越多快得离谱。石板自己烧和烤架从下面加热都用这一套算法。
 */
public final class BakingSpeed {
    private static final int BASE_SPEED = 10;
    private static final double DIMINISHING_EXPONENT = 0.7;

    private BakingSpeed() {}

    /**
     * 单个热量源的烘烤速度：基础速度 + 热量加成。
     */
    public static int forHeatSource(HeatSource source) {
        return BASE_SPEED + (source.getHeatLevel() - 1);
    }

    /**
     * 多个热量源的合并烘烤速度：按收益递减叠加，至少为基础速度；无燃烧热量源时返回 0。
     */
    public static int forHeatSources(Collection<? extends HeatSource> sources) {
        int activeCount = 0;
        double totalEffectiveSpeed = 0;
        for (HeatSource source : sources) {
            if (source != null && source.isCombusting()) {
                activeCount++;
                double individualSpeed = forHeatSource(source);
                totalEffectiveSpeed += individualSpeed / Math.pow(activeCount, DIMINISHING_EXPONENT);
            }
        }
        if (activeCount == 0) {
            return 0;
        }
        return Math.max(BASE_SPEED, (int) Math.round(totalEffectiveSpeed));
    }
}
