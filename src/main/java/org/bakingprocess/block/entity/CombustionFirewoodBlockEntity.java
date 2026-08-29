package org.bakingprocess.block.entity;

import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.World;
import org.bakingprocess.block.CombustionFirewoodBlock;
import org.bakingprocess.block.FirewoodBlock;
import org.bakingprocess.registry.ModBlockEntityTypes;

/**
 * <h1>柴火堆燃烧实体</h1>
 * <p>承载正在燃烧（或已燃尽）柴火堆的运行数据。核心设计是<b>把燃烧逻辑与视觉状态解耦</b>：
 * 剩余能量是唯一的真实输入，燃烧阶段（{@link CombustionPhase}）与循环归属（{@link Round}）
 * 都由能量与添柴历史推导；最终通过 {@link #mapVisual} 查表映射到 7 个视觉模型状态
 * （{@link CombustionFirewoodBlock.CombustionState}），模型数量与外观保持不变。</p>
 * <p>主要交互与生命周期：</p>
 * <ul>
 *     <li><b>能量</b>：满值 {@value #MAX_ENERGY}，每 tick 消耗 1 点，添柴恢复 50%；燃尽后无法再添柴。</li>
 *     <li><b>热量</b>：由剩余能量即时推导（{@link #getHeatLevel()}），供烤箱读取燃烧强度。</li>
 *     <li><b>视觉</b>：由阶段 × 归属查表得出，见 {@link #mapVisual}；首次与再次添柴的模型归属相互独立。</li>
 *     <li><b>可复用</b>：烤架等以 {@code world == null} 的虚拟柴火堆复用本逻辑，故燃烧状态读写经由
 *         {@link #getCombustionState} / {@link #setCombustionState}，子类可覆盖为无世界交互的实现。</li>
 * </ul>
 */
public class CombustionFirewoodBlockEntity extends BlockEntity {
    static final int MAX_ENERGY = 12000;
    static final int HALF_ENERGY = MAX_ENERGY / 2; // 50% 能量阈值
    static final int FIREWOOD_ENERGY = HALF_ENERGY; // 每次添柴增加 50% 能量
    /** 上方空间检查间隔：10 tick（半秒） */
    static final int SPACE_CHECK_INTERVAL = 10;

    /** 当前剩余能量，每 tick 消耗 1 点，添柴时按 {@link #FIREWOOD_ENERGY} 增加；唯一的真实输入 */
    protected int energy;
    /** 当前燃烧循环归属，随添柴与燃烧阶段变化（见 {@link #refresh()}） */
    protected Round round = Round.FIRST;
    /** 距上次上方空间检查经过的 tick 数（按半秒一次降频，避免每 tick 扫描上方 6 格） */
    protected int ticksSinceSpaceCheck;

    public CombustionFirewoodBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntityTypes.COMBUSTION_FIREWOOD, pos, state);
        // 首次点燃时设置满能量
        if (getCombustionState() == CombustionFirewoodBlock.CombustionState.FIRST_IGNITED) {
            this.energy = MAX_ENERGY;
            this.round = Round.FIRST;
        }
    }

    // ==================== 燃烧状态读写 ====================

    /**
     * 读取当前燃烧状态（默认从方块状态属性读取；子类可覆盖为虚拟状态）。
     */
    protected CombustionFirewoodBlock.CombustionState getCombustionState() {
        return getCachedState().get(CombustionFirewoodBlock.COMBUSTION_STATE);
    }

    /**
     * 写入燃烧状态（默认写入方块状态属性；子类可覆盖为空实现）。
     */
    protected void setCombustionState(CombustionFirewoodBlock.CombustionState state) {
        if (world != null) {
            world.setBlockState(pos, getCachedState().with(CombustionFirewoodBlock.COMBUSTION_STATE, state));
        }
    }

    // ==================== 燃烧推进 ====================

    public static void tick(World world, BlockPos pos, BlockState state, CombustionFirewoodBlockEntity blockEntity) {
        blockEntity.consumeEnergy();

        // 上方空间检查按半秒一次降频执行，避免每 tick 扫描上方 6 格
        if (blockEntity.ticksSinceSpaceCheck >= SPACE_CHECK_INTERVAL) {
            blockEntity.ticksSinceSpaceCheck = 0;
            blockEntity.checkClearSpaceAbove(world, pos);
        } else {
            blockEntity.ticksSinceSpaceCheck++;
        }
    }

    /**
     * 唯一的状态机入口：在能量或归属变化后，重新推导燃烧阶段与视觉状态，并写入方块状态。
     * 归入非首次归属的"再次添柴"标记只在满火阶段存在，燃烧降到半段或熄灭时会被清掉。
     */
    protected void refresh() {
        // 燃烧降到半段或熄灭时，清除"再次添柴"标记（并入非首次归属）
        if (round == Round.READDED && getPhase() != CombustionPhase.IGNITED) {
            round = Round.AGAIN;
        }

        if (world == null || world.isClient()) {
            return;
        }
        setCombustionState(mapVisual(getPhase(), round));
        markDirty();
    }

    // ==================== 熄灭 ====================

    /**
     * 强制熄灭当前燃烧的柴火堆
     * 根据当前燃烧阶段与归属决定熄灭后的状态
     *
     * @return 是否成功熄灭（如果已经熄灭则返回false）
     */
    public boolean extinguish() {
        if (world == null || world.isClient()) {
            return false;
        }
        if (!getCombustionState().isBurning()) {
            return false;
        }

        this.energy = 0;
        refresh();

        world.playSound(null, pos, SoundEvents.BLOCK_FIRE_EXTINGUISH, SoundCategory.BLOCKS, 0.5f, 1.0f);
        spawnExtinguishParticles(world, pos);
        return true;
    }

    // ==================== 添柴 ====================

    /**
     * 添柴操作 - 任何能量不满的状态都可以添柴
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
     * 当前燃烧阶段（由能量推导）
     */
    public CombustionPhase getPhase() {
        if (energy <= 0) {
            return CombustionPhase.EXTINGUISHED;
        }
        return energy > HALF_ENERGY ? CombustionPhase.IGNITED : CombustionPhase.HALF;
    }

    /**
     * 当前循环归属
     */
    public Round getRound() {
        return round;
    }

    /**
     * 设置循环归属
     */
    public void setRound(Round round) {
        this.round = round;
        markDirty();
    }

    /**
     * 是否处于首次燃烧循环（首次归属）
     */
    public boolean isFirstCycle() {
        return round == Round.FIRST;
    }

    /**
     * 设置是否首次循环（兼容旧 API，映射到归属档）
     */
    public void setFirstCycle(boolean firstCycle) {
        this.round = firstCycle ? Round.FIRST : Round.AGAIN;
        markDirty();
    }

    /**
     * 设置循环次数（兼容旧 API，映射到归属档）
     */
    public void setCycleCount(int count) {
        this.round = count <= 0 ? Round.FIRST : Round.AGAIN;
        markDirty();
    }

    /**
     * 当前热量等级（由能量推导）：0 无热量，1 低热量，2 高热量
     */
    public int getHeatLevel() {
        if (energy <= 0) {
            return 0;
        }
        return energy > HALF_ENERGY ? 2 : 1;
    }

    /**
     * 检查是否有热量
     */
    public boolean hasHeat() {
        return isCombusting();
    }

    /**
     * 检查是否有热量
     */
    public boolean isCombusting() {
        return getHeatLevel() > 0;
    }

    // ==================== 能量操作 ====================

    public void addEnergy(int energy) {
        this.energy = Math.min(this.energy + energy, MAX_ENERGY);
        refresh();
    }

    public boolean consumeEnergy() {
        return consumeEnergy(1);
    }

    /**
     * 消耗指定数量的能量
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
            refresh();
            return true;
        }
        return false;
    }

    public void setEnergy(int energy) {
        this.energy = Math.min(energy, MAX_ENERGY);
        refresh();
    }

    // ==================== 查询与状态判定 ====================

    /**
     * 检查是否可以添柴
     */
    public boolean canAddFirewood() {
        return energy < MAX_ENERGY && !isCompletelyExtinguished();
    }

    /**
     * 检查能量是否已经耗尽
     *
     * @return 能量是否<=0
     */
    public boolean isEnergyDepleted() {
        return energy <= 0;
    }

    /**
     * 获取能量消耗进度（0.0到1.0）
     *
     * @return 能量消耗进度，0表示满能量，1表示能量耗尽
     */
    public float getEnergyConsumptionProgress() {
        return 1.0f - ((float) energy / MAX_ENERGY);
    }

    /**
     * 获取当前能量百分比
     */
    public float getEnergyRatio() {
        return (float) energy / MAX_ENERGY;
    }

    /**
     * 检查是否完全燃尽（不能再燃烧）
     */
    public boolean isCompletelyExtinguished() {
        CombustionFirewoodBlock.CombustionState currentState = getCombustionState();
        return (currentState == CombustionFirewoodBlock.CombustionState.FIRST_EXTINGUISHED ||
                currentState == CombustionFirewoodBlock.CombustionState.AGAIN_EXTINGUISHED) &&
                energy <= 0;
    }

    /**
     * 获取半能量值（50%）
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

    // ==================== 视觉映射 ====================

    /**
     * 由燃烧阶段与循环归属映射出当前的视觉模型状态。
     * <p>{@code FIRST} 使用首次外观；非首次归属中，{@code READDED} 仅在满火阶段表现为
     * {@code REIGNITED}，其余情况归入非首次外观。</p>
     */
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

    // ==================== 顶部空间与熄灭辅助 ====================

    /**
     * 检查上方空间，如果不满足条件则强制熄灭
     */
    private void checkClearSpaceAbove(World world, BlockPos pos) {
        if (world.isClient()) {
            return;
        }
        // 只有在燃烧状态下才需要检查
        if (!getCombustionState().isBurning()) {
            return;
        }
        if (!FirewoodBlock.hasClearSpaceAbove(world, pos)) {
            // 上方空间被阻塞，强制熄灭
            extinguishDueToObstruction(world, pos);
        }
    }

    /**
     * 由于上方阻塞而强制熄灭
     */
    private void extinguishDueToObstruction(World world, BlockPos pos) {
        this.energy = 0;
        refresh();

        // 播放特殊的阻塞熄灭音效
        world.playSound(null, pos, SoundEvents.BLOCK_FIRE_EXTINGUISH, SoundCategory.BLOCKS, 0.7f, 0.8f);
        // 生成更多的烟雾粒子，表示因阻塞而熄灭
        spawnObstructionExtinguishParticles(world, pos);
    }

    /**
     * 生成熄灭粒子效果
     */
    private void spawnExtinguishParticles(World world, BlockPos pos) {
        if (world.isClient()) {
            Random random = world.random;
            for (int i = 0; i < 5; i++) {
                double x = pos.getX() + 0.5 + (random.nextDouble() - 0.5) * 0.5;
                double y = pos.getY() + 0.3 + random.nextDouble() * 0.2;
                double z = pos.getZ() + 0.5 + (random.nextDouble() - 0.5) * 0.5;

                world.addParticle(ParticleTypes.SMOKE,
                        x, y, z,
                        (random.nextDouble() - 0.5) * 0.05,
                        0.05,
                        (random.nextDouble() - 0.5) * 0.05);
            }
        }
    }

    /**
     * 生成因阻塞而熄灭的粒子效果
     */
    private void spawnObstructionExtinguishParticles(World world, BlockPos pos) {
        if (world.isClient()) {
            Random random = world.random;
            for (int i = 0; i < 10; i++) {
                double x = pos.getX() + 0.5 + (random.nextDouble() - 0.5) * 0.8;
                double y = pos.getY() + 0.5 + random.nextDouble() * 0.5;
                double z = pos.getZ() + 0.5 + (random.nextDouble() - 0.5) * 0.8;

                world.addParticle(ParticleTypes.SMOKE,
                        x, y, z,
                        (random.nextDouble() - 0.5) * 0.1,
                        0.05 + random.nextDouble() * 0.1,
                        (random.nextDouble() - 0.5) * 0.1);
            }
        }
    }

    // ==================== 序列化 ====================

    @Override
    public void writeNbt(NbtCompound nbt) {
        super.writeNbt(nbt);
        nbt.putInt("Energy", energy);
        nbt.putInt("Round", round.ordinal());
    }

    @Override
    public void readNbt(NbtCompound nbt) {
        super.readNbt(nbt);
        energy = nbt.getInt("Energy");
        // 优先读新字段 Round；旧存档则从 IsFirstCycle 映射归属
        if (nbt.contains("Round")) {
            int roundIndex = nbt.getInt("Round");
            if (roundIndex >= 0 && roundIndex < Round.values().length) {
                round = Round.values()[roundIndex];
            }
        } else {
            round = nbt.getBoolean("IsFirstCycle") ? Round.FIRST : Round.AGAIN;
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
