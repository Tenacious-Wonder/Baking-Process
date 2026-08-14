package org.bakingprocess.client.render.block.blockentity;

import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.render.model.BakedModel;
import net.minecraft.client.render.model.json.ModelTransformationMode;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.RotationAxis;
import org.bakingprocess.block.EmptyBreadBoatBlock;
import org.bakingprocess.block.PlateBlock;
import org.bakingprocess.client.render.item.renderer.MoldItemRenderer;
import org.bakingprocess.client.render.model.ModModelId;
import org.bakingprocess.container.BreadBoatContainer;
import org.bakingprocess.registry.ModItems;
import org.bakingprocess.util.BakingProcessUtils;
import org.dfood.block.FoodBlocks;
import org.twcore.api.content.ContainerUtil;
import org.twcore.client.api.render.UpPlaceStackRenderer;
import org.twcore.content.Content;

public class UpPlaceStackRenderers {
    public static void registerAll() {
        // 菜刀
        UpPlaceStackRenderer.register(ModItems.KITCHEN_KNIFE, createKitchenKnifeRenderer());

        // 铁盘
        UpPlaceStackRenderer.register(ModItems.IRON_PLATE, context -> {
            BlockState state = context.getDefaultBlockState();
            Content content = ContainerUtil.extractContent(context.stack());

            if (content != null && state.getBlock() instanceof PlateBlock) {
                state = state.with(PlateBlock.IS_COVERED, true);
            }

            context.renderBlockStateOrItem(state);
        });

        // 面包船
        UpPlaceStackRenderer.register(ModItems.HARD_BREAD_BOAT, context -> {
            BlockState state = context.getDefaultBlockState();
            Content content = ContainerUtil.extractContent(context.stack());

            if (content != null && state.getBlock() instanceof EmptyBreadBoatBlock) {
                BreadBoatContainer.BreadBoatSoupType soupType =
                        BreadBoatContainer.BreadBoatSoupType.fromContent(content);
                state = EmptyBreadBoatBlock.asTargetState(state, soupType);
            }

            context.renderBlockStateOrItem(state);
        });

        // 食物方块
        FoodBlocks.FOOD_BLOCK_REGISTRY.forEach((s, block) ->
                UpPlaceStackRenderer.register(block.asItem(), createSpecialItemRenderer()));

        // 模具
        UpPlaceStackRenderer.register(ModItems.TOAST_EMBRYO_MOLD, context -> MoldItemRenderer.renderMold(context.stack(), ModelTransformationMode.GUI, context.matrices(),
                context.vertexConsumers(), context.light(), context.overlay()));
        UpPlaceStackRenderer.register(ModItems.CAKE_EMBRYO_MOLD, context -> MoldItemRenderer.renderMold(context.stack(), ModelTransformationMode.GUI,
                context.matrices(), context.vertexConsumers(), context.light(), context.overlay()));
    }

    public static UpPlaceStackRenderer createSpecialItemRenderer() {
        return context -> {
            BlockState renderState = BakingProcessUtils.createCountBlockstate(context.stack(), context.getFacing());
            if (!renderState.isOf(Blocks.AIR)) {
                context.renderBlockState(renderState);
            } else {
                context.renderItem();
            }
        };
    }

    public static UpPlaceStackRenderer createKitchenKnifeRenderer() {
        return context -> {
            BakedModel model = context.getModelManager()
                    .getModel(ModModelId.BOARD_KITCHEN_KNIFE);

            if (model == null) {
                context.defaultRender();
                return;
            }

            Direction facing = context.getFacing();
            BlockState state = context.getDefaultBlockState();

            context.matrices().push();

            // 应用菜刀特定的变换
            context.matrices().translate(0.5, 0.0, 0.5);
            context.matrices().multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-facing.asRotation()));
            context.matrices().translate(-0.5, -0.1, -0.5);

            // 渲染菜刀模型
            context.renderCustomModel(model, state);

            context.matrices().pop();
        };
    }
}