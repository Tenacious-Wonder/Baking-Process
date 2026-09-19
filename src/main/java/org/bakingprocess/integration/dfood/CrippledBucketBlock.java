package org.bakingprocess.integration.dfood;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.ShapeContext;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.potion.Potion;
import net.minecraft.potion.PotionUtil;
import net.minecraft.sound.SoundEvents;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.DirectionProperty;
import net.minecraft.state.property.Properties;
import net.minecraft.util.ActionResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.BlockView;
import net.minecraft.world.WorldAccess;
import net.minecraft.world.event.GameEvent;
import org.dfood.replace.FoodBlocks;
import org.dfood.shape.FoodShapeHandle;
import org.dfood.util.IntPropertyManager;
import org.bakingprocess.block.CrippledBlock;
import org.bakingprocess.registry.ModItems;
import org.jetbrains.annotations.Nullable;

/**
 * 水桶的残损方块，表示被使用过的桶
 */
public class CrippledBucketBlock extends CrippledBlock {
    public static final DirectionProperty FACING = Properties.HORIZONTAL_FACING;
    private static final FoodShapeHandle foodShapeHandle = FoodShapeHandle.getInstance();

    /**
     * 药水类型，用于确定给予玩家的瓶子内容物。
     * 为null时表示奶桶。
     */
    @Nullable
    private final Potion potionType;

    public CrippledBucketBlock(Settings settings, int maxUse, Block baseBlock, @Nullable Potion potionType) {
        super(settings, maxUse, baseBlock, new ItemStack(Items.BUCKET));
        this.potionType = potionType;
    }

    @Override
    public VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
        return foodShapeHandle.getShape(state, NUMBER_OF_USE);
    }

    @Override
    protected ActionResult tryUse(WorldAccess world, BlockPos pos, BlockState state, PlayerEntity player) {
        ItemStack handStack = player.getMainHandStack();

        // 检查是否手持空瓶子
        if (handStack.getItem() == Items.GLASS_BOTTLE) {
            int i = state.get(NUMBER_OF_USE);
            world.playSound(player, pos, SoundEvents.ITEM_BOTTLE_FILL, player.getSoundCategory(), 1.0F, 1.0F);

            // 消耗空瓶子并给予水瓶
            if (!player.isCreative()) {
                handStack.decrement(1);
            }
            ItemStack waterBottle = PotionUtil.setPotion(new ItemStack(Items.POTION), this.potionType);
            if (this.potionType == null) {
                waterBottle = new ItemStack(ModItems.MILK_POTION);
            }
            if (!player.giveItemStack(waterBottle)) {
                player.dropItem(waterBottle, false);
            }

            world.emitGameEvent(player, GameEvent.FLUID_PICKUP, pos);

            if (i < 3) {
                world.setBlockState(pos, state.with(NUMBER_OF_USE, i + 1), Block.NOTIFY_ALL);
            } else {
                // 水用完了，变成空桶
                world.setBlockState(pos, getUseFinishesState(world, pos, state, player), Block.NOTIFY_ALL);
            }

            return ActionResult.SUCCESS;
        }

        return ActionResult.PASS;
    }

    @Override
    protected BlockState getUseFinishesState(WorldAccess world, BlockPos pos, BlockState state, PlayerEntity player) {
        return FoodBlocks.BUCKET.getDefaultState().with(FACING, state.get(FACING));
    }

    @Override
    protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
        super.appendProperties(builder);
        builder.add(FACING);
    }

    public static BlockState getWaterBucketState(BlockState state) {
        for (Block block : AssistedBlocks.assistedBlocks) {
            if (block instanceof CrippledBucketBlock crippledBucketBlock && crippledBucketBlock.isBaseBlock(state)) {
                return crippledBucketBlock.getDefaultState()
                        .with(CrippledBucketBlock.FACING, state.get(CrippledBucketBlock.FACING))
                        .with(IntPropertyManager.create("number_of_use", 3), 1);
            }
        }
        return Blocks.AIR.getDefaultState();
    }
}