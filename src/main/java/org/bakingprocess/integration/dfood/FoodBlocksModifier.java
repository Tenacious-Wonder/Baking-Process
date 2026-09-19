package org.bakingprocess.integration.dfood;

import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.ActionResult;
import org.dfood.block.entity.SuspiciousStewBlockEntity;
import org.dfood.block.FoodBlock;
import org.dfood.replace.FoodBlocks;

public class FoodBlocksModifier {
    /** 能够让玩家像使用蛋糕那样使用炖菜。*/
    public static final FoodBlock.OnUseHook stewEatHook = (state, world, pos, player, hand, hit) -> {
        if (player.canConsume(false)) {
            BlockEntity currentBlockEntity = world.getBlockEntity(pos);
            NbtCompound blockEntityData = null;
            // 如果是迷之炖菜方块实体，保存其数据
            if (currentBlockEntity instanceof SuspiciousStewBlockEntity) {
                blockEntityData = currentBlockEntity.createNbt();
            }
            BlockState blockState = CrippledStewBlock.getStewState(state);
            // 设置新的方块状态
            world.setBlockState(pos, blockState);
            // 如果有方块实体数据需要传递，将其应用到新的方块实体
            if (blockEntityData != null) {
                BlockEntity newBlockEntity = world.getBlockEntity(pos);
                if (newBlockEntity instanceof SuspiciousStewBlockEntity) {
                    newBlockEntity.readNbt(blockEntityData);
                }
            }
            blockState.onUse(world, player, hand, hit);
            return ActionResult.SUCCESS;
        }
        return ActionResult.PASS;
    };

    /** 可以使用空瓶子从水桶中盛出水 */
    protected static final FoodBlock.OnUseHook waterBucketHook = (state, world, pos, player, hand, hit) -> {
        ItemStack handStack = player.getStackInHand(hand);

        // 检查是否手持空瓶子
        if (handStack.getItem() == Items.GLASS_BOTTLE) {
            if (world.isClient) {
                // 客户端播放声音
                world.playSound(player, pos, SoundEvents.ITEM_BOTTLE_FILL, player.getSoundCategory(), 1.0F, 1.0F);
                return ActionResult.SUCCESS;
            }

            // 转换为残损水桶状态
            BlockState newState = CrippledBucketBlock.getWaterBucketState(state);
            world.setBlockState(pos, newState, 3);

            // 播放声音
            world.playSound(player, pos, SoundEvents.ITEM_BOTTLE_FILL, player.getSoundCategory(), 1.0F, 1.0F);

            // 调用新方块的使用方法
            newState.onUse(world, player, hand, hit);
            return ActionResult.SUCCESS;
        }

        return ActionResult.PASS;
    };

    public static void FoodBlockAdd() {
        ((FoodBlock)FoodBlocks.RABBIT_STEW).setOnUseHook(stewEatHook);
        ((FoodBlock)FoodBlocks.MUSHROOM_STEW).setOnUseHook(stewEatHook);
        ((FoodBlock)FoodBlocks.BEETROOT_SOUP).setOnUseHook(stewEatHook);
        ((FoodBlock)FoodBlocks.SUSPICIOUS_STEW).setOnUseHook(stewEatHook);

        ((FoodBlock)FoodBlocks.WATER_BUCKET).setOnUseHook(waterBucketHook);
        ((FoodBlock)FoodBlocks.MILK_BUCKET).setOnUseHook(waterBucketHook);
    }
}