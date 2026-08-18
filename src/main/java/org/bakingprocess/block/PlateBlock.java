package org.bakingprocess.block;

import net.minecraft.block.Block;
import net.minecraft.block.BlockEntityProvider;
import net.minecraft.block.BlockState;
import net.minecraft.block.ShapeContext;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.client.item.TooltipContext;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.loot.context.LootContextParameterSet;
import net.minecraft.loot.context.LootContextParameters;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.registry.Registries;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.BooleanProperty;
import net.minecraft.state.property.DirectionProperty;
import net.minecraft.state.property.Properties;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.ItemScatterer;
import net.minecraft.util.collection.DefaultedList;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;
import org.bakingprocess.block.entity.PlateBlockEntity;
import org.bakingprocess.culinary.Culinary;
import org.bakingprocess.culinary.carrier.ItemStackVessel;
import org.bakingprocess.culinary.carrier.ServingVessel;
import org.bakingprocess.culinary.step.ProcessingStep;
import org.bakingprocess.registry.ModItems;
import org.jetbrains.annotations.Nullable;
import org.twcore.api.process.PlayerAction;

import java.util.List;

/**
 * <h1>盘子方块</h1>
 * <p>摆盘 / 食用 / 盖盖 / 取放交互的入口；菜数据经 {@link ServingVessel#CULINARY_NBT_KEY}
 * 在方块实体与盘子物品间互转（{@link #writeCulinaryToStack} / {@link #readCulinaryFromStack}）。</p>
 */
public class PlateBlock extends Block implements BlockEntityProvider {
    /** 盘子物品上承载菜肴数据的 NBT 键（见 {@link org.bakingprocess.culinary.carrier.ServingVessel#CULINARY_NBT_KEY}）。 */
    public static final String CULINARY_NBT_KEY = ServingVessel.CULINARY_NBT_KEY;

    /**
     * 表示当前的方块是否已经被盖子覆盖。
     * <p>请不要直接更改该属性的值。</p>
     */
    public static final BooleanProperty IS_COVERED = BooleanProperty.of("is_covered");
    public static final DirectionProperty FACING = Properties.HORIZONTAL_FACING;
    public static final VoxelShape BASE_SHAPE = Block.createCuboidShape(0.5, 0, 0.5, 15.5, 2,15.5);
    public static final VoxelShape LIB_SHAPE = Block.createCuboidShape(1, 2, 1, 15, 8, 15);

    public PlateBlock(Settings settings) {
        super(settings);
        this.setDefaultState(getDefaultState().with(IS_COVERED, false));
    }

    @Override
    public ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, Hand hand, BlockHitResult hit) {
        BlockEntity entity = world.getBlockEntity(pos);
        ItemStack handStack = player.getStackInHand(hand);

        if (hand == Hand.OFF_HAND) {
            return ActionResult.PASS;
        }

        if (entity instanceof PlateBlockEntity plateBlockEntity) {
            // 尝试食用
            if (plateBlockEntity.getCulinary() != null && !state.get(IS_COVERED) && handStack.isEmpty()) {
                return plateBlockEntity.tryEat(player, hand, hit);
            }

            if (plateBlockEntity.getEatProcess().isActive()) {
                return ActionResult.PASS;
            }

            // 尝试盖盖子
            if (plateBlockEntity.getCulinary() != null && !state.get(IS_COVERED) && handStack.isOf(ModItems.PLATE_LID)) {
                plateBlockEntity.coverWithLid();
                if (!player.isCreative()) {
                    handStack.decrement(1);
                }

                return ActionResult.SUCCESS;
            }

            // 尝试取下盖子
            if (state.get(IS_COVERED) && player.isSneaking() && plateBlockEntity.removeCoverAndRestore()) {
                player.giveItemStack(new ItemStack(ModItems.PLATE_LID));
                return ActionResult.SUCCESS;
            }

            // 直接取下整个盘子
            if (state.get(IS_COVERED) && plateBlockEntity.getCulinary() != null && !player.isSneaking() && handStack.isEmpty()) {
                // 构建带有菜肴数据的盘子物品
                ItemStack plateStack = new ItemStack(this.asItem());
                writeCulinaryToStack(plateStack, plateBlockEntity.getCulinary());

                // 给予玩家物品
                player.giveItemStack(plateStack);

                // 移除方块
                world.removeBlock(pos, false);
                return ActionResult.SUCCESS;
            }

            // 尝试摆盘
            return plateBlockEntity.tryPlating(player, hand, hit);
        }

        return super.onUse(state, world, pos, player, hand, hit);
    }

    @Override
    public List<ItemStack> getDroppedStacks(BlockState state, LootContextParameterSet.Builder builder) {
        BlockEntity entity = builder.get(LootContextParameters.BLOCK_ENTITY);

        if (state.get(IS_COVERED) && entity instanceof PlateBlockEntity plateBlockEntity) {
            List<ItemStack> droppedStacks = super.getDroppedStacks(state, builder);
            Culinary dish = plateBlockEntity.getCulinary();
            if (dish != null) {
                for (ItemStack stack : droppedStacks) {
                    if (stack.isOf(state.getBlock().asItem())) {
                        writeCulinaryToStack(stack, dish);
                    }
                }
            }
            return droppedStacks;
        }

        return super.getDroppedStacks(state, builder);
    }

    @Override
    public void onPlaced(World world, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack itemStack) {
        BlockEntity entity = world.getBlockEntity(pos);

        if (entity instanceof PlateBlockEntity plateBlockEntity) {
            Culinary dish = readCulinaryFromStack(itemStack);
            if (dish != null) {
                plateBlockEntity.tryAddCulinary(dish);
                plateBlockEntity.coverWithLid();
            }
        }
    }

    @Override
    public void onStateReplaced(BlockState state, World world, BlockPos pos, BlockState newState, boolean moved) {
        if (!state.isOf(newState.getBlock())) {
            BlockEntity blockEntity = world.getBlockEntity(pos);
            if (blockEntity instanceof PlateBlockEntity plateBlockEntity) {
                // 仅进行中的摆盘由流程管理的操作序列负责掉落原料；成品态（已固化菜肴）不掉
                if (plateBlockEntity.getPlatingProcess().isActive()) {
                    DefaultedList<ItemStack> stacks = DefaultedList.of();
                    for (PlayerAction action : plateBlockEntity.getPlatingProcess().getPerformedActions()) {
                        ItemStack stack = action.toItemStack();
                        if (!stack.isEmpty()) {
                            stacks.add(stack);
                        }
                    }
                    ItemScatterer.spawn(world, pos, stacks);
                }
                world.updateComparators(pos, this);
            }
            super.onStateReplaced(state, world, pos, newState, moved);
        }
    }

    @Override
    public void appendTooltip(ItemStack stack, @Nullable BlockView world, List<Text> tooltip, TooltipContext options) {
        Culinary dish = readCulinaryFromStack(stack);

        if (dish != null) {
            ProcessingStep latest = dish.getLatestStep();
            if (latest != null) {
                Text text = latest.getDisplayName();
                if (text instanceof MutableText mutableText) {
                    mutableText.formatted(Formatting.ITALIC, Formatting.DARK_GRAY);
                }
                tooltip.add(text);
            }
        }
    }

    @Override
    public VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
        if (state.get(IS_COVERED)) {
            return VoxelShapes.union(BASE_SHAPE, LIB_SHAPE);
        }

        return BASE_SHAPE;
    }

    // ==================== 菜肴数据与盘子物品的互转 ====================

    /**
     * 把菜肴数据写入盘子物品的 NBT（菜数据键 {@link #CULINARY_NBT_KEY}，
     * 容器身份复合对象 {@link ItemStackVessel#VESSEL_NBT_KEY} 供物品堆栈容器识别）。
     */
    public static void writeCulinaryToStack(ItemStack stack, Culinary dish) {
        NbtCompound root = stack.getOrCreateNbt();

        // 容器身份复合对象：name = 容器物品标识
        NbtCompound vessel = new NbtCompound();
        vessel.putString(ItemStackVessel.VESSEL_NAME_KEY, Registries.ITEM.getId(stack.getItem()).toString());
        root.put(ItemStackVessel.VESSEL_NBT_KEY, vessel);

        // 菜数据
        NbtCompound culinaryNbt = new NbtCompound();
        dish.writeNbt(culinaryNbt);
        root.put(CULINARY_NBT_KEY, culinaryNbt);
    }

    /** 从盘子物品的 NBT 读取菜肴数据；无菜时返回 {@code null}。 */
    @Nullable
    public static Culinary readCulinaryFromStack(ItemStack stack) {
        NbtCompound root = stack.getNbt();
        if (root == null || !root.contains(CULINARY_NBT_KEY, NbtElement.COMPOUND_TYPE)) {
            return null;
        }
        return Culinary.create().readNbt(root.getCompound(CULINARY_NBT_KEY));
    }

    @Override
    public @Nullable BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
        return new PlateBlockEntity(pos, state);
    }

    @Override
    protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
        builder.add(IS_COVERED, FACING);
    }
}
