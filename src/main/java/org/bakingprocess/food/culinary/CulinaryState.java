package org.bakingprocess.food.culinary;

import net.minecraft.nbt.NbtCompound;

/**
 * 菜肴的动态状态数据类。
 *
 * <p>承载无法由加工步骤链决定、但语义上<b>跟随这道菜</b>的可读写事实，例如已经
 * 被吃了几口。它与步骤链（静态历史）共同构成一道菜的完整数据：步骤链决定"是什么菜"，
 * 本状态决定"这菜现在吃到哪了"。</p>
 *
 * <p>本类是<b>被动的账本</b>：它不自己随时间演化、不持有世界引用；字段的推进
 * 由持有这道菜的容器（或世界）在交互中驱动后写回。腐坏、时间戳等未来字段可在本类中扩展。</p>
 *
 * @see Culinary
 */
public final class CulinaryState {
    /** 已吃口数，默认 0。大于 0 表示这道菜已被食用过（不可再加工）。 */
    private int eatenCount;

    public CulinaryState() {
        this(0);
    }

    public CulinaryState(int eatenCount) {
        this.eatenCount = eatenCount;
    }

    /** 已吃口数。 */
    public int getEatenCount() {
        return eatenCount;
    }

    /** 是否已被吃过至少一口（即"已食用"，此后不允许再加工）。 */
    public boolean isConsumed() {
        return eatenCount > 0;
    }

    /** 吃下一口，将已吃口数加一。 */
    public void incrementEatenCount() {
        eatenCount++;
    }

    // ==================== NBT 序列化 ====================

    /** 将动态状态写入给定 NBT，返回写入后的同一份 NBT。 */
    public NbtCompound writeNbt(NbtCompound nbt) {
        nbt.putInt("eatenCount", eatenCount);
        return nbt;
    }

    /** 用 NBT 覆盖当前动态状态，返回 this。 */
    public CulinaryState readNbt(NbtCompound nbt) {
        eatenCount = nbt.getInt("eatenCount");
        return this;
    }
}
