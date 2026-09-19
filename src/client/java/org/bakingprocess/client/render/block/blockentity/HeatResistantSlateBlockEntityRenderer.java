package org.bakingprocess.client.render.block.blockentity;

import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.block.entity.BlockEntityRendererFactory;
import net.minecraft.client.util.math.MatrixStack;
import org.bakingprocess.block.entity.HeatResistantSlateBlockEntity;
import org.twcore.api.blockvolume.BlockVolume;
import org.twcore.client.api.render.BlockVolumeDebugRenderer;
import org.twcore.client.api.render.UpPlaceBlockEntityRenderer;

public class HeatResistantSlateBlockEntityRenderer extends UpPlaceBlockEntityRenderer<HeatResistantSlateBlockEntity> implements BlockVolumeDebugRenderer<HeatResistantSlateBlockEntity> {

    public HeatResistantSlateBlockEntityRenderer(BlockEntityRendererFactory.Context ctx) {
        super(ctx);
    }

    @Override
    public void render(HeatResistantSlateBlockEntity entity, float tickDelta, MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light, int overlay) {
        if (entity.isEmpty()) {
            return;
        }

        matrices.push();

        // 调整渲染位置
        matrices.translate(0.0, 0.125, 0.0);
        fromStackRender(entity.getStack(0), entity, tickDelta, matrices, vertexConsumers, light, overlay);

        matrices.pop();
    }

    @Override
    public TextRenderer getTextRenderer() {
        return context.getTextRenderer();
    }

    @Override
    public BlockVolume getBlockVolume(HeatResistantSlateBlockEntity entity) {
        return entity.getBlockVolume();
    }

    @Override
    public void otherDebugRender(HeatResistantSlateBlockEntity entity, BlockVolume volume, float tickDelta, MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light, int overlay) {
        // 如果炉子结构有效，显示额外信息
        if (entity.isStoveValid()) {
            String stoveType = "Stove: " + entity.getCurrentStoveStructureType();
            int stoveWidth = getTextRenderer().getWidth(stoveType);
            getTextRenderer().draw(
                    stoveType,
                    -stoveWidth / 2f, 10,
                    0xFF00FF00,
                    false,
                    matrices.peek().getPositionMatrix(),
                    vertexConsumers,
                    TextRenderer.TextLayerType.POLYGON_OFFSET,
                    0,
                    light
            );
        }
    }
}
