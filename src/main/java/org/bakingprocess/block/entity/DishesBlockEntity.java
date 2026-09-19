package org.bakingprocess.block.entity;

import net.minecraft.block.BlockState;
import net.minecraft.block.ShapeContext;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.listener.ClientPlayPacketListener;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.play.BlockEntityUpdateS2CPacket;
import net.minecraft.util.ActionResult;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.BlockView;
import org.dfood.block.FoodBlock;
import org.dfood.item.DoubleBlockItem;
import org.dfood.shape.FoodShapeHandle;
import org.bakingprocess.block.GarnishDishesBlock;
import org.bakingprocess.registry.ModBlockEntityTypes;
import org.bakingprocess.util.BakingProcessUtils;
import org.twcore.api.block.UpPlaceBlockEntity;

import java.util.List;

/**
 * 盘子方块实体，用于存储食物物品
 * 注意：item.getBlock()返回的Block必须是{@link FoodBlock}的实例，否则无法放入物品栏
 */
public class DishesBlockEntity extends UpPlaceBlockEntity {
    private static final int INVENTORY_SIZE = 1;
    private static final int MAX_STACK_SIZE = 11;
    private static final double FOOD_OFFSET_Y = 0.1;
    private static final FoodShapeHandle SHAPE_HANDLE = FoodShapeHandle.getInstance();

    public DishesBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntityTypes.GARNISH_DISHES, pos, state, INVENTORY_SIZE);
    }

    @Override
    public VoxelShape getContentShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
        BlockState itemState = this.getInventoryBlockState();
        if (itemState.getBlock() instanceof FoodBlock foodBlock) {
            return SHAPE_HANDLE.getShape(itemState, foodBlock.NUMBER_OF_FOOD)
                    .offset(0.0, FOOD_OFFSET_Y, 0.0);
        }
        return FoodShapeHandle.shapes.ALL.getShape().offset(0.0, FOOD_OFFSET_Y, 0.0);
    }

    @Override
    public boolean isValidItem(ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }

        // 蛋糕可以直接放入
        if (stack.getItem() == Items.CAKE) {
            return true;
        }

        Item item = stack.getItem();
        if (item instanceof DoubleBlockItem doubleBlockItem) {
            return doubleBlockItem.getSecondBlock() instanceof FoodBlock;
        } else if (item instanceof BlockItem blockItem) {
            return blockItem.getBlock() instanceof FoodBlock;
        }
        return false;
    }

    /**
     * 获取当前物品栏中的物品对应的方块状态
     * @return 物品对应的方块状态
     */
    public BlockState getInventoryBlockState() {
        ItemStack stack = this.inventory.get(0);
        Direction facing = this.getCachedState().get(GarnishDishesBlock.FACING);

        return BakingProcessUtils.createCountBlockstate(stack, facing);
    }

    @Override
    public Result tryAddItem(ItemStack stack, BlockHitResult hit) {
        if (stack.isEmpty() || !isValidItem(stack)) {
            return Result.of(ActionResult.PASS);
        }

        Item item = stack.getItem();
        ItemStack newStack = stack.copy();
        newStack.setCount(1);
        ItemStack currentStack = this.getStack(0);

        if (currentStack.isEmpty()) {
            this.setStack(0, newStack);
            this.markDirtyAndSync();
            return Result.of(newStack, ActionResult.SUCCESS);
        } else if (currentStack.getItem() == item) {
            FoodBlock block = (FoodBlock) getInventoryBlockState().getBlock();
            if (currentStack.getCount() < block.MAX_FOOD) {
                currentStack.increment(1);
                this.markDirtyAndSync();
                return Result.of(newStack, ActionResult.SUCCESS);
            }
        }
        return Result.of(ActionResult.PASS);
    }

    @Override
    public Result tryFetchItem(PlayerEntity player, BlockHitResult hit) {
        if (!isEmpty()) {
            ItemStack stack = removeStack(0, 1);
            if (!player.isCreative() && !player.giveItemStack(stack)) {
                player.dropItem(stack, false);
            }
            markDirtyAndSync();
            return Result.of(List.of(stack.copy()), ActionResult.SUCCESS);
        }
        return Result.of(ActionResult.PASS);
    }

    @Override
    public int getMaxCountPerStack() {
        return MAX_STACK_SIZE;
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