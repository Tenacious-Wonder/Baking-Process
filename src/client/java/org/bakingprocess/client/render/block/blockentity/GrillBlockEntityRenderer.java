package org.bakingprocess.client.render.block.blockentity;

import net.minecraft.block.BlockState;
import net.minecraft.client.render.RenderLayers;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.block.BlockRenderManager;
import net.minecraft.client.render.block.entity.BlockEntityRenderer;
import net.minecraft.client.render.block.entity.BlockEntityRendererFactory;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.world.World;
import org.bakingprocess.block.entity.GrillBlockEntity;

public class GrillBlockEntityRenderer implements BlockEntityRenderer<GrillBlockEntity> {
    private final BlockRenderManager blockRender;

    public GrillBlockEntityRenderer(BlockEntityRendererFactory.Context context) {
        this.blockRender = context.getRenderManager();
    }

    @Override
    public void render(GrillBlockEntity entity, float tickDelta, MatrixStack matrices,
                       VertexConsumerProvider vertexConsumers, int light, int overlay) {
        World world = entity.getWorld();
        if (world == null) {
            return;
        }

        // 空烤架没有可渲染的柴火堆状态，跳过
        BlockState firewoodState = entity.getFirewoodState();
        if (firewoodState == null) {
            return;
        }

        matrices.push();
        blockRender.renderBlock(
                firewoodState,
                entity.getPos(),
                world, matrices,
                vertexConsumers.getBuffer(RenderLayers.getBlockLayer(firewoodState)),
                true, world.random
        );
        matrices.pop();
    }
}
