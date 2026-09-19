package org.bakingprocess.block;

import net.minecraft.block.Block;
import net.minecraft.block.BlockRenderType;
import net.minecraft.block.BlockState;
import net.minecraft.block.BlockWithEntity;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BlockEntityTicker;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.item.ItemStack;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.BooleanProperty;
import net.minecraft.state.property.DirectionProperty;
import net.minecraft.state.property.Properties;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.World;
import net.minecraft.world.WorldView;
import org.bakingprocess.block.entity.GrillBlockEntity;
import org.bakingprocess.registry.ModBlockEntityTypes;
import org.bakingprocess.registry.ModBlocks;
import org.bakingprocess.registry.ModItems;
import org.jetbrains.annotations.Nullable;

public class GrillBlock extends BlockWithEntity {
    public static final DirectionProperty FACING = Properties.HORIZONTAL_FACING;
    public static final BooleanProperty LIT = Properties.LIT;

    public GrillBlock(Settings settings) {
        super(settings);
    }

    @Override
    public @Nullable BlockState getPlacementState(ItemPlacementContext ctx) {
        return this.getDefaultState()
                .with(FACING, ctx.getHorizontalPlayerFacing())
                .with(LIT, false);
    }

    @Override
    protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
        builder.add(FACING, LIT);
    }

    @Override
    public boolean canPlaceAt(BlockState state, WorldView world, BlockPos pos) {
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if (dx == 0 && dz == 0) {
                    continue;
                }
                if (!world.getBlockState(pos.add(dx, 0, dz)).isAir()) {
                    return false;
                }
            }
        }
        return super.canPlaceAt(state, world, pos);
    }

    @Override
    public void neighborUpdate(BlockState state, World world, BlockPos pos, Block sourceBlock, BlockPos sourcePos, boolean notify) {
        super.neighborUpdate(state, world, pos, sourceBlock, sourcePos, notify);
        if (world.isClient) {
            return;
        }
        for (Direction direction : Direction.Type.HORIZONTAL) {
            if (!world.getBlockState(pos.offset(direction)).isAir()) {
                world.breakBlock(pos, true);
                return;
            }
        }
    }

    @Override
    public ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, Hand hand, BlockHitResult hit) {
        if (!(world.getBlockEntity(pos) instanceof GrillBlockEntity grill)) {
            return ActionResult.PASS;
        }

        // 空烤架没有正在维护的柴火堆可交互：手持柴火时放入第一根，否则放行
        BlockState firewoodState = grill.getFirewoodState();
        if (firewoodState == null) {
            ItemStack stack = player.getStackInHand(hand);

            // 向空烤架放入第一根柴火
            if (stack.isOf(ModItems.FIREWOOD)) {
                if (!grill.addFirewood()) {
                    return ActionResult.PASS;
                }

                // 消耗手持的柴火
                ((FirewoodBlock) ModBlocks.FIREWOOD).playPlaceSound(world, pos, player);
                if (!player.isCreative()) {
                    stack.decrement(1);
                }

                return ActionResult.SUCCESS;
            }

            // 空手直接取下空烤架（优先级最低，仅空烤架可达）：播放铁活版门开合声后端起整个烤架
            if (stack.isEmpty()) {
                world.playSound(player, pos, SoundEvents.BLOCK_IRON_TRAPDOOR_OPEN, SoundCategory.BLOCKS, 1.0F, 1.0F);
                if (!world.isClient()) {
                    ItemStack grillStack = new ItemStack(ModItems.GRILL);
                    if (!player.isCreative() && !player.giveItemStack(grillStack)) {
                        player.dropItem(grillStack, false);
                    }
                    world.removeBlock(pos, false);
                }
                return ActionResult.SUCCESS;
            }

            return ActionResult.PASS;
        }
        return firewoodState.onUse(world, player, hand, hit);
    }

    @Override
    public void onStateReplaced(BlockState state, World world, BlockPos pos, BlockState newState, boolean moved) {
        if (state.isOf(newState.getBlock())) {
            return;
        }

        if (!moved) {
            if (world.getBlockEntity(pos) instanceof GrillBlockEntity grill) {
                grill.releaseFirewood(world, pos, state.get(FACING));
            }
            // 未放置真实柴火堆（空烤架或放置失败）时兜底移除自身方块实体；
            // 放置成功时方块实体已由释放逻辑移除并重建为燃烧实体，此处不会误伤
            if (world.getBlockEntity(pos) instanceof GrillBlockEntity) {
                world.removeBlockEntity(pos);
            }
        }
    }

    @Override
    public @Nullable BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
        return new GrillBlockEntity(pos, state);
    }

    @Override
    public @Nullable <T extends BlockEntity> BlockEntityTicker<T> getTicker(World world, BlockState state, BlockEntityType<T> type) {
        return checkType(type, ModBlockEntityTypes.GRILL, GrillBlockEntity::tick);
    }

    @Override
    public BlockRenderType getRenderType(BlockState state) {
        return BlockRenderType.MODEL;
    }

    @Override
    public void randomDisplayTick(BlockState state, World world, BlockPos pos, Random random) {
        super.randomDisplayTick(state, world, pos, random);

        if (!(world.getBlockEntity(pos) instanceof GrillBlockEntity grill) || !grill.isBaking()) {
            return;
        }
        HeatResistantSlateBlock.playBakingEffects(world, pos, pos.up(), random);
    }
}
