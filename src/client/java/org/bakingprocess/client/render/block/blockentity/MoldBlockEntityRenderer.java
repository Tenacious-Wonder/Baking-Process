package org.bakingprocess.client.render.block.blockentity;

import net.minecraft.block.BlockState;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.block.BlockModelRenderer;
import net.minecraft.client.render.block.entity.BlockEntityRenderer;
import net.minecraft.client.render.block.entity.BlockEntityRendererFactory;
import net.minecraft.client.render.model.BakedModel;
import net.minecraft.client.render.model.BakedModelManager;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.World;
import org.bakingprocess.block.entity.MoldBlockEntity;
import org.bakingprocess.client.render.model.ModModelId;
import org.bakingprocess.content.ShapedDoughContent;

public class MoldBlockEntityRenderer implements BlockEntityRenderer<MoldBlockEntity> {
    private final BakedModelManager modelManager;
    private final BlockModelRenderer modelRenderer;

    public MoldBlockEntityRenderer(BlockEntityRendererFactory.Context ctx) {
        this.modelManager = ctx.getRenderManager().getModels().getModelManager();
        this.modelRenderer = ctx.getRenderManager().getModelRenderer();
    }

    @Override
    public void render(MoldBlockEntity entity, float tickDelta, MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light, int overlay) {
        ShapedDoughContent content = entity.getShapedDough();

        if (content != null) {
            renderContent(content, entity.getCachedState(), entity.getPos(), entity.getWorld(), modelManager, modelRenderer, matrices, vertexConsumers);
        }
    }

    /**
     * 在模具中渲染定型面团。
     *
     * @param content 要渲染的定型面团
     */
    public static void renderContent(ShapedDoughContent content, BlockState state, BlockPos pos, World world,
                                     BakedModelManager modelManager, BlockModelRenderer modelRenderer,
                                     MatrixStack matrices, VertexConsumerProvider vertexConsumers) {
        matrices.push();
        matrices.translate(0, 0.1, 0);
        BakedModel renderModel = modelManager.getModel(ModModelId.createShapedDoughModelId(content));

        if (renderModel != null) {
            // 渲染切割模型
            modelRenderer.render(
                    world, renderModel, state, pos,
                    matrices, vertexConsumers.getBuffer(RenderLayer.getCutout()),
                    true, Random.create(), state.getRenderingSeed(pos), OverlayTexture.DEFAULT_UV
            );
        }

        matrices.pop();
    }
}
