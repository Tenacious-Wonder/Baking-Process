package org.bakingprocess.block.entity;

import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.network.listener.ClientPlayPacketListener;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.play.BlockEntityUpdateS2CPacket;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.bakingprocess.BakingProcess;
import org.bakingprocess.block.PlateBlock;
import org.bakingprocess.block.process.EatDishesProcess;
import org.bakingprocess.block.process.PlatingProcess;
import org.bakingprocess.content.DishesContent;
import org.bakingprocess.recipe.PlatingRecipe;
import org.bakingprocess.registry.ModBlockEntityTypes;
import org.bakingprocess.registry.ModItems;
import org.jetbrains.annotations.Nullable;
import org.twcore.api.process.PlayerAction;
import org.twcore.content.Content;
import org.twcore.process.playeraction.PlayerActionListUtil;
import org.twcore.registry.TWRegistries;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class PlateBlockEntity extends BlockEntity implements PlatableBlockEntity {
    /** 方块库存的最大容量，同时也是摆盘流程的最大步骤数量。 */
    public static final int MAX_STEPS = 10;
    private static final String OUTCOME_KEY = "outcome";
    private static final String ACTIONS_KEY = "actions";

    /**
     * 已执行的操作列表。
     * <p>当{@linkplain #platingProcess}处于关闭状态时该列表应该为空</p>
     */
    private final List<PlayerAction> performedActions = new ArrayList<>();
    /** 摆盘流程 */
    private final PlatingProcess<PlateBlockEntity> platingProcess;
    /** 食用流程 */
    private final EatDishesProcess<PlateBlockEntity> eatProcess;
    /** 摆盘配方的最终产物 */
    @Nullable
    private DishesContent outcome;

    public PlateBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntityTypes.PLATE, pos, state);
        this.eatProcess = new EatDishesProcess<>();
        this.platingProcess = new PlatingProcess<>();
    }

    // ==================== 操作管理方法 ====================

    @Override
    public List<PlayerAction> getPerformedActions() {
        return new ArrayList<>(performedActions);
    }

    @Override
    public boolean performAction(int step, PlayerAction action) {
        if (!platingProcess.isActive()) {
            return false;
        }

        // 验证参数
        if (step < 0 || step >= MAX_STEPS || action == null) {
            return false;
        }

        // 检查步骤连续性：前面的步骤必须有操作
        for (int i = 0; i < step; i++) {
            if (i >= performedActions.size()) {
                return false;
            }
        }

        // 检查该步骤是否已有操作
        if (step < performedActions.size()) {
            return false;
        }

        // 确保列表足够大
        while (performedActions.size() < step) {
            performedActions.add(null);
        }

        // 添加操作
        performedActions.add(action);

        // 标记脏状态以保存
        markDirty();
        return true;
    }

    @Override
    public @Nullable PlayerAction removeAction(int step) {
        // 验证参数
        if (step < 0 || step >= performedActions.size()) {
            return null;
        }

        PlayerAction removed = performedActions.get(step);
        if (removed == null) {
            return null;
        }

        // 移除该步骤及之后的所有操作（保持连续性）
        while (performedActions.size() > step) {
            performedActions.remove(performedActions.size() - 1);
        }

        markDirty();
        return removed;
    }

    @Override
    public void clearPerformedActions() {
        performedActions.clear();
        markDirty();
    }

    // ==================== 盖子相关方法 ====================

    /**
     * 尝试盖上盖子，只有当盘子内拥有完整的菜肴时才会成功。
     * @return 是否成功盖上盖子
     */
    public boolean coverWithLid() {
        if (outcome == null || world == null) {
            return false;
        }

        return world.setBlockState(pos, getCachedState().with(PlateBlock.IS_COVERED, true));
    }

    /**
     * 取下盖子并尝试恢复摆盘流程。
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

        // 尝试恢复摆盘流程
        if (outcome != null) {
            restoreProcess();
        }

        world.playSound(null, pos, SoundEvents.BLOCK_METAL_PLACE, SoundCategory.BLOCKS, 0.5f, 1.2f);
        markDirty();
        return true;
    }

    /**
     * 尝试根据当前的{@link #outcome}恢复流程。
     */
    public boolean restoreProcess() {
        if (platingProcess.isActive() || outcome == null || world == null) {
            return false;
        }

        // 根据菜肴获取对应的配方
        PlatingRecipe recipe = PlatingRecipe.getRecipeByContainerAndDishes(getContainerType(), outcome);
        if (recipe == null) {
            return false;
        }

        // 清除当前的菜肴
        setOutcome(null);

        // 根据配方的操作恢复 performedActions
        List<PlayerAction> actions = recipe.getActions();
        performedActions.clear();
        performedActions.addAll(actions);

        // 启动摆盘流程
        platingProcess.start(world, this);

        // 初始化候选配方列表
        boolean initialized = platingProcess.initializeCandidates(world, this);
        if (!initialized) {
            // 如果初始化失败，重置状态
            clearPerformedActions();
            platingProcess.reset();
            return false;
        }

        markDirty();
        return true;
    }

    // ==================== 交互方法 ====================

    /**
     * 尝试摆盘。
     */
    public ActionResult tryPlating(PlayerEntity player, Hand hand, BlockHitResult hit) {
        // 检查是否满足摆盘条件
        if (eatProcess.isActive() || outcome != null || getCachedState().get(PlateBlock.IS_COVERED)) {
            BakingProcess.LOGGER.info("正常情况");
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
        // 如果摆盘流程活跃，不允许吃
        if (platingProcess.isActive()) {
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

        // 清除当前状态
        this.performedActions.clear();

        if (nbt.contains("plating_process")) {
            platingProcess.readFromNbt(nbt.getCompound("plating_process"));
        }

        if (nbt.contains("eat_process")) {
            eatProcess.readFromNbt(nbt.getCompound("eat_process"));
        }

        // 读取菜肴
        if (nbt.contains(OUTCOME_KEY, NbtElement.STRING_TYPE)) {
            Content content = TWRegistries.CONTENT.get(Identifier.tryParse(nbt.getString(OUTCOME_KEY)));
            setOutcome((DishesContent) content);
        } else {
            // 读取操作列表
            List<PlayerAction> actions = PlayerActionListUtil.readActionsFromNbt(nbt);
            performedActions.addAll(actions);
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

        if (outcome != null) {
            nbt.putString(OUTCOME_KEY, Objects.requireNonNull(TWRegistries.CONTENT.getId(outcome)).toString());
        } else {
            // 写入操作列表
            PlayerActionListUtil.writeActionsToNbt(nbt, performedActions);
        }
    }

    // ==================== PlatableBlockEntity 接口实现 ====================

    @Override
    public Item getContainerType() {
        return this.getCachedState().getBlock().asItem();
    }

    @Override
    public boolean isCompletionItem(ItemStack stack) {
        return stack.isOf(ModItems.PLATE_LID);
    }

    @Override
    public void onPlatingComplete(World world, BlockPos pos, PlatingRecipe recipe, PlayerEntity player, Hand hand, HitResult hit) {
        // 设置菜肴
        setOutcome(recipe.getDishes());

        // 消耗一个完成物品
        if (!player.isCreative()) {
            player.getStackInHand(hand).decrement(1);
        }

        // 盖上盖子
        if (coverWithLid()) {
            world.playSound(null, pos, SoundEvents.BLOCK_METAL_PLACE, SoundCategory.BLOCKS, 0.5f, 0.8f);
        }
    }

    @Override
    public void onEatComplete(World world, BlockPos pos, PlayerEntity player, Hand hand, HitResult hit) {
        setOutcome(null);
    }

    @Override
    public @Nullable DishesContent getOutcome() {
        return outcome;
    }

    @Override
    public int size() {
        return MAX_STEPS;
    }

    // ==================== 访问器方法 ====================

    /**
     * 设置当前的菜肴，这会同时清空当前的操作列表。
     */
    public void setOutcome(@Nullable DishesContent outcome) {
        this.outcome = outcome;

        if (outcome != null) {
            clearPerformedActions();
            platingProcess.reset();
        }

        markDirty();
    }

    /**
     * 获取当前摆盘流程。
     */
    public PlatingProcess<PlateBlockEntity> getPlatingProcess() {
        return platingProcess;
    }

    public EatDishesProcess<PlateBlockEntity> getEatProcess() {
        return eatProcess;
    }

    public String getDebugInfo() {
        return platingProcess.toString() + "\n" + getPerformedActions();
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
