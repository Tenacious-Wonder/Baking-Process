package org.bakingprocess.block.entity;

import java.util.function.Consumer;

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
import org.bakingprocess.processing.baking.FirewoodCombustion;
import org.bakingprocess.processing.baking.HeatSource;
import org.bakingprocess.registry.ModBlockEntityTypes;
import org.jetbrains.annotations.Nullable;

/**
 * 负责烧着的柴火堆在"世界里"的那部分——
 * 每 tick 消耗能量、切换方块外观、检查上方空间、播放熄灭效果。
 * 燃烧数据与规则本身在 {@link FirewoodCombustion} 里（真实与虚拟柴火堆共用一份逻辑），
 * 本实体只管把状态变化落到世界上。
 *
 * <p>烤架内部的"虚拟柴火堆"也复用本实体：它没有真实世界（{@code world == null}），
 * 写外观这类副作用就改为更新自己内存里的状态，并把新状态交给宿主
 * （{@link #setStateChangedCallback}）显示维护。</p>
 */
public class CombustionFirewoodBlockEntity extends BlockEntity implements HeatSource {
    /** 上方空间检查间隔：10 tick（半秒） */
    static final int SPACE_CHECK_INTERVAL = 10;

    /** 燃烧状态机（纯逻辑，真实与虚拟柴火堆共用） */
    private final FirewoodCombustion combustion;
    /** 距上次上方空间检查经过的 tick 数（按半秒一次降频，避免每 tick 扫描上方 6 格） */
    protected int ticksSinceSpaceCheck;
    /** 虚拟模式下的宿主回调（由烤架等虚拟宿主注入）：把更新后的柴火堆方块状态交给宿主显示维护 */
    @Nullable
    private Consumer<BlockState> stateChangedCallback;

    public CombustionFirewoodBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntityTypes.COMBUSTION_FIREWOOD, pos, state);
        this.combustion = new FirewoodCombustion(this::refresh);
        // 首次点燃（或默认状态）时设置满能量与首次归属
        if (getCombustionState() == CombustionFirewoodBlock.CombustionState.FIRST_IGNITED) {
            this.combustion.setEnergy(FirewoodCombustion.MAX_ENERGY);
            this.combustion.setRound(FirewoodCombustion.Round.FIRST);
        }
    }

    // ==================== 燃烧状态读写 ====================

    /**
     * 读取当前燃烧状态（从方块状态属性读取；虚拟场景下为缓存的推导状态）。
     */
    protected CombustionFirewoodBlock.CombustionState getCombustionState() {
        return getCachedState().get(CombustionFirewoodBlock.COMBUSTION_STATE);
    }

    /**
     * 写入燃烧状态。
     */
    protected void setCombustionState(CombustionFirewoodBlock.CombustionState state) {
        if (world == null) {
            // 虚拟柴火堆：更新自身缓存状态，并把新状态交给宿主显示维护
            BlockState newState = getCachedState().with(CombustionFirewoodBlock.COMBUSTION_STATE, state);
            setCachedState(newState);
            onStateChanged(newState);
            return;
        }
        if (!world.isClient()) {
            world.setBlockState(pos, getCachedState().with(CombustionFirewoodBlock.COMBUSTION_STATE, state));
        }
    }

    /**
     * 状态变化通知：真实柴火堆标记存档；虚拟柴火堆把新状态转发给宿主回调。
     */
    protected void onStateChanged(BlockState state) {
        if (world != null) {
            markDirty();
        } else if (stateChangedCallback != null) {
            stateChangedCallback.accept(state);
        }
    }

    /**
     * 设置虚拟模式下的宿主回调（真实柴火堆无需设置）：宿主据此显示维护柴火堆外观。
     */
    public void setStateChangedCallback(@Nullable Consumer<BlockState> stateChangedCallback) {
        this.stateChangedCallback = stateChangedCallback;
    }

    // ==================== 燃烧推进 ====================

    public static void tick(World world, BlockPos pos, BlockState state, CombustionFirewoodBlockEntity blockEntity) {
        if (world == null) {
            return;
        }
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
     * 状态机重推入口：在能量或归属变化后，重新推导视觉状态并写入方块状态。
     * 由 {@link FirewoodCombustion} 的 onChange 回调驱动，也可由持有方在恢复存档后显式调用。
     */
    protected void refresh() {
        setCombustionState(combustion.refresh());
        markDirty();
    }

    // ==================== 熄灭 ====================

    /**
     * 强制熄灭当前燃烧的柴火堆。
     *
     * @return 是否成功熄灭（如果已经熄灭则返回 false）
     */
    public boolean extinguish() {
        if (world != null && world.isClient()) {
            return false;
        }
        if (!getCombustionState().isBurning()) {
            return false;
        }

        combustion.setEnergy(0);

        // 表现副作用：真实柴火堆直接播放；虚拟柴火堆由宿主自行处理
        if (world != null) {
            world.playSound(null, pos, SoundEvents.BLOCK_FIRE_EXTINGUISH, SoundCategory.BLOCKS, 0.5f, 1.0f);
            spawnExtinguishParticles(world, pos);
        }
        return true;
    }

    // ==================== 状态推导（委托） ====================

    /**
     * 添柴操作（委托燃烧状态机）：任何能量不满且未完全燃尽的状态都可以添柴。
     *
     * @return 是否成功添柴
     */
    public boolean addFirewood() {
        return combustion.addFirewood();
    }

    /**
     * 当前燃烧阶段（由能量推导）。
     */
    public FirewoodCombustion.CombustionPhase getPhase() {
        return combustion.getPhase();
    }

    /**
     * 当前循环归属。
     */
    public FirewoodCombustion.Round getRound() {
        return combustion.getRound();
    }

    /**
     * 设置循环归属。
     */
    public void setRound(FirewoodCombustion.Round round) {
        combustion.setRound(round);
        markDirty();
    }

    /**
     * 是否处于首次燃烧循环（首次归属）。
     */
    public boolean isFirstCycle() {
        return combustion.isFirstCycle();
    }

    /**
     * 设置是否首次循环（兼容旧 API，映射到归属档）。
     */
    public void setFirstCycle(boolean firstCycle) {
        combustion.setFirstCycle(firstCycle);
        markDirty();
    }

    /**
     * 设置循环次数（兼容旧 API，映射到归属档）。
     */
    public void setCycleCount(int count) {
        combustion.setCycleCount(count);
        markDirty();
    }

    /**
     * 当前热量等级（由能量推导）：0 无热量，1 低热量，2 高热量。
     */
    @Override
    public int getHeatLevel() {
        return combustion.getHeatLevel();
    }

    /**
     * 检查是否有热量。
     */
    public boolean hasHeat() {
        return combustion.hasHeat();
    }

    /**
     * 检查是否有热量。
     */
    @Override
    public boolean isCombusting() {
        return combustion.isCombusting();
    }

    // ==================== 能量操作（委托） ====================

    public void addEnergy(int energy) {
        combustion.addEnergy(energy);
    }

    public boolean consumeEnergy() {
        return combustion.consumeEnergy();
    }

    /**
     * 消耗指定数量的能量。
     *
     * @param amount 消耗的能量数量
     * @return 是否成功消耗了能量
     */
    public boolean consumeEnergy(int amount) {
        return combustion.consumeEnergy(amount);
    }

    public void setEnergy(int energy) {
        combustion.setEnergy(energy);
    }

    // ==================== 查询与状态判定（委托） ====================

    /**
     * 检查是否可以添柴。
     */
    public boolean canAddFirewood() {
        return combustion.canAddFirewood();
    }

    /**
     * 检查能量是否已经耗尽。
     */
    public boolean isEnergyDepleted() {
        return combustion.isEnergyDepleted();
    }

    /**
     * 获取能量消耗进度（0.0 到 1.0）：0 表示满能量，1 表示能量耗尽。
     */
    public float getEnergyConsumptionProgress() {
        return combustion.getEnergyConsumptionProgress();
    }

    /**
     * 获取当前能量百分比。
     */
    public float getEnergyRatio() {
        return combustion.getEnergyRatio();
    }

    /**
     * 检查是否完全燃尽（能量耗尽即燃尽，不能再燃烧）。
     */
    public boolean isCompletelyExtinguished() {
        return combustion.isCompletelyExtinguished();
    }

    /**
     * 获取半能量值（50%）。
     */
    public int getHalfEnergy() {
        return combustion.getHalfEnergy();
    }

    public int getEnergy() {
        return combustion.getEnergy();
    }

    public static int getMaxEnergy() {
        return FirewoodCombustion.getMaxEnergy();
    }

    // ==================== 顶部空间与熄灭辅助 ====================

    /**
     * 检查上方空间，如果不满足条件则强制熄灭。
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
     * 由于上方阻塞而强制熄灭。
     */
    private void extinguishDueToObstruction(World world, BlockPos pos) {
        combustion.setEnergy(0);

        // 播放特殊的阻塞熄灭音效
        world.playSound(null, pos, SoundEvents.BLOCK_FIRE_EXTINGUISH, SoundCategory.BLOCKS, 0.7f, 0.8f);
        // 生成更多的烟雾粒子，表示因阻塞而熄灭
        spawnObstructionExtinguishParticles(world, pos);
    }

    /**
     * 生成熄灭粒子效果。
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
     * 生成因阻塞而熄灭的粒子效果。
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
        combustion.writeNbt(nbt);
    }

    @Override
    public void readNbt(NbtCompound nbt) {
        super.readNbt(nbt);
        combustion.readNbt(nbt);
    }
}
