package org.bakingprocess.block.entity;

import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.ShapeContext;
import net.minecraft.block.pattern.BlockPattern;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.inventory.Inventory;
import net.minecraft.inventory.SidedInventory;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.listener.ClientPlayPacketListener;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.play.BlockEntityUpdateS2CPacket;
import net.minecraft.recipe.*;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;
import org.bakingprocess.block.HeatResistantSlateBlock;
import org.bakingprocess.item.ModSharpKitchenwareItem;
import org.bakingprocess.processing.baking.BakingSpeed;
import org.bakingprocess.processing.baking.StoveStructure;
import org.bakingprocess.recipe.StoveRecipe;
import org.bakingprocess.registry.ModBlockEntityTypes;
import org.bakingprocess.registry.ModRecipeTypes;
import org.bakingprocess.util.BakingProcessUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.twcore.api.block.UpPlaceBlockEntity;
import org.twcore.api.blockvolume.BlockVolume;
import org.twcore.api.blockvolume.BlockVolumeRegistry;

import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * <h1>耐热石板方块实体</h1>
 * <p>
 * 承载耐热石板（炉子台面）的运行数据与逻辑。耐热石板的结构由方块体系统描述，本实体负责三类职责：
 * </p>
 * <ul>
 *   <li><b>方块体快照</b>：所属方块体（{@link #blockVolume}）在服务端结构检查时实时查询，
 *       客户端经 NBT 同步用于显示。</li>
 *   <li><b>炉子结构判定</b>：委托 {@link StoveStructure}，按固定间隔判定石板是否构成炉子
 *       并维护柴火绑定（供烘烤读取热量）。</li>
 *   <li><b>烘烤</b>：委托 {@link BakingLogic}，消耗台面物品、推进烘烤进度并产出配方结果。
 *       烘烤推进入口（{@link #advanceBaking} / {@link #resetBaking}）对外暴露，耐热石板自身
 *       tick 与外部热量源（如下方烤架）共用。</li>
 * </ul>
 *
 * <h2>烘烤驱动方式</h2>
 * <p>
 * 石板自身处于炉子模式（结构有效且有自身柴火热量）时，由本实体 tick 主动推进；石板下方是
 * 烤架等外部热量源时，结构判定不成立，由外部按自身柴火燃烧情况每 tick 调用
 * {@link #advanceBaking} 主动推进。外部停止驱动后，实体会在短暂宽限（{@value #EXTERNAL_DRIVE_WINDOW}
 * tick）后自动重置烘烤进度。
 * </p>
 */
public class HeatResistantSlateBlockEntity extends UpPlaceBlockEntity implements SidedInventory, RecipeUnlocker, RecipeInputProvider {
    protected static final int MIN_CHECK_INTERVAL = 10;
    protected static final String VOLUME_KEY = "BlockVolume";
    protected static final double INPUT_OFFSET_Y = 0.1;
    /** 外部热量源停止驱动后的宽限 tick 数：宽限内不重置进度，避免外部与石板 tick 时序错位导致误重置。 */
    protected static final int EXTERNAL_DRIVE_WINDOW = 2;

    /** 所属方块体：服务端在结构检查时实时查询；客户端为 NBT 同步的显示快照。 */
    @Nullable
    protected BlockVolume blockVolume;
    /** 炉子结构：结构判定结果与柴火绑定。服务端由结构检查维护，客户端为 NBT 同步快照。 */
    protected final StoveStructure stoveStructure = new StoveStructure();
    /** 烘烤逻辑：配方匹配、进度推进与产出。 */
    protected final BakingLogic bakingLogic;

    // ==================== 检查节流 ====================

    private int age;
    private int lastCheckTime = 0;
    /** 最近一次烘烤推进（自身或外部驱动）的 tick；初始远离当前 age，确保起步时不误判外部驱动。 */
    private int lastBakingTick = -100;

    public HeatResistantSlateBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntityTypes.HEAT_RESISTANT_SLATE, pos, state, 1);
        this.bakingLogic = new BakingLogic(
                this::markDirty,
                this::markDirtyAndSync,
                this::playBakedSound
        );
    }

    // ==================== 容器交互 ====================

    @Override
    public VoxelShape getContentShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
        return getBlockShape(this.getInventoryBlockState(), world, pos);
    }

    private VoxelShape getBlockShape(BlockState blockState, BlockView world, BlockPos pos) {
        Block block = blockState.getBlock();

        if (block == Blocks.AIR) {
            return VoxelShapes.empty();
        }

        VoxelShape shape = block.getDefaultState().getOutlineShape(world, pos);

        return shape.offset(0.0, INPUT_OFFSET_Y, 0.0);
    }

    @Override
    public boolean isValidItem(ItemStack stack) {
        return bakingLogic.isValidItem(stack, world);
    }

    @Override
    public Result tryAddItem(ItemStack stack, BlockHitResult hit) {
        if (stack.isEmpty() || !isValidItem(stack)) {
            return Result.of(ActionResult.PASS);
        }

        // 尝试放置的新堆栈
        ItemStack newStack = stack.copy();

        // 直接放置物品
        int maxCount = getMaxInputCount(newStack);
        if (isEmpty() && maxCount != 0) {
            newStack.setCount(Math.min(newStack.getCount(), maxCount));
            this.setStack(0, newStack);
            this.markDirtyAndSync();
            return Result.of(newStack, ActionResult.SUCCESS);
        }
        return Result.of(ActionResult.PASS);
    }

    @Override
    public void onPlace(BlockState state, World world, BlockPos pos, PlayerEntity player, Hand hand, BlockHitResult hit, ItemStack placeStack, List<ItemStack> itemStacks) {
        playSound(world, pos, placeStack, true);

        if (!player.isCreative()) {
            placeStack.decrement(getStack(0).getCount());
        }
    }

    @Override
    public Result tryFetchItem(PlayerEntity player, BlockHitResult hit) {
        ItemStack contentStack = this.getStack(0);

        if (contentStack.isEmpty()) {
            return Result.of(ActionResult.PASS);
        }

        // 普通物品的取出逻辑
        if (!player.isCreative() && !player.giveItemStack(contentStack)) {
            player.dropItem(contentStack, false);
        }

        this.setStack(0, ItemStack.EMPTY);

        this.markDirtyAndSync();
        return Result.of(contentStack.copy(), ActionResult.SUCCESS);
    }

    @Override
    public void onFetch(BlockState state, World world, BlockPos pos, PlayerEntity player, Hand hand, BlockHitResult hit, List<ItemStack> fetchStacks) {
        super.onFetch(state, world, pos, player, hand, hit, fetchStacks);
        ItemStack handStack = player.getStackInHand(hand);

        if (handStack.getItem() instanceof ModSharpKitchenwareItem){
            handStack.damage(1, player, e -> e.sendEquipmentBreakStatus(EquipmentSlot.MAINHAND));
        }
    }

    /**
     * 获取当前物品栏中的物品对应的方块状态。
     *
     * @return 物品对应的方块状态
     */
    public BlockState getInventoryBlockState() {
        ItemStack stack = this.inventory.get(0);
        Direction facing = Direction.EAST;

        Direction structureDirection = stoveStructure.getResultDirection();
        if (structureDirection != null){
            facing = structureDirection;
        }
        return BakingProcessUtils.createCountBlockstate(stack, facing);
    }

    /**
     * 获取该配方允许的最大输入数量。
     *
     * @param stack 要输入的物品堆栈
     * @return 该配方允许的最大输入数量；0 表示未找到匹配的配方
     */
    protected int getMaxInputCount(ItemStack stack){
        return bakingLogic.getMaxInputCount(stack, world);
    }

    // ==================== 序列化 ====================

    @Override
    public void writeNbt(NbtCompound nbt) {
        super.writeNbt(nbt);

        // 保存所属方块体快照（客户端显示用）
        if (blockVolume != null) {
            nbt.put(VOLUME_KEY, blockVolume.toNbt());
        }

        // 炉子结构状态（存档恢复与客户端同步）
        stoveStructure.writeNbt(nbt);

        // 保存烘烤进度
        nbt.putInt("BakingTime", bakingLogic.getBakingTime());
        nbt.putInt("BakingTimeTotal", bakingLogic.getBakingTimeTotal());
    }

    @Override
    public void readNbt(NbtCompound nbt) {
        super.readNbt(nbt);

        // 恢复所属方块体快照（客户端无索引，仅用于显示；服务端在结构检查时实时查询覆盖）
        if (nbt.contains(VOLUME_KEY)) {
            this.blockVolume = BlockVolume.fromNbt(nbt.getCompound(VOLUME_KEY));
        }

        stoveStructure.readNbt(nbt);

        // 读取烘烤进度
        bakingLogic.readNbt(nbt.getInt("BakingTime"), nbt.getInt("BakingTimeTotal"));
    }

    // ==================== 方块体快照 ====================

    /**
     * 获取所属方块体：服务端由结构检查实时查询并缓存，客户端为 NBT 同步的显示快照。
     */
    @Nullable
    public BlockVolume getBlockVolume() {
        return blockVolume;
    }

    /**
     * 重新查询所属方块体并同步到客户端（结构变化事件或周期检查时调用）。
     */
    public void refreshBlockVolume() {
        if (world == null || world.isClient) {
            return;
        }
        BlockVolume volume = BlockVolumeRegistry.findBlockVolume(world, pos);
        if (volume == null) {
            if (this.blockVolume != null) {
                this.blockVolume = null;
                this.markDirty();
            }
            return;
        }
        if (!volume.equals(this.blockVolume)) {
            this.blockVolume = volume;
            this.markDirty();
            world.updateListeners(pos, getCachedState(), getCachedState(), 3);
        }
    }

    @Override
    public void markRemoved() {
        super.markRemoved();
        blockVolume = null;
    }

    // ==================== 炉子结构 ====================

    /**
     * 获取对应的炉型图案。
     *
     * @param index 炉型索引
     * @return 对应的炉型图案
     */
    @Nullable
    public BlockPattern getStovePattern(int index){
        return HeatResistantSlateBlock.getStovePattern(index);
    }

    /**
     * 获取当前炉子结构检查结果。
     */
    @Nullable
    public BlockPattern.Result getCurrentStoveResult() {
        return stoveStructure.getPatternResult();
    }

    /**
     * 获取当前炉子结构类型。
     */
    public int getCurrentStoveStructureType() {
        return stoveStructure.getPatternType();
    }

    /**
     * 检查当前是否有效的炉子结构。
     */
    public boolean isStoveValid() {
        return stoveStructure.isValid();
    }

    /**
     * 获取当前炉子结构朝向。
     */
    @Nullable
    public Direction getResultDirection(){
        return stoveStructure.getResultDirection();
    }

    /**
     * 获取绑定的柴火位置集合（不可修改视图）。
     */
    public Set<BlockPos> getFirewoodPositions() {
        return stoveStructure.getFirewoodPositions();
    }

    /**
     * 注意：尝试修改此集合是没有效果的
     *
     * @return 绑定的柴火堆方块实体的集合
     */
    public Set<CombustionFirewoodBlockEntity> getFirewoodEntities() {
        return stoveStructure.getFirewoodEntities();
    }

    /**
     * 当前正在燃烧的柴火堆数量。
     */
    public int getActiveFirewoodCount() {
        return stoveStructure.getActiveFirewoodCount();
    }

    // ==================== 烘烤对外接口 ====================

    /**
     * 以指定速度推进烘烤进度（耐热石板自身 tick 与外部热量源共用入口）。
     *
     * <p>外部热量源（如下方烤架）按自身柴火燃烧情况每 tick 调用本方法主动推进烘烤；
     * 无热量时停止调用即可，实体会在宽限后自动重置进度。</p>
     *
     * @param world       当前世界
     * @param bakingSpeed 本 tick 的烘烤速度；无热量时传 0
     * @return 是否处于烘烤推进状态（容器有配方且正在累积进度）
     */
    public boolean advanceBaking(World world, int bakingSpeed) {
        this.lastBakingTick = this.age;
        return bakingLogic.advance(world, this, bakingSpeed);
    }

    /**
     * 重置烘烤进度（外部热量源可主动调用；实体自身在无任何热量时也会自动重置）。
     */
    public void resetBaking() {
        bakingLogic.reset();
    }

    /**
     * 自身结构绑定的柴火堆中是否有正在燃烧的（仅炉子模式成立时才有意义）。
     */
    public boolean hasSelfHeat(){
        for (CombustionFirewoodBlockEntity firewood : stoveStructure.getFirewoodEntities()) {
            if (firewood.isCombusting()) {
                return true;
            }
        }
        return false;
    }

    /**
     * 检查是否可以开始烘烤（有物品、自身热量、炉子结构有效且配方匹配）。
     */
    public boolean canBake() {
        return !isEmpty() && hasSelfHeat() && stoveStructure.isValid() && bakingLogic.hasMatch(this, world);
    }

    /**
     * 获取当前烘烤进度的百分比。
     *
     * @return 烘烤进度百分比（0.0 - 1.0）
     */
    public float getBakingProgress() {
        return bakingLogic.getProgress();
    }

    public int getBakingTime() {
        return bakingLogic.getBakingTime();
    }

    public int getBakingTimeTotal() {
        return bakingLogic.getBakingTimeTotal();
    }

    /**
     * 当前是否有烘烤进度（用于客户端表现，如粒子与声音）。
     */
    public boolean isBaking() {
        return bakingLogic.isBaking();
    }

    @Override
    public void provideRecipeInputs(RecipeMatcher finder) {
        for (ItemStack stack : this.inventory) {
            finder.addInput(stack);
        }
    }

    @Override
    public void setLastRecipe(@Nullable Recipe<?> recipe) {
        bakingLogic.setLastRecipe(recipe);
    }

    @Override
    public @Nullable Recipe<?> getLastRecipe() {
        return bakingLogic.getLastRecipe();
    }

    // ==================== 生命周期 ====================

    public static void tick(World world, BlockPos pos, BlockState state, @NotNull HeatResistantSlateBlockEntity blockEntity) {
        // 每tick增加方块寿命
        blockEntity.age++;
        if (blockEntity.age == Integer.MAX_VALUE) {
            blockEntity.age = 0;
        }

        // 结构检查按固定间隔降频执行
        if (blockEntity.age - blockEntity.lastCheckTime >= MIN_CHECK_INTERVAL) {
            blockEntity.checkPattern(world, pos);
            blockEntity.lastCheckTime = blockEntity.age;
        }

        // 更新绑定的柴火堆实体引用
        blockEntity.stoveStructure.updateFirewood(world);

        // 烘烤推进：自身炉子模式（结构有效且有自身热量）由本实体主动推进；
        // 否则若外部热量源（如下方烤架）正在驱动则不干预，无任何热量时重置进度
        boolean selfBaking = blockEntity.stoveStructure.isValid() && blockEntity.hasSelfHeat();
        if (selfBaking) {
            blockEntity.advanceBaking(world, blockEntity.getSelfBakingSpeed());
        } else if (!blockEntity.isExternallyDriving()) {
            blockEntity.resetBaking();
        }
    }

    /**
     * 周期结构检查：刷新方块体快照后，由主方块执行完整判定，从方块同步主方块结果。
     */
    private void checkPattern(World world, BlockPos pos) {
        if (world == null || world.isClient) {
            return;
        }

        refreshBlockVolume();

        BlockVolume volume = this.blockVolume;
        if (volume == null) {
            return;
        }

        // 非主方块：直接同步主方块的结构数据（只有主方块执行完整判定）
        if (!volume.masterPos().equals(pos)) {
            if (world.getBlockEntity(volume.masterPos()) instanceof HeatResistantSlateBlockEntity master) {
                this.stoveStructure.copyFrom(master.stoveStructure);
                markDirty();
            }
            return;
        }

        stoveStructure.check(world, volume);
        markDirty();
    }

    /**
     * 外部热量源是否仍在驱动烘烤：最近 {@value EXTERNAL_DRIVE_WINDOW} tick 内有推进调用。
     * 自身模式不成立时，该调用只能来自外部，故作为"外部驱动中"的判定依据。
     */
    private boolean isExternallyDriving() {
        return this.age - this.lastBakingTick <= EXTERNAL_DRIVE_WINDOW;
    }

    /**
     * 计算自身炉子模式下的烘烤速度：由结构绑定的柴火堆燃烧情况推导（多热量源收益递减）。
     */
    private int getSelfBakingSpeed() {
        return BakingSpeed.forHeatSources(stoveStructure.getFirewoodEntities());
    }

    /**
     * 完成烘烤的表现：播放火苗噼啪声（由 {@link BakingLogic} 完成产出后回调）。
     */
    private void playBakedSound() {
        if (world != null && !world.isClient) {
            world.playSound(null, pos, SoundEvents.BLOCK_FURNACE_FIRE_CRACKLE, SoundCategory.BLOCKS, 0.5f, 1.0f);
        }
    }

    // ==================== 网络与容器接口 ====================

    @Override
    public NbtCompound toInitialChunkDataNbt() {
        return this.createNbt();
    }

    @Override
    public Packet<ClientPlayPacketListener> toUpdatePacket() {
        return BlockEntityUpdateS2CPacket.create(this);
    }

    @Override
    public int getMaxCountPerStack() {
        return 16;
    }

    @Override
    public int[] getAvailableSlots(Direction side) {
        return new int[]{0};
    }

    @Override
    public boolean canInsert(int slot, ItemStack stack, @Nullable Direction dir) {
        return slot == 0;
    }

    @Override
    public boolean canExtract(int slot, ItemStack stack, Direction dir) {
        return slot == 0;
    }

    /**
     * <h1>烘烤逻辑</h1>
     * <p>
     * 管理耐热石板上物品的<b>烘烤进度状态机</b>：配方匹配、进度推进、产出与重置。
     * 本类不感知热量来源——烘烤速度由调用方（耐热石板自身 tick 或外部热量源如烤架）传入，
     * 它只负责把速度换算成进度，并在进度满时产出配方结果。
     * </p>
     *
     * <h2>系统构成与生命周期</h2>
     * <ul>
     *   <li><b>驱动方式</b>：每次推进由外部显式调用 {@link #advance}；调用方决定速度
     *       （无热量传 0，本类随即重置进度）。</li>
     *   <li><b>推进条件</b>：容器非空、速度为正、输入匹配炉子配方——任一不满足即重置进度。</li>
     *   <li><b>产出</b>：进度达到总时间后调用配方 {@link StoveRecipe#craft} 产出，记录配方解锁
     *       信息（{@link #recipesUsed} / {@link #lastRecipe}），并通过回调通知实体播放表现。</li>
     *   <li><b>状态回调</b>：数据变更（{@code onDirty}）、进度同步（{@code onSync}）、完成产出
     *       （{@code onBaked}）由持有方注入，本类不依赖具体方块实体。</li>
     * </ul>
     */
    public static class BakingLogic implements RecipeUnlocker {
        protected static final int MIN_BAKING_TIME = 100; // 最小烘烤时间

        // ==================== 烘烤状态 ====================

        protected int bakingTime;
        protected int bakingTimeTotal;
        protected final Object2IntOpenHashMap<Identifier> recipesUsed = new Object2IntOpenHashMap<>();
        protected final RecipeManager.MatchGetter<Inventory, ? extends StoveRecipe> matchGetter;
        @Nullable
        protected Recipe<?> lastRecipe;

        // ==================== 状态回调 ====================

        private final Runnable onDirty;
        private final Runnable onSync;
        private final Runnable onBaked;

        public BakingLogic(Runnable onDirty, Runnable onSync, Runnable onBaked) {
            this.matchGetter = RecipeManager.createCachedMatchGetter(ModRecipeTypes.STOVE);
            this.onDirty = onDirty;
            this.onSync = onSync;
            this.onBaked = onBaked;
        }

        // ==================== 推进 ====================

        /**
         * 以指定速度推进烘烤进度。
         *
         * @param world       当前世界（配方匹配与产出需要）
         * @param inventory   被烘烤的容器（通常为方块实体自身）
         * @param bakingSpeed 本 tick 的烘烤速度；无热量传 0（随即重置进度）
         * @return 是否处于烘烤推进状态（容器有配方且正在累积进度）
         */
        public boolean advance(World world, Inventory inventory, int bakingSpeed) {
            // 无物品或无热量：没有推进的前提，重置进度
            if (inventory.isEmpty() || bakingSpeed <= 0) {
                reset();
                return false;
            }

            // 获取匹配的配方；无配方（物品不合法/已烤完）时重置
            StoveRecipe recipe = this.matchGetter.getFirstMatch(inventory, world).orElse(null);
            if (recipe == null) {
                reset();
                return false;
            }

            // 首次推进时按输入数量初始化总时间
            if (bakingTimeTotal == 0) {
                bakingTimeTotal = Math.max(recipe.getBakingTimeForInput(inventory.getStack(0).getCount()), MIN_BAKING_TIME);
            }

            bakingTime += bakingSpeed;

            if (bakingTime >= bakingTimeTotal) {
                completeBaking(world, inventory, recipe);
            }

            // 进度变化同步到客户端（渲染进度）
            onSync.run();
            return true;
        }

        /**
         * 完成烘烤：产出配方结果并记录配方解锁信息。
         */
        private void completeBaking(World world, Inventory inventory, StoveRecipe recipe) {
            ItemStack inputStack = inventory.getStack(0);
            ItemStack outputStack = recipe.craft(inventory, world.getRegistryManager());

            if (inputStack.isEmpty() || outputStack.isEmpty()) {
                reset();
                return;
            }

            // 消耗输入物品
            inventory.setStack(0, outputStack);

            setLastRecipe(recipe);
            recipesUsed.addTo(recipe.getId(), 1);

            reset();
            onBaked.run();
        }

        /**
         * 重置烘烤进度（无进度时不做任何事，避免无谓的存档标记）。
         */
        public void reset() {
            if (bakingTime == 0 && bakingTimeTotal == 0) {
                return;
            }
            this.bakingTime = 0;
            this.bakingTimeTotal = 0;
            onDirty.run();
        }

        // ==================== 序列化 ====================

        /**
         * 恢复烘烤进度（由持有方从 NBT 读取后传入）。
         */
        public void readNbt(int bakingTime, int bakingTimeTotal) {
            this.bakingTime = bakingTime;
            this.bakingTimeTotal = bakingTimeTotal;
        }

        // ==================== 配方校验 ====================

        /**
         * 判断物品堆栈是否能匹配某个炉子配方（放置校验）。
         */
        public boolean isValidItem(ItemStack stack, World world) {
            if (stack.isEmpty()) {
                return false;
            }
            return this.matchGetter.getFirstMatch(new SimpleInventory(stack), world).isPresent();
        }

        /**
         * 获取该配方允许的最大输入数量。
         *
         * @return 该配方允许的最大输入数量；0 表示未找到匹配的配方
         */
        public int getMaxInputCount(ItemStack stack, World world) {
            Optional<? extends StoveRecipe> expectedRecipe = this.matchGetter.getFirstMatch(new SimpleInventory(stack), world);
            return expectedRecipe.map(StoveRecipe::getMaxInputCount).orElse(0);
        }

        /**
         * 当前容器内容是否能匹配某个炉子配方。
         */
        public boolean hasMatch(Inventory inventory, World world) {
            return this.matchGetter.getFirstMatch(inventory, world).isPresent();
        }

        // ==================== 查询 ====================

        /**
         * 获取当前烘烤进度的百分比。
         *
         * @return 烘烤进度百分比（0.0 - 1.0）
         */
        public float getProgress() {
            if (bakingTimeTotal > 0) {
                return (float) bakingTime / bakingTimeTotal;
            }
            return 0.0f;
        }

        public int getBakingTime() {
            return bakingTime;
        }

        public int getBakingTimeTotal() {
            return bakingTimeTotal;
        }

        /**
         * 当前是否有烘烤进度（用于客户端表现，如粒子与声音）。
         */
        public boolean isBaking() {
            return bakingTime > 0;
        }

        public Object2IntOpenHashMap<Identifier> getRecipesUsed() {
            return recipesUsed;
        }

        @Override
        public void setLastRecipe(@Nullable Recipe<?> recipe) {
            this.lastRecipe = recipe;
        }

        @Override
        public @Nullable Recipe<?> getLastRecipe() {
            return this.lastRecipe;
        }
    }
}
