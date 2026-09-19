package org.bakingprocess.integration.dfood;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.ShapeContext;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.FoodComponent;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.sound.SoundEvents;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.DirectionProperty;
import net.minecraft.state.property.Properties;
import net.minecraft.util.ActionResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;
import net.minecraft.world.WorldAccess;
import net.minecraft.world.event.GameEvent;
import org.dfood.replace.FoodBlocks;
import org.dfood.shape.FoodShapeHandle;
import org.dfood.util.IntPropertyManager;
import org.bakingprocess.block.CrippledBlock;

/**
 * 该类的实例是一个过渡方块，表示被食用过的汤
 */
public class CrippledStewBlock extends CrippledBlock {
    public final FoodComponent foodComponent;
    public static final DirectionProperty FACING = Properties.HORIZONTAL_FACING;
    private static final FoodShapeHandle foodShapeHandle = FoodShapeHandle.getInstance();

    public CrippledStewBlock(Settings settings, int maxUse, FoodComponent foodComponent, Block baseBlock) {
        super(settings, maxUse, baseBlock, new ItemStack(Items.BOWL));
        this.foodComponent = foodComponent;
    }

    @Override
    public VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
        return foodShapeHandle.getShape(state, NUMBER_OF_USE);
    }

    @Override
    protected ActionResult tryUse(WorldAccess world, BlockPos pos, BlockState state, PlayerEntity player) {
        if (!player.canConsume(false)) {
            return ActionResult.PASS;
        } else {
            world.playSound(player, pos, SoundEvents.ENTITY_GENERIC_DRINK, player.getSoundCategory(), 1.0F, 1.0F);
            player.getHungerManager().add(foodComponent.getHunger() / 4, foodComponent.getSaturationModifier() / 4.0F);
            int i = state.get(NUMBER_OF_USE);
            world.emitGameEvent(player, GameEvent.EAT, pos);
            if (i < useNumber) {
                world.setBlockState(pos, state.with(NUMBER_OF_USE, i + 1), Block.NOTIFY_ALL);
            } else {
                world.setBlockState(pos, getUseFinishesState(world, pos, state, player), Block.NOTIFY_ALL);
            }

            return ActionResult.SUCCESS;
        }
    }

    @Override
    protected BlockState getUseFinishesState(WorldAccess world, BlockPos pos, BlockState state, PlayerEntity player) {
        return FoodBlocks.BOWL.getDefaultState().with(FACING, state.get(FACING));
    }

    @Override
    public void onBreak(World world, BlockPos pos, BlockState state, PlayerEntity player) {
        super.onBreak(world,pos, state, player);
        if (!world.isClient && state.get(NUMBER_OF_USE) > 0) {
            int hunger = foodComponent.getHunger() / 4;
            float saturation = foodComponent.getSaturationModifier() / 4.0F;
            int numberOfEat = state.get(NUMBER_OF_USE);
            player.getHungerManager().add(hunger * numberOfEat, saturation * numberOfEat);
            world.emitGameEvent(player, GameEvent.EAT, pos);
        }
    }

    public static BlockState getStewState(BlockState state) {
        for (Block block : AssistedBlocks.assistedBlocks) {
            if (block instanceof CrippledStewBlock crippledStewBlock && crippledStewBlock.isBaseBlock(state)) {
                return crippledStewBlock.getDefaultState()
                        .with(CrippledStewBlock.FACING, state.get(CrippledStewBlock.FACING))
                        .with(IntPropertyManager.create("number_of_use", 4), 1);
            }
        }
        return Blocks.AIR.getDefaultState();
    }

    @Override
    protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
        super.appendProperties(builder);
        builder.add(FACING);
    }

    public boolean isSuspiciousStew() {
        return this instanceof CrippledSuspiciousStewBlock;
    }
}
