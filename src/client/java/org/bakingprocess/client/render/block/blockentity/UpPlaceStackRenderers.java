package org.bakingprocess.client.render.block.blockentity;

import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.render.model.BakedModel;
import net.minecraft.client.render.model.json.ModelTransformationMode;
import net.minecraft.item.Item;
import net.minecraft.registry.Registries;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.RotationAxis;
import org.bakingprocess.block.EmptyBreadBoatBlock;
import org.bakingprocess.block.BasePlatableBlock;
import org.bakingprocess.client.render.item.renderer.MoldItemRenderer;
import org.bakingprocess.client.render.model.ModModelId;
import org.bakingprocess.container.BreadBoatContainer;
import org.bakingprocess.registry.ModItems;
import org.bakingprocess.util.BakingProcessUtils;
import org.dfood.replace.FoodBlocks;
import org.twcore.api.content.ContainerUtil;
import org.twcore.client.api.render.UpPlaceStackRenderer;
import org.twcore.content.Content;

public class UpPlaceStackRenderers {
    public static void registerAll() {
        // 菜刀
        UpPlaceStackRenderer.register(ModItems.KITCHEN_KNIFE, createKitchenKnifeRenderer());

        // 可摆盘容器（铁盘 / 平底锅）
        registerPlatableContainer(ModItems.IRON_PLATE);
        registerPlatableContainer(ModItems.FRYING_PAN);

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

    /**
     * 注册可摆盘容器（铁盘 / 平底锅）在置物面（耐热石板等）上的渲染：
     * 带菜的成品容器视为盖盖态——先渲染盘体（方块状态，朝向随置物面），再在盘沿之上叠加铁盖物品模型。
     */
    private static void registerPlatableContainer(Item container) {
        UpPlaceStackRenderer.register(container, context -> {
            BlockState state = context.getDefaultBlockState();
            boolean hasDish = state.getBlock() instanceof BasePlatableBlock
                    && BasePlatableBlock.readCulinaryFromStack(context.stack()) != null;

            // 有菜的成品盘视为盖盖态（盘体模型不受盖盖影响，盖由下方叠加）
            if (hasDish) {
                state = state.with(BasePlatableBlock.IS_COVERED, true);
            }

            context.renderBlockStateOrItem(state);

            // 盖盖态：盘体之上叠加铁盖物品（PLATE_LID）模型，抬升 1px（与方块实体渲染一致）
            if (hasDish) {
                BakedModel lidModel = context.getModelManager().getModel(
                        ModModelId.createItemModelId(Registries.ITEM.getId(ModItems.PLATE_LID).getPath()));
                if (lidModel != null && lidModel != context.getModelManager().getMissingModel()) {
                    context.matrices().push();
                    context.matrices().translate(0.0F, 1.0F / 16.0F, 0.0F);
                    context.renderCustomModel(lidModel, state);
                    context.matrices().pop();
                }
            }
        });
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