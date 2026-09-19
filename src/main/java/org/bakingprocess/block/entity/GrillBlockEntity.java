package org.bakingprocess.block.entity;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.listener.ClientPlayPacketListener;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.play.BlockEntityUpdateS2CPacket;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;
import org.bakingprocess.block.BasePlatableBlock;
import org.bakingprocess.block.CombustionFirewoodBlock;
import org.bakingprocess.block.FirewoodBlock;
import org.bakingprocess.block.GrillBlock;
import org.bakingprocess.processing.baking.BakingSpeed;
import org.bakingprocess.processing.baking.FirewoodCombustion;
import org.bakingprocess.recipe.StoveRecipe;
import org.bakingprocess.registry.ModBlockEntityTypes;
import org.bakingprocess.registry.ModBlocks;
import org.bakingprocess.registry.ModItems;
import org.jetbrains.annotations.Nullable;

public class GrillBlockEntity extends BlockEntity {
    /** 烤架下柴火的最大堆叠次数 */
    public static final int MAX_FIREWOOD = 2;

    /** 客户端燃烧表现（粒子/声音）的驱动间隔：8 tick */
    private static final int DISPLAY_TICK_INTERVAL = 8;

    private static final String FIREWOOD_COUNT_KEY = "firewood_count";
    private static final String FIREWOOD_PILE_KEY = "firewood_pile";
    private static final String BAKING_TIME_KEY = "baking_time";
    private static final String BAKING_TIME_TOTAL_KEY = "baking_time_total";

    /** 柴火堆叠数量（0 ~ {@link #MAX_FIREWOOD}），未点燃阶段的柴火存量 */
    private int firewoodCount = 0;

    /** 虚拟燃烧柴火堆：点燃后创建（满能量），委托其能量 / 热量逻辑；未点燃为 {@code null} */
    @Nullable
    private CombustionFirewoodBlockEntity firewoodPile;

    /** 虚拟燃烧柴火堆的显示方块状态（外观阶段 + 朝向），随柴火堆状态变化同步维护；未点燃为 {@code null} */
    @Nullable
    private BlockState firewoodPileState;

    /** 盘内菜肴的烘烤进度（仅当上方是可烤容器时推进；烤架自己持有的进度，见 {@link #drivePlateBaking}） */
    private int bakingTime;
    private int bakingTimeTotal;

    /** 客户端燃烧表现驱动计数器（降频调用 randomDisplayTick） */
    private int ticksSinceDisplayTick;

    public GrillBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntityTypes.GRILL, pos, state);
    }

    // ==================== tick ====================

    public static void tick(World world, BlockPos pos, BlockState state, GrillBlockEntity blockEntity) {
        if (world.isClient) {
            blockEntity.clientTick(world, pos);
            return;
        }
        blockEntity.serverTick(world, pos, state);
    }

    /**
     * 服务端每 tick：烧柴、按火力同步发光、把热量喂给上方的耐热石板。
     */
    private void serverTick(World world, BlockPos pos, BlockState state) {
        // 烧：让虚拟柴火堆消耗能量（外观变化由它自己的回调驱动）
        if (firewoodPile != null) {
            firewoodPile.consumeEnergy(1);
        }

        // 发光：有火在烧就点亮 LIT，否则关掉
        boolean lit = firewoodPile != null && firewoodPile.isCombusting();
        if (state.get(GrillBlock.LIT) != lit) {
            world.setBlockState(pos, state.with(GrillBlock.LIT, lit), Block.NOTIFY_ALL);
        }

        // 烘烤：按上方对象分发（耐热石板 / 可烤盘子）
        driveBaking(world);
    }

    /**
     * 客户端每 tick：降频驱动虚拟燃烧柴火堆的粒子 / 声音表现（直接调用方块的表现方法）。
     */
    private void clientTick(World world, BlockPos pos) {
        if (++ticksSinceDisplayTick < DISPLAY_TICK_INTERVAL) {
            return;
        }
        ticksSinceDisplayTick = 0;
        if (firewoodPile != null && firewoodPile.isCombusting() && firewoodPileState != null) {
            firewoodPileState.getBlock().randomDisplayTick(firewoodPileState, world, pos, world.random);
        }
    }

    // ==================== 烘烤驱动 ====================

    /**
     * 按上方对象分发烘烤：上方是耐热石板时只当热源驱动石板自身的烘烤器；
     * 上方是可摆盘容器（盖盖的盘子）时用烤架自己的进度烘烤盘内菜肴；
     * 上方无对象（或对象被换走）时重置盘内烘烤进度。
     */
    private void driveBaking(World world) {
        BlockEntity above = world.getBlockEntity(pos.up());
        if (above instanceof HeatResistantSlateBlockEntity slate) {
            // 石板模式与盘内烘烤互斥：进度跟随当前上方对象，换对象即重置
            resetBaking();
            driveSlateBaking(slate);
        } else if (above instanceof BasePlatableBlockEntity plate) {
            drivePlateBaking(world, plate);
        } else {
            resetBaking();
        }
    }

    /**
     * 驱动上方耐热石板的烘烤：虚拟柴火堆燃烧时，按柴火热量推进上方石板的烘烤进度；
     * 无热量时不调用（石板会在宽限后自动重置进度）。
     */
    private void driveSlateBaking(HeatResistantSlateBlockEntity slate) {
        if (firewoodPile != null && firewoodPile.isCombusting()) {
            // 单热量源速度语义与耐热石板自身炉子模式一致（BakingSpeed.forHeatSource）
            slate.advanceBaking(world, BakingSpeed.forHeatSource(firewoodPile));
        }
    }

    /**
     * 烘烤上方的盖盖盘子：先经炉子配方认证盘内菜肴（决定"能否烤、烤多久"），
     * 认证通过且有火时推进烤架自己的进度，进度满时由配方产生烘烤步骤应用到盘内菜肴。
     */
    private void drivePlateBaking(World world, BasePlatableBlockEntity plate) {
        // 盖盖是烤盘的前提：未盖盖时容器会拒绝加工步骤（摆盘流程仍活动）
        if (!plate.getCachedState().get(BasePlatableBlock.IS_COVERED)) {
            resetBaking();
            return;
        }

        // 配方认证：这道菜是否被某个炉子配方认证（能否烤、烤多久由配方决定）
        StoveRecipe recipe = StoveRecipe.getFirstDishMatch(plate, world).orElse(null);
        if (recipe == null) {
            resetBaking();
            return;
        }

        // 无火：重置进度（与石板一致：热源中断即重置）
        if (firewoodPile == null || !firewoodPile.isCombusting()) {
            resetBaking();
            return;
        }

        // 首次推进时初始化总时间（配方权威时长）
        if (bakingTimeTotal == 0) {
            bakingTimeTotal = recipe.getBakingTime();
        }

        bakingTime += BakingSpeed.forHeatSource(firewoodPile);

        if (bakingTime >= bakingTimeTotal) {
            // 认证产出：烘烤步骤只能由配方产生并应用
            if (recipe.applyCulinaryOutput(plate)) {
                playBakedSound();
            }
            resetBaking(); // 归零并同步，客户端随之停止烘烤表现
            return;
        }

        // 推进期间每 tick 同步进度（与耐热石板烘烤期间一致），客户端 isBaking 据此驱动表现
        markDirtyAndSync();
    }

    /**
     * 重置盘内菜肴的烘烤进度（无进度时不做任何事，避免无谓的存档标记）。
     * 从"在烤"归零时同步客户端：盘子被拿走、火灭等停止场景都要让烘烤表现随之停止。
     */
    private void resetBaking() {
        if (bakingTime == 0 && bakingTimeTotal == 0) {
            return;
        }
        bakingTime = 0;
        bakingTimeTotal = 0;
        markDirtyAndSync();
    }

    /**
     * 标记存档并同步到客户端（方块实体的进度数据更新时调用；与耐热石板的同步方式一致）。
     */
    private void markDirtyAndSync() {
        markDirty();
        if (world != null && !world.isClient) {
            world.updateListeners(pos, getCachedState(), getCachedState(), 3);
        }
    }

    /**
     * 完成烘烤的表现：播放火苗噼啪声。
     */
    private void playBakedSound() {
        if (world != null && !world.isClient) {
            world.playSound(null, pos, SoundEvents.BLOCK_FURNACE_FIRE_CRACKLE, SoundCategory.BLOCKS, 0.5f, 1.0f);
        }
    }

    /**
     * 当前是否在烘烤盘内菜肴（有进度）。
     */
    public boolean isBaking() {
        return bakingTime > 0;
    }

    public int getBakingTime() {
        return bakingTime;
    }

    public int getBakingTimeTotal() {
        return bakingTimeTotal;
    }

    // ==================== 柴火管理 ====================

    /**
     * 放置柴火：像正常堆叠一样堆叠数量 +1（最多 {@link #MAX_FIREWOOD} 次）。
     * 未点燃阶段仅累加数量，不创建虚拟柴火堆。
     *
     * @return 是否成功放置
     */
    public boolean addFirewood() {
        if (firewoodCount >= MAX_FIREWOOD) {
            return false;
        }
        firewoodCount++;
        markDirty();
        return true;
    }

    /**
     * 当前柴火堆叠数量（未点燃阶段的柴火存量）。
     */
    public int getFirewoodCount() {
        return firewoodCount;
    }

    /**
     * 取出柴火：堆叠数量 -1（未点燃阶段），物品发放由调用方（柴火堆方块）负责。
     *
     * @return 是否成功取出
     */
    public boolean removeFirewood() {
        if (firewoodCount <= 0) {
            return false;
        }
        firewoodCount--;
        markDirty();
        return true;
    }

    /**
     * 获取烤架当前维护的柴火堆方块状态：未点燃时为对应堆叠数量的柴火方块状态，
     * 点燃 / 灰烬时为虚拟燃烧柴火堆当前推导出的燃烧状态；
     * 烤架空置（没放柴也没点燃）时返回 {@code null}，表示没有正在维护的柴火堆。
     *
     * @return 对应柴火堆的方块状态；烤架空置时为 {@code null}
     */
    @Nullable
    public BlockState getFirewoodState() {
        if (firewoodPile != null) {
            return firewoodPileState;
        }
        if (firewoodCount <= 0) {
            return null;
        }

        FirewoodBlock firewoodBlock = (FirewoodBlock) ModBlocks.FIREWOOD;
        return firewoodBlock.getDefaultState()
                .with(FirewoodBlock.FACING, getCachedState().get(GrillBlock.FACING))
                .with(firewoodBlock.NUMBER_OF_FOOD, firewoodCount);
    }

    /**
     * 是否已可点燃（堆满 {@link #MAX_FIREWOOD} 且当前无虚拟柴火堆）。
     */
    public boolean canIgnite() {
        return firewoodCount >= MAX_FIREWOOD && firewoodPile == null;
    }

    /**
     * 点燃烤架：创建满能量的虚拟燃烧柴火堆，后续燃烧由 {@link #serverTick} 驱动。
     *
     * @return 是否成功点燃
     */
    public boolean ignite() {
        if (!canIgnite()) {
            return false;
        }
        firewoodPile = createFirewoodPile();
        markDirty();
        return true;
    }

    /**
     * 获取烤架内的虚拟燃烧柴火堆。
     *
     * @return 点燃后创建；未点燃为 {@code null}
     */
    @Nullable
    public CombustionFirewoodBlockEntity getFirewoodPile() {
        return firewoodPile;
    }

    /**
     * 当前是否处于灰烬态（虚拟柴火堆已燃尽）。
     */
    public boolean isAsh() {
        return firewoodPile != null
                && firewoodPile.getPhase() == FirewoodCombustion.CombustionPhase.EXTINGUISHED;
    }

    /**
     * 移除烤架当前维护的柴火堆，烤架回到空状态。
     */
    public void removeFirewoodPile() {
        firewoodPile = null;
        firewoodPileState = null;
        firewoodCount = 0;
        markDirty();
    }

    /**
     * 创建虚拟燃烧柴火堆：以首次点燃外观（带烤架朝向）构造，并把外观变化回调接到
     * {@link #onFirewoodPileStateChanged}，由烤架显示维护它的方块状态。
     */
    private CombustionFirewoodBlockEntity createFirewoodPile() {
        BlockState initialState = ModBlocks.COMBUSTION_FIREWOOD.getDefaultState()
                .with(CombustionFirewoodBlock.HORIZONTAL_FACING, getCachedState().get(GrillBlock.FACING));
        CombustionFirewoodBlockEntity pile = new CombustionFirewoodBlockEntity(pos, initialState);
        firewoodPileState = initialState;
        pile.setStateChangedCallback(this::onFirewoodPileStateChanged);
        return pile;
    }

    /**
     * 虚拟柴火堆外观变化回调：更新烤架显示维护的状态并标记存档。
     * 燃烧阶段变化时还要同步客户端——客户端不自行燃烧，添柴 / 烧到半段 / 燃尽等
     * 外观变化都只能靠服务端这里推送。
     */
    private void onFirewoodPileStateChanged(BlockState state) {
        boolean stageChanged = firewoodPileState == null
                || firewoodPileState.get(CombustionFirewoodBlock.COMBUSTION_STATE)
                != state.get(CombustionFirewoodBlock.COMBUSTION_STATE);
        firewoodPileState = state;
        markDirty();
        if (stageChanged && world != null && !world.isClient) {
            ((ServerWorld) world).getChunkManager().markForUpdate(pos);
        }
    }

    // ==================== 释放（烤架被移除时） ====================

    /**
     * 烤架被拆掉时，把里面那堆柴火转成真实方块放回原位置。
     */
    public void releaseFirewood(World world, BlockPos pos, Direction facing) {
        if (firewoodPile != null) {
            // 点燃 / 灰烬：放出对应外观的燃烧柴火堆
            BlockState target = ModBlocks.COMBUSTION_FIREWOOD.getDefaultState()
                    .with(CombustionFirewoodBlock.HORIZONTAL_FACING, facing)
                    .with(CombustionFirewoodBlock.COMBUSTION_STATE, firewoodPile.getCombustionState());
            // 先移除自身方块实体再放置，避免原版方块实体管理误删刚放出的实体
            world.removeBlockEntity(pos);
            if (world.setBlockState(pos, target, Block.NOTIFY_ALL)) {
                // 把虚拟柴火堆的能量与归属搬给真实实体，并按能量重推外观
                if (world.getBlockEntity(pos) instanceof CombustionFirewoodBlockEntity real) {
                    real.readNbt(firewoodPile.createNbt());
                    real.refresh();
                }
            } else {
                // 位置放不下：只掉灰烬（2 木炭）作回退
                dropStack(world, pos, new ItemStack(Items.CHARCOAL, 2));
            }
            return;
        }
        if (firewoodCount > 0) {
            // 未点燃：放出堆了对应数量的柴火方块
            FirewoodBlock firewoodBlock = (FirewoodBlock) ModBlocks.FIREWOOD;
            BlockState target = firewoodBlock.getDefaultState()
                    .with(FirewoodBlock.FACING, facing)
                    .with(firewoodBlock.NUMBER_OF_FOOD, firewoodCount);
            world.removeBlockEntity(pos);
            if (!world.setBlockState(pos, target, Block.NOTIFY_ALL)) {
                // 位置放不下：直接掉柴火物品
                dropStack(world, pos, new ItemStack(ModItems.FIREWOOD, firewoodCount));
            }
        }
    }

    /**
     * 把物品以实体形式掉落在指定位置（放置失败的回退）。
     */
    private static void dropStack(World world, BlockPos pos, ItemStack stack) {
        if (!stack.isEmpty()) {
            ItemEntity itemEntity = new ItemEntity(world, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, stack);
            itemEntity.setToDefaultPickupDelay();
            world.spawnEntity(itemEntity);
        }
    }

    // ==================== NBT ====================

    @Override
    public void writeNbt(NbtCompound nbt) {
        super.writeNbt(nbt);
        nbt.putInt(FIREWOOD_COUNT_KEY, firewoodCount);
        if (firewoodPile != null) {
            nbt.put(FIREWOOD_PILE_KEY, firewoodPile.createNbt());
        }
        // 保存盘内菜肴的烘烤进度
        nbt.putInt(BAKING_TIME_KEY, bakingTime);
        nbt.putInt(BAKING_TIME_TOTAL_KEY, bakingTimeTotal);
    }

    @Override
    public void readNbt(NbtCompound nbt) {
        super.readNbt(nbt);
        firewoodCount = nbt.getInt(FIREWOOD_COUNT_KEY);
        NbtCompound pileNbt = nbt.getCompound(FIREWOOD_PILE_KEY);
        if (!pileNbt.isEmpty()) {
            firewoodPile = createFirewoodPile();
            firewoodPile.readNbt(pileNbt);
            // 重推视觉状态：cachedState 不序列化，重启后需从能量/归属重新推导
            firewoodPile.refresh();
        } else {
            firewoodPile = null;
        }
        // 读取盘内菜肴的烘烤进度
        bakingTime = nbt.getInt(BAKING_TIME_KEY);
        bakingTimeTotal = nbt.getInt(BAKING_TIME_TOTAL_KEY);
    }

    @Override
    public NbtCompound toInitialChunkDataNbt() {
        return this.createNbt();
    }

    @Override
    public Packet<ClientPlayPacketListener> toUpdatePacket() {
        return BlockEntityUpdateS2CPacket.create(this);
    }
}
