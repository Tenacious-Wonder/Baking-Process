package org.bakingprocess.processing.baking;

import net.minecraft.nbt.NbtCompound;
import org.bakingprocess.block.CombustionFirewoodBlock;
import org.jetbrains.annotations.Nullable;

/**
 * 柴火堆的燃烧状态机：记住"这堆柴烧到哪了"。
 * 核心数据是剩余能量——烧了多久、是什么火力（满火 / 保温 / 熄灭）、外观该是
 * 哪种灰烬，全都由能量和添柴历史推算出来。
 *
 * <p>本类不碰世界：真实柴火堆和烤架里的虚拟柴火堆共用这一份逻辑，
 * 往世界写状态、放粒子这些事由持有它的方块实体去做。</p>
 */
public class FirewoodCombustion implements HeatSource {
    /** 满能量：每 tick 消耗 1 点，约 10 分钟烧完 */
    public static final int MAX_ENERGY = 12000;
    /** 50% 能量阈值：高于此值为满火阶段 */
    public static final int HALF_ENERGY = MAX_ENERGY / 2;
    /** 每次添柴增加的能量（50%） */
    public static final int FIREWOOD_ENERGY = HALF_ENERGY;

    private static final String ENERGY_KEY = "Energy";
    private static final String ROUND_KEY = "Round";
    private static final String LEGACY_FIRST_CYCLE_KEY = "IsFirstCycle";

    /** 当前剩余能量，每 tick 消耗 1 点，添柴时按 {@link #FIREWOOD_ENERGY} 增加；唯一的真实输入 */
    private int energy;
    /** 当前燃烧循环归属，随添柴与燃烧阶段变化（见 {@link #refreshRound()}） */
    private Round round = Round.FIRST;

    /** 状态变化通知：能量类状态变化时由持有方注入（通常为重推视觉状态） */
    @Nullable
    private Runnable onChange;

    public FirewoodCombustion() {}

    public FirewoodCombustion(@Nullable Runnable onChange) {
        this.onChange = onChange;
    }

    /**
     * 设置状态变化回调（能量类状态变化时调用）。
     */
    public void setOnChange(@Nullable Runnable onChange) {
        this.onChange = onChange;
    }

    // ==================== 状态刷新与视觉 ====================

    /**
     * 按当前能量与添柴历史，算出柴火堆现在该显示的外观。
     * 状态变化或读档后由持有方调用。
     *
     * @return 当前应展示的燃烧外观
     */
    public CombustionFirewoodBlock.CombustionState refresh() {
        refreshRound();
        return mapVisual();
    }

    /**
     * 把当前阶段与归属换算成燃烧柴火堆的外观状态。
     */
    public CombustionFirewoodBlock.CombustionState mapVisual() {
        return mapVisual(getPhase(), round);
    }

    private static CombustionFirewoodBlock.CombustionState mapVisual(CombustionPhase phase, Round round) {
        if (round == Round.FIRST) {
            return switch (phase) {
                case IGNITED -> CombustionFirewoodBlock.CombustionState.FIRST_IGNITED;
                case HALF -> CombustionFirewoodBlock.CombustionState.FIRST_HALF;
                case EXTINGUISHED -> CombustionFirewoodBlock.CombustionState.FIRST_EXTINGUISHED;
            };
        }
        // 非首次归属：再次添柴（READDED）只在满火时表现为 REIGNITED，其余退回非首次外观
        return switch (phase) {
            case IGNITED -> round == Round.READDED ?
                    CombustionFirewoodBlock.CombustionState.REIGNITED :
                    CombustionFirewoodBlock.CombustionState.AGAIN_IGNITED;
            case HALF -> CombustionFirewoodBlock.CombustionState.AGAIN_HALF;
            case EXTINGUISHED -> CombustionFirewoodBlock.CombustionState.AGAIN_EXTINGUISHED;
        };
    }

    private void refreshRound() {
        // 只有满火时才保留"再次添柴"外观；烧到半段或熄灭就退回普通的"非首次"外观
        if (round == Round.READDED && getPhase() != CombustionPhase.IGNITED) {
            round = Round.AGAIN;
        }
    }

    // ==================== 添柴 ====================

    /**
     * 添柴操作：任何能量不满且未完全燃尽的状态都可以添柴。
     *
     * @return 是否成功添柴
     */
    public boolean addFirewood() {
        if (energy >= MAX_ENERGY || isCompletelyExtinguished()) {
            return false; // 能量已满或完全燃尽，无法添柴
        }

        // 归属推进：非首次满火（再添柴标记）再添就回到非首次，其余情况进入"再次添柴"归属
        this.round = round == Round.AGAIN ? Round.READDED : Round.AGAIN;
        addEnergy(FIREWOOD_ENERGY);
        return true;
    }

    // ==================== 状态推导 ====================

    /**
     * 当前燃烧阶段（由能量推导）。
     */
    public CombustionPhase getPhase() {
        if (energy <= 0) {
            return CombustionPhase.EXTINGUISHED;
        }
        return energy > HALF_ENERGY ? CombustionPhase.IGNITED : CombustionPhase.HALF;
    }

    /**
     * 当前循环归属。
     */
    public Round getRound() {
        return round;
    }

    /**
     * 设置循环归属。
     */
    public void setRound(Round round) {
        this.round = round;
    }

    /**
     * 是否处于首次燃烧循环（首次归属）。
     */
    public boolean isFirstCycle() {
        return round == Round.FIRST;
    }

    /**
     * 设置是否首次循环（兼容旧 API，映射到归属档）。
     */
    public void setFirstCycle(boolean firstCycle) {
        this.round = firstCycle ? Round.FIRST : Round.AGAIN;
    }

    /**
     * 设置循环次数（兼容旧 API，映射到归属档）。
     */
    public void setCycleCount(int count) {
        this.round = count <= 0 ? Round.FIRST : Round.AGAIN;
    }

    @Override
    public int getHeatLevel() {
        if (energy <= 0) {
            return 0;
        }
        return energy > HALF_ENERGY ? 2 : 1;
    }

    /**
     * 检查是否有热量。
     */
    public boolean hasHeat() {
        return isCombusting();
    }

    @Override
    public boolean isCombusting() {
        return getHeatLevel() > 0;
    }

    // ==================== 能量操作 ====================

    public void addEnergy(int energy) {
        int oldEnergy = this.energy;
        this.energy = Math.min(this.energy + energy, MAX_ENERGY);
        if (oldEnergy != this.energy) {
            notifyChanged();
        }
    }

    public boolean consumeEnergy() {
        return consumeEnergy(1);
    }

    /**
     * 消耗指定数量的能量。
     *
     * @param amount 消耗的能量数量
     * @return 是否成功消耗了能量
     */
    public boolean consumeEnergy(int amount) {
        if (energy <= 0 || amount <= 0) {
            return false;
        }

        int oldEnergy = this.energy;
        this.energy = Math.max(0, this.energy - amount);

        if (oldEnergy != this.energy) {
            notifyChanged();
            return true;
        }
        return false;
    }

    public void setEnergy(int energy) {
        int oldEnergy = this.energy;
        this.energy = Math.min(energy, MAX_ENERGY);
        if (oldEnergy != this.energy) {
            notifyChanged();
        }
    }

    // ==================== 查询与状态判定 ====================

    /**
     * 检查是否可以添柴。
     */
    public boolean canAddFirewood() {
        return energy < MAX_ENERGY && !isCompletelyExtinguished();
    }

    /**
     * 检查能量是否已经耗尽。
     */
    public boolean isEnergyDepleted() {
        return energy <= 0;
    }

    /**
     * 获取能量消耗进度（0.0 到 1.0）：0 表示满能量，1 表示能量耗尽。
     */
    public float getEnergyConsumptionProgress() {
        return 1.0f - ((float) energy / MAX_ENERGY);
    }

    /**
     * 获取当前能量百分比。
     */
    public float getEnergyRatio() {
        return (float) energy / MAX_ENERGY;
    }

    /**
     * 检查是否完全燃尽（能量耗尽即燃尽，不再能燃烧）。
     */
    public boolean isCompletelyExtinguished() {
        return getPhase() == CombustionPhase.EXTINGUISHED;
    }

    /**
     * 获取半能量值（50%）。
     */
    public int getHalfEnergy() {
        return HALF_ENERGY;
    }

    public int getEnergy() {
        return energy;
    }

    public static int getMaxEnergy() {
        return MAX_ENERGY;
    }

    // ==================== 序列化 ====================

    public void writeNbt(NbtCompound nbt) {
        nbt.putInt(ENERGY_KEY, energy);
        nbt.putInt(ROUND_KEY, round.ordinal());
    }

    public void readNbt(NbtCompound nbt) {
        energy = nbt.getInt(ENERGY_KEY);
        // 优先读新字段 Round；旧存档则从 IsFirstCycle 映射归属
        if (nbt.contains(ROUND_KEY)) {
            int roundIndex = nbt.getInt(ROUND_KEY);
            if (roundIndex >= 0 && roundIndex < Round.values().length) {
                round = Round.values()[roundIndex];
            }
        } else {
            round = nbt.getBoolean(LEGACY_FIRST_CYCLE_KEY) ? Round.FIRST : Round.AGAIN;
        }
    }

    // ==================== 内部 ====================

    private void notifyChanged() {
        refreshRound();
        if (onChange != null) {
            onChange.run();
        }
    }

    // ==================== 逻辑枚举 ====================

    /**
     * 燃烧阶段：由剩余能量唯一决定，是逻辑层对"烧到哪了"的抽象。
     */
    public enum CombustionPhase {
        /** 满火阶段：能量大于 50% */
        IGNITED,
        /** 保温阶段：能量不足 50% 但尚未耗尽 */
        HALF,
        /** 熄灭阶段：能量耗尽 */
        EXTINGUISHED
    }

    /**
     * 燃烧循环归属：记录当前处于哪一轮燃烧，决定使用哪套视觉外观。
     */
    public enum Round {
        /** 首次燃烧 */
        FIRST,
        /** 非首次燃烧（添过一次柴） */
        AGAIN,
        /** 再次添柴（仅在满火阶段有效，其余归入 {@link #AGAIN}） */
        READDED
    }
}
