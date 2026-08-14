package org.bakingprocess.client.render.item.renderer;

import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.RenderLayers;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.block.BlockRenderManager;
import net.minecraft.client.render.model.BakedModel;
import net.minecraft.client.render.model.BakedModelManager;
import net.minecraft.client.render.model.json.ModelTransformationMode;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemStack;
import org.bakingprocess.client.render.model.ModModelId;
import org.bakingprocess.content.ShapedDoughContent;
import org.twcore.api.content.ContainerUtil;
import org.twcore.content.Content;

public class MoldItemRenderer {
    private static final MinecraftClient CLIENT = MinecraftClient.getInstance();

    public static void renderMold(ItemStack stack, ModelTransformationMode mode, MatrixStack matrices,
                                  VertexConsumerProvider vertexConsumers, int light, int overlay) {

        if (!(stack.getItem() instanceof BlockItem blockItem)) return;

        BlockRenderManager blockRenderer = CLIENT.getBlockRenderManager();
        BakedModelManager manager = CLIENT.getBakedModelManager();

        BlockState state = blockItem.getBlock().getDefaultState();
        Content content = ContainerUtil.extractContent(stack);

        // 渲染方块本身
        blockRenderer.renderBlockAsEntity(state, matrices, vertexConsumers, light, overlay);

        // 如果有定型面团内容，渲染它
        if (content instanceof ShapedDoughContent shapedDough) {
            BakedModel model = manager.getModel(ModModelId.createShapedDoughModelId(shapedDough));
            blockRenderer.getModelRenderer().render(matrices.peek(),
                    vertexConsumers.getBuffer(RenderLayers.getBlockLayer(state)), state, model, 1, 1, 1, light, overlay);
        }
    }
}