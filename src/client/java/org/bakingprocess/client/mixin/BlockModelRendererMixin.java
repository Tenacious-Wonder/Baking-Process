package org.bakingprocess.client.mixin;

import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.block.BlockModelRenderer;
import net.minecraft.client.render.model.BakedModel;
import net.minecraft.client.render.model.BakedModelManager;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.world.BlockRenderView;
import org.bakingprocess.client.render.model.ModModelId;
import org.dfood.block.FoodBlock;
import org.bakingprocess.block.entity.HeatResistantSlateBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(BlockModelRenderer.class)
public class BlockModelRendererMixin {

    @ModifyVariable(
            method = "render(Lnet/minecraft/world/BlockRenderView;Lnet/minecraft/client/render/model/BakedModel;Lnet/minecraft/block/BlockState;Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumer;ZLnet/minecraft/util/math/random/Random;JI)V",
            at = @At("HEAD"),
            argsOnly = true)
    public BakedModel renderCookingModel(BakedModel model, BlockRenderView world, BakedModel bakedModel, BlockState state, BlockPos pos, MatrixStack matrices) {
        if (state.getBlock() instanceof FoodBlock foodBlock &&
                world.getBlockEntity(pos) instanceof HeatResistantSlateBlockEntity) {
            int foodValue = state.get(foodBlock.NUMBER_OF_FOOD);

            if (foodValue > 1) {
                BakedModelManager manager = MinecraftClient.getInstance().getBakedModelManager();
                Identifier renderModelId = ModModelId.createCookingModelId(Registries.BLOCK.getId(state.getBlock()).getPath(), foodValue);
                BakedModel model1 = manager.getModel(renderModelId);

                // 只在成功获取到有效模型时才执行旋转并返回新模型
                if (model1 != null && model1 != manager.getMissingModel()) {
                    // 手动旋转模型
                    matrices.translate(0.5, 0.5, 0.5);
                    float facing = state.get(FoodBlock.FACING).asRotation();
                    matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(facing));
                    matrices.translate(-0.5, -0.5, -0.5);

                    return model1;
                }
            }
        }

        return model;
    }
}