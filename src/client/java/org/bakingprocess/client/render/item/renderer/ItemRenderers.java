package org.bakingprocess.client.render.item.renderer;

import net.fabricmc.fabric.api.client.rendering.v1.BuiltinItemRendererRegistry;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.block.entity.BlockEntityRenderDispatcher;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.util.math.BlockPos;
import org.bakingprocess.block.entity.GrindingStoneBlockEntity;
import org.bakingprocess.registry.ModItems;

public class ItemRenderers {
    private static final BuiltinItemRendererRegistry INSTANCE = BuiltinItemRendererRegistry.INSTANCE;
    private static BlockEntityRenderDispatcher blockEntityRenderer;

    public static void registry() {
        registrySimpleBlockEntityRenderer(ModItems.GRINDING_STONE, GrindingStoneBlockEntity::new);
        INSTANCE.register(ModItems.TOAST_EMBRYO_MOLD, MoldItemRenderer::renderMold);
        INSTANCE.register(ModItems.CAKE_EMBRYO_MOLD, MoldItemRenderer::renderMold);
        // 铁盘 / 平底锅：复用方块实体渲染器（同石磨思路），物品形态与方块实体渲染效果一致
        INSTANCE.register(ModItems.IRON_PLATE, PlatableItemRenderer::render);
        INSTANCE.register(ModItems.FRYING_PAN, PlatableItemRenderer::render);
    }

    /**
     * 创建一个简单地复用方块实体渲染器的物品渲染器。
     * @param Entity 方块实体类型
     * @return 创建的物品渲染器
     */
    public static BuiltinItemRendererRegistry.DynamicItemRenderer createSimpleBlockEntityRenderer(BlockEntity Entity) {
        return ((stack, mode, matrices, vertexConsumers, light, overlay) -> {
            if (blockEntityRenderer == null) {
                blockEntityRenderer = MinecraftClient.getInstance().getBlockEntityRenderDispatcher();
            }

            blockEntityRenderer.renderEntity(Entity, matrices, vertexConsumers, light, overlay);
        });
    }

    /**
     * 注册一个简单地复用方块实体渲染器的物品渲染器
     * @param item 要注册的物品，需要实现{@link BlockItem}
     * @param factory 方块实体工厂
     */
    public static void registrySimpleBlockEntityRenderer(Item item, BlockEntityType.BlockEntityFactory<BlockEntity> factory) {
        if (item instanceof BlockItem blockItem) {
            BlockState state = blockItem.getBlock().getDefaultState();
            BlockEntity entity = factory.create(BlockPos.ORIGIN, state);

            INSTANCE.register(item, createSimpleBlockEntityRenderer(entity));
        }
    }
}
