package org.bakingprocess.client.render.block;

import net.fabricmc.fabric.api.client.rendering.v1.ColorProviderRegistry;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.BlockRenderView;
import org.bakingprocess.block.FlourSackBlock;
import org.bakingprocess.block.entity.FlourSackBlockEntity;
import org.bakingprocess.block.entity.PotsBlockEntity;
import org.bakingprocess.block.process.KneadingProcess;
import org.bakingprocess.integration.dfood.AssistedBlocks;
import org.bakingprocess.item.FlourItem;
import org.bakingprocess.item.FlourSackItem;
import org.bakingprocess.registry.ModBlocks;
import org.jetbrains.annotations.Nullable;
import org.twcore.api.block.UpPlaceBlockEntity;
import org.twcore.content.Content;
import org.twcore.content.HaveColorContent;

import java.util.Map;
import java.util.Optional;

public class ModBlockColors {
    public static void registryColors() {
        ColorProviderRegistry.BLOCK.register(ModBlockColors::getFlourSackColor, ModBlocks.FLOUR_SACK);
        ColorProviderRegistry.BLOCK.register((state, world, pos, tintIndex) -> tintIndex != -1 ? 4159204 : -1, AssistedBlocks.CRIPPLED_WATER_BUCKET);
        ColorProviderRegistry.BLOCK.register(ModBlockColors::getPotColor, ModBlocks.IRON_POTS, ModBlocks.CLAY_POTS);
    }

    private static int getFlourSackColor(BlockState state, @Nullable BlockRenderView world, @Nullable BlockPos pos, int tintIndex) {
        // 检查必要的参数
        if (world == null || pos == null) {
            return -1;
        }

        // 粉尘袋被放到置物面上时，颜色取自它所在槽位的内容
        BlockEntity blockEntity = world.getBlockEntity(pos);
        if (blockEntity instanceof UpPlaceBlockEntity shelfBlockEntity) {
            // 处理架子上的粉尘袋染色
            return getShelfFlourSackColor(shelfBlockEntity, state);
        }
        else if (blockEntity instanceof FlourSackBlockEntity flourSackBlockEntity) {
            // 直接放置的粉尘袋
            return getDirectFlourSackColor(flourSackBlockEntity, tintIndex);
        } else {
            // 未知的方块实体类型
            return -1;
        }
    }

    private static int getShelfFlourSackColor(UpPlaceBlockEntity shelfBlockEntity, BlockState state) {
        // 获取架子索引
        int shelfIndex = 0;
        if (state.contains(FlourSackBlock.SHELF_INDEX)) {
            shelfIndex = state.get(FlourSackBlock.SHELF_INDEX);
        }

        // 检查该槽位是否有粉尘袋
        if (shelfIndex < 0 || shelfIndex >= shelfBlockEntity.size()) {
            return FlourSackBlockEntity.DEFAULT_FLOUR_COLOR;
        }

        ItemStack shelfStack = shelfBlockEntity.getStack(shelfIndex);
        if (shelfStack.isEmpty() || !(shelfStack.getItem() instanceof BlockItem blockItem) ||
                !(blockItem.getBlock() instanceof FlourSackBlock)) {
            return FlourSackBlockEntity.DEFAULT_FLOUR_COLOR;
        }

        // 直接从物品堆栈的NBT中获取粉尘颜色
        return getFlourColorFromItemStack(shelfStack);
    }

    private static int getDirectFlourSackColor(FlourSackBlockEntity flourSackBlockEntity, int tintIndex) {
        int sackCount = flourSackBlockEntity.getNbtCount();

        // 根据tintIndex获取对应位置的粉尘颜色
        if (tintIndex >= 0 && tintIndex < sackCount) {
            return flourSackBlockEntity.getFlourColor(tintIndex);
        }

        // 如果tintIndex超出范围
        return FlourSackBlockEntity.DEFAULT_FLOUR_COLOR;
    }

    private static int getFlourColorFromItemStack(ItemStack flourSackStack) {
        try {
            // 获取粉尘袋中存储的物品
            Optional<ItemStack> flourStackOptional = FlourSackItem.getBundledStack(flourSackStack);

            if (flourStackOptional.isPresent()) {
                ItemStack flourStack = flourStackOptional.get();

                if (!flourStack.isEmpty() && flourStack.getItem() instanceof FlourItem flourItem) {
                    return flourItem.getColor();
                }
            }

            return FlourSackBlockEntity.DEFAULT_FLOUR_COLOR;

        } catch (Exception e) {
            return FlourSackBlockEntity.DEFAULT_FLOUR_COLOR;
        }
    }

    private static int getPotColor(BlockState state, @Nullable BlockRenderView world, @Nullable BlockPos pos, int tintIndex) {
        // 只有特定的tintIndex需要染色（假设tintIndex 0是液体部分）
        if (tintIndex != 0) {
            return -1; // 使用方块原色
        }

        // 检查必要的参数
        if (world == null || pos == null) {
            return -1;
        }

        BlockEntity blockEntity = world.getBlockEntity(pos);
        if (blockEntity instanceof PotsBlockEntity potsBlockEntity) {

            return getMixedLiquidColor(potsBlockEntity.getKneadingProcess());
        }

        return -1;
    }

    /**
     * 计算混合液体颜色
     * 基于所有液体的颜色和计数，计算加权平均颜色
     *
     * @return 混合后的液体颜色，如果没有液体返回默认颜色-1
     */
    private static int getMixedLiquidColor(KneadingProcess<?> process) {
        if (process == null || !process.isActive()) {
            return -1; // 默认无颜色
        }

        Map<Content, Integer> liquidCounts = process.getLiquidCounts();
        if (liquidCounts.isEmpty()) {
            return -1; // 没有液体
        }

        int totalCount = 0;
        float totalRed = 0;
        float totalGreen = 0;
        float totalBlue = 0;

        // 遍历所有液体，计算加权颜色
        for (Map.Entry<Content, Integer> entry : liquidCounts.entrySet()) {
            Content content = entry.getKey();
            int count = entry.getValue();

            // 只处理BaseLiquidContent类型
            if (content instanceof HaveColorContent baseLiquidContent) {
                int color = baseLiquidContent.getColor();

                // 分解颜色分量
                int red = (color >> 16) & 0xFF;
                int green = (color >> 8) & 0xFF;
                int blue = color & 0xFF;

                // 使用平方加权法（更好避免颜色被稀释）
                totalRed += red * red * count;
                totalGreen += green * green * count;
                totalBlue += blue * blue * count;
                totalCount += count;
            }
        }

        if (totalCount == 0) {
            return -1; // 没有有效的液体颜色
        }

        // 计算加权平均并取平方根
        int mixedRed = (int) Math.sqrt(totalRed / totalCount);
        int mixedGreen = (int) Math.sqrt(totalGreen / totalCount);
        int mixedBlue = (int) Math.sqrt(totalBlue / totalCount);

        // 组合颜色，使用不透明Alpha
        return 0xFF000000 | (mixedRed << 16) | (mixedGreen << 8) | mixedBlue;
    }
}