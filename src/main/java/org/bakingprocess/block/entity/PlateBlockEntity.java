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
import org.bakingprocess.block.PlateBlock;
import org.bakingprocess.block.process.EatDishesProcess;
import org.bakingprocess.block.process.PlatingProcess;
import org.bakingprocess.content.DishesContent;
import org.bakingprocess.recipe.PlatingRecipe;
import org.bakingprocess.registry.ModBlockEntityTypes;
import org.bakingprocess.registry.ModItems;
import org.jetbrains.annotations.Nullable;
import org.twcore.content.Content;
import org.twcore.registry.TWRegistries;

import java.util.Objects;

/**
 * 盘子方块实体，可摆盘方块的标准实现。
 *
 * <p><strong>职责划分：</strong></p>
 * <ul>
 *   <li>本实体持有菜肴（{@link DishesContent}），并负责容器身份、完成物品判断与流程回调</li>
 * </ul>
 */
public class PlateBlockEntity extends BlockEntity implements PlatableBlockEntity {
    private static final String OUTCOME_KEY = "outcome";

    /** 摆盘流程（负责操作序列、候选配方与配方匹配） */
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

    /**
     * 尝试根据当前的{@link #outcome}恢复流程。
     *
     * <p>运行时世界必然可用，直接通过 {@link PlatingRecipe#findRecipe} 从配方管理器
     * 反查配方，将成品菜肴还原为进行中的摆盘流程，并立即恢复候选配方。</p>
     *
     * @return 是否成功恢复流程
     */
    public boolean restoreProcess() {
        if (platingProcess.isActive() || outcome == null || world == null) {
            return false;
        }

        // 从世界配方管理器反查配方
        PlatingRecipe recipe = PlatingRecipe.findRecipe(world, getContainerType(), outcome);
        if (recipe == null) {
            return false;
        }

        // 清除菜肴，并将配方的完整操作序列恢复到流程中
        setOutcome(null);
        platingProcess.restoreFromRecipe(recipe);

        // 启动摆盘流程，并立即恢复候选配方与精确匹配
        platingProcess.start(world, this);
        platingProcess.restoreCandidates(world, this);

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

        // 操作序列与流程状态由 PlatingProcess 自行序列化
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

    // ==================== 访问器方法 ====================

    /**
     * 设置当前的菜肴，这会同时清空当前的操作列表。
     */
    public void setOutcome(@Nullable DishesContent outcome) {
        this.outcome = outcome;

        if (outcome != null) {
            // 出菜后清空操作序列与流程状态
            platingProcess.clearPerformedActions();
            platingProcess.reset();
        }

        markDirty();
    }

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
