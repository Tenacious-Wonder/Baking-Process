package org.bakingprocess.block.entity;

import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.network.listener.ClientPlayPacketListener;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.play.BlockEntityUpdateS2CPacket;
import net.minecraft.registry.Registries;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.bakingprocess.block.PlateBlock;
import org.bakingprocess.block.process.EatDishesProcess;
import org.bakingprocess.block.process.PlatingProcess;
import org.bakingprocess.culinary.Culinary;
import org.bakingprocess.culinary.CulinaryHandle;
import org.bakingprocess.culinary.step.PlatingStep;
import org.bakingprocess.culinary.step.ProcessingStep;
import org.bakingprocess.recipe.PlatingRecipe;
import org.bakingprocess.registry.ModBlockEntityTypes;
import org.bakingprocess.registry.ModItems;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * <h1>盘子方块实体</h1>
 * <p>可摆盘方块的标准实现，同时是菜肴容器（{@link ServingVessel}）。</p>
 *
 * <h2>菜与流程的状态关系</h2>
 * <ul>
 *     <li><b>摆盘流程活动</b>（未盖盖）：菜肴按是否完全匹配配方动态推导（{@link #getCulinary()}）；</li>
 *     <li><b>成品态</b>（盖盖）：菜肴固化在字段中（含已吃口数），流程不活动；</li>
 *     <li><b>揭盖还原</b>：仅当菜肴只含一个摆盘步骤时还原为流程，否则菜保留、露着可继续吃。</li>
 * </ul>
 */
public class PlateBlockEntity extends BlockEntity implements PlatableBlockEntity {
    private static final String CULINARY_KEY = "culinary";

    /** 摆盘流程（负责操作序列、候选配方与配方匹配） */
    private final PlatingProcess<PlateBlockEntity> platingProcess;
    /** 食用流程 */
    private final EatDishesProcess<PlateBlockEntity> eatProcess;
    /** 对内部真实菜肴的操作句柄（容器认可语义在此实现） */
    private final CulinaryHandle handle = new CulinaryHandle() {
        @Override
        public int getEatenCount() {
            return culinary != null ? culinary.getEatenCount() : 0;
        }

        @Override
        public boolean isConsumed() {
            return culinary != null && culinary.isConsumed();
        }

        @Override
        public int getRemainingEats() {
            return culinary != null ? culinary.getRemainingEats() : 0;
        }

        @Override
        public boolean isEdible() {
            return culinary != null && culinary.isEdible();
        }

        @Override
        public int getTotalEats() {
            return culinary != null ? culinary.getTotalEats() : 0;
        }

        @Override
        public boolean applyStep(ProcessingStep step) {
            // 摆盘流程活动时不允许加工（此时菜还在动态推导中）
            if (platingProcess.isActive() || culinary == null) {
                return false;
            }
            culinary.addStep(step);
            markDirty();
            return true;
        }

        @Override
        public boolean eat(PlayerEntity player, World world) {
            return culinary != null && culinary.eat(player, world, PlateBlockEntity.this);
        }
    };
    /** 成品态固化的菜肴 */
    @Nullable
    private Culinary culinary;

    public PlateBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntityTypes.PLATE, pos, state);
        this.eatProcess = new EatDishesProcess<>();
        this.platingProcess = new PlatingProcess<>();
    }

    // ==================== ServingVessel 实现 ====================

    @Override
    public Identifier getContainerId() {
        return Registries.ITEM.getId(getCachedState().getBlock().asItem());
    }

    /**
     * 当前菜肴：摆盘流程活动时按是否有完全匹配的配方动态推导，否则返回固化菜肴的拷贝。
     */
    @Override
    @Nullable
    public Culinary getCulinary() {
        Culinary current = resolveCulinary();
        return current != null ? current.copy() : null;
    }

    /**
     * 内部真实菜肴（不做拷贝）；供容器自身操作使用。
     */
    @Nullable
    private Culinary resolveCulinary() {
        if (platingProcess.isActive()) {
            PlatingRecipe recipe = platingProcess.getMatchedRecipe();
            if (recipe != null) {
                return Culinary.create().addStep(recipe.createStep());
            }
            return null;
        }
        return culinary;
    }

    @Override
    public boolean tryAddCulinary(Culinary culinary) {
        this.culinary = culinary;
        platingProcess.reset();
        platingProcess.clearPerformedActions();
        markDirty();
        return true;
    }

    @Override
    public void clearCulinary() {
        this.culinary = null;
        platingProcess.reset();
        platingProcess.clearPerformedActions();
        markDirty();
    }

    @Override
    public CulinaryHandle getCulinaryHandle() {
        return handle;
    }

    // ==================== 盖子相关方法 ====================

    /**
     * 尝试盖上盖子：把当前菜肴（动态或固化）固化为成品态并关闭流程。
     *
     * @return 是否成功盖上盖子
     */
    public boolean coverWithLid() {
        Culinary current = resolveCulinary();
        if (current == null || world == null) {
            return false;
        }

        this.culinary = current;
        platingProcess.reset();
        platingProcess.clearPerformedActions();
        boolean covered = world.setBlockState(pos, getCachedState().with(PlateBlock.IS_COVERED, true));
        markDirty();
        return covered;
    }

    /**
     * 取下盖子并尝试把成品菜还原为进行中的摆盘。
     *
     * <p>仅当固化菜肴恰好只含一个摆盘步骤（未烘烤等二次加工）时才能还原：
     * 用该步骤的操作序列恢复流程，菜肴转回动态推导；否则菜保留，可露着继续吃。</p>
     */
    public boolean removeCoverAndRestore() {
        if (world == null) {
            return false;
        }

        BlockState currentState = getCachedState();
        if (!currentState.get(PlateBlock.IS_COVERED)) {
            return false;
        }

        // 取下盖子
        BlockState newState = currentState.with(PlateBlock.IS_COVERED, false);
        boolean coverRemoved = world.setBlockState(pos, newState, 3);
        if (!coverRemoved) {
            return false;
        }

        // 尝试还原为摆盘流程
        if (culinary != null) {
            List<ProcessingStep> steps = culinary.getSteps();
            if (steps.size() == 1 && steps.get(0) instanceof PlatingStep plating) {
                this.culinary = null;
                platingProcess.restoreActions(plating.getActions());
                platingProcess.start(world, this);
                platingProcess.restoreCandidates(world, this);
            }
        }

        world.playSound(null, pos, SoundEvents.BLOCK_METAL_PLACE, SoundCategory.BLOCKS, 0.5f, 1.2f);
        markDirty();
        return true;
    }

    /**
     * 设置方块实体所属的世界。
     *
     * <p>重写此方法以在 {@code readNbt} 之后、世界可用时恢复摆盘流程：
     * 游戏重启后由 {@link PlatingProcess#restoreAfterWorldSet} 按已持久化的
     * 操作序列恢复候选配方与精确匹配。客户端不需要恢复（渲染按操作序列取模型）。</p>
     */
    @Override
    public void setWorld(World world) {
        super.setWorld(world);
        if (world != null && !world.isClient) {
            platingProcess.restoreAfterWorldSet(world, this);
        }
    }

    // ==================== 交互方法 ====================

    /**
     * 尝试摆盘。
     */
    public ActionResult tryPlating(PlayerEntity player, Hand hand, BlockHitResult hit) {
        // 检查是否满足摆盘条件
        if (eatProcess.isActive() || resolveCulinary() != null || getCachedState().get(PlateBlock.IS_COVERED)) {
            return ActionResult.PASS;
        }

        if (!platingProcess.isActive()) {
            platingProcess.start(world, this);
        }

        return platingProcess.executeStep(this, getCachedState(), world, pos, player, hand, hit);
    }

    /**
     * 尝试食用。
     */
    public ActionResult tryEat(PlayerEntity player, Hand hand, BlockHitResult hit) {
        // 摆盘流程活动（未盖盖、可能还在摆）时不允许吃
        if (platingProcess.isActive() || resolveCulinary() == null) {
            return ActionResult.PASS;
        }

        if (!eatProcess.isActive()) {
            eatProcess.start(world, this);
        }

        return eatProcess.executeStep(this, getCachedState(), world, pos, player, hand, hit);
    }

    // ==================== NBT 序列化 ====================

    @Override
    public void readNbt(NbtCompound nbt) {
        super.readNbt(nbt);

        // 操作序列与流程状态由 PlatingProcess 自行序列化
        if (nbt.contains("plating_process")) {
            platingProcess.readFromNbt(nbt.getCompound("plating_process"));
        }

        if (nbt.contains("eat_process")) {
            eatProcess.readFromNbt(nbt.getCompound("eat_process"));
        }

        // 读取成品态菜肴（盖着盖子）；有菜时流程应处于关闭状态
        if (nbt.contains(CULINARY_KEY, NbtElement.COMPOUND_TYPE)) {
            this.culinary = Culinary.create().readNbt(nbt.getCompound(CULINARY_KEY));
            platingProcess.reset();
        }
    }

    @Override
    protected void writeNbt(NbtCompound nbt) {
        super.writeNbt(nbt);

        NbtCompound platingNbt = new NbtCompound();
        platingProcess.writeToNbt(platingNbt);
        nbt.put("plating_process", platingNbt);

        NbtCompound eatNbt = new NbtCompound();
        eatProcess.writeToNbt(eatNbt);
        nbt.put("eat_process", eatNbt);

        if (culinary != null) {
            NbtCompound culinaryNbt = new NbtCompound();
            culinary.writeNbt(culinaryNbt);
            nbt.put(CULINARY_KEY, culinaryNbt);
        }
    }

    // ==================== PlatableBlockEntity 接口实现 ====================

    @Override
    public boolean isCompletionItem(ItemStack stack) {
        return stack.isOf(ModItems.PLATE_LID);
    }

    @Override
    public void onPlatingComplete(World world, BlockPos pos, PlatingRecipe recipe, PlayerEntity player, Hand hand, HitResult hit) {
        // 用配方生成摆盘步骤并接纳为菜肴
        tryAddCulinary(Culinary.create().addStep(recipe.createStep()));

        // 消耗一个完成物品
        if (!player.isCreative()) {
            player.getStackInHand(hand).decrement(1);
        }

        // 盖上盖子
        if (coverWithLid()) {
            world.playSound(null, pos, SoundEvents.BLOCK_METAL_PLACE, SoundCategory.BLOCKS, 0.5f, 0.8f);
        }
    }

    // ==================== 访问器方法 ====================

    public PlatingProcess<PlateBlockEntity> getPlatingProcess() {
        return platingProcess;
    }

    public EatDishesProcess<PlateBlockEntity> getEatProcess() {
        return eatProcess;
    }

    // ==================== 网络同步 ====================

    @Override
    public @Nullable Packet<ClientPlayPacketListener> toUpdatePacket() {
        return BlockEntityUpdateS2CPacket.create(this);
    }

    @Override
    public NbtCompound toInitialChunkDataNbt() {
        return createNbt();
    }

    @Override
    public void markDirty() {
        super.markDirty();

        if (world != null) {
            world.updateListeners(pos, getCachedState(), getCachedState(), 3);
        }
    }
}
