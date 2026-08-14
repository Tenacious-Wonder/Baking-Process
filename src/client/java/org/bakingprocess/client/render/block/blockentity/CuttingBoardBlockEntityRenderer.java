package org.bakingprocess.client.render.block.blockentity;

import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.block.BlockModelRenderer;
import net.minecraft.client.render.block.entity.BlockEntityRendererFactory;
import net.minecraft.client.render.model.BakedModel;
import net.minecraft.client.render.model.BakedModelManager;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.random.Random;
import org.bakingprocess.block.CuttingBoardBlock;
import org.bakingprocess.block.entity.CuttingBoardBlockEntity;
import org.bakingprocess.block.process.CuttingProcess;
import org.bakingprocess.client.render.model.ModModelId;
import org.twcore.client.api.render.UpPlaceBlockEntityRenderer;

import java.util.HashMap;
import java.util.Map;

public class CuttingBoardBlockEntityRenderer extends UpPlaceBlockEntityRenderer<CuttingBoardBlockEntity> {
    public static final Map<Item, Float> ITEM_ROTATIONS = new HashMap<>();

    static {
        ITEM_ROTATIONS.put(Items.COD, -90f);
        ITEM_ROTATIONS.put(Items.COOKED_COD, -90f);
        ITEM_ROTATIONS.put(Items.SALMON, -90f);
        ITEM_ROTATIONS.put(Items.COOKED_SALMON, -90f);
    }

    private final BakedModelManager modelManager;
    private final BlockModelRenderer modelRenderer;

    public CuttingBoardBlockEntityRenderer(BlockEntityRendererFactory.Context ctx) {
        super(ctx);
        this.modelManager = ctx.getRenderManager().getModels().getModelManager();
        this.modelRenderer = ctx.getRenderManager().getModelRenderer();
    }

    /**
     * 获取切割模型
     * @param itemStack 正在切割的物品
     * @param cutCount 当前切割次数（从1开始）
     * @param manager BakedModelManager
     * @return 对应的切割模型，如果不存在则返回null
     */
    public static BakedModel getCuttingModel(ItemStack itemStack, int cutCount, BakedModelManager manager) {
        if (itemStack.isEmpty() || cutCount < 1) {
            return null;
        }

        // 获取物品的完整标识符
        Identifier itemId = Registries.ITEM.getId(itemStack.getItem());

        // 构建切割模型ID
        Identifier modelId = ModModelId.createCuttingModelId(itemId, cutCount);
        BakedModel model = manager.getModel(modelId);

        // 检查是否是有效模型（不是错误模型）
        if (model == manager.getMissingModel()) {
            return null;
        }

        return model;
    }

    @Override
    public void render(CuttingBoardBlockEntity entity, float tickDelta, MatrixStack matrices,
                       VertexConsumerProvider vertexConsumers, int light, int overlay) {
        CuttingProcess<CuttingBoardBlockEntity> cuttingProcess = entity.getCuttingProcess();
        ItemStack currentStack = cuttingProcess.isActive()?
                cuttingProcess.getState().inputStack():
                entity.getStack(0);

        matrices.push();
        matrices.translate(0, 0.1, 0);
        matrices.translate(0.5, 0, 0.5);

        // 物品特定旋转
        if (ITEM_ROTATIONS.containsKey(currentStack.getItem())) {
            matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(ITEM_ROTATIONS.get(currentStack.getItem())));
        }

        // 流程模型旋转
        if (cuttingProcess.isActive()) {
            Direction facing = entity.getCachedState().get(CuttingBoardBlock.FACING);
            matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-facing.asRotation()));
        }

        matrices.translate(-0.5, 0, -0.5);

        // 优先尝试渲染切割模型
        if (cuttingProcess.isActive()) {
            renderCuttingModel(entity, matrices, vertexConsumers);
        } else {
            // 如果没有切割模型，渲染默认物品
            fromStackRender(currentStack, entity, tickDelta, matrices, vertexConsumers, light, overlay);
        }

        matrices.pop();
    }

    /**
     * 渲染切割模型
     */
    private void renderCuttingModel(CuttingBoardBlockEntity entity, MatrixStack matrices, VertexConsumerProvider vertexConsumers) {
        CuttingProcess<CuttingBoardBlockEntity> process = entity.getCuttingProcess();

        if (process == null || !process.isActive()) {
            return;
        }

        CuttingProcess.CuttingState state = process.getState();
        if (state == null || state.inputStack() == null) {
            return;
        }

        int currentCut = state.currentCut();
        if (currentCut < 1) {
            return;
        }

        BakedModel model = getCuttingModel(state.inputStack(), currentCut, modelManager);

        if (model == null || model == modelManager.getMissingModel()) {
            return;
        }

        // 渲染切割模型
        modelRenderer.render(
                entity.getWorld(),
                model,
                entity.getCachedState(),
                entity.getPos(),
                matrices,
                vertexConsumers.getBuffer(RenderLayer.getCutout()),
                true,
                Random.create(),
                entity.getCachedState().getRenderingSeed(entity.getPos()),
                OverlayTexture.DEFAULT_UV
        );
    }
}