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
import net.minecraft.item.Item;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.random.Random;
import org.bakingprocess.BakingProcess;
import org.bakingprocess.block.PlateBlock;
import org.bakingprocess.block.entity.PlateBlockEntity;
import org.bakingprocess.client.render.model.ModModelId;
import org.bakingprocess.client.render.model.PlatingModelManager;
import org.bakingprocess.config.IngredientTableData;
import org.bakingprocess.culinary.Culinary;
import org.bakingprocess.culinary.ingredient.CulinaryIngredient;
import org.bakingprocess.culinary.ingredient.IngredientCategory;
import org.bakingprocess.culinary.step.BakingStep;
import org.bakingprocess.culinary.step.PlatingStep;
import org.bakingprocess.culinary.step.ProcessingStep;
import org.twcore.api.process.PlayerAction;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class PlateBlockEntityRenderer implements BlockEntityRenderer<PlateBlockEntity> {
    private final BakedModelManager modelManager;
    private final BlockModelRenderer modelRenderer;

    public PlateBlockEntityRenderer(BlockEntityRendererFactory.Context context) {
        this.modelManager = context.getRenderManager().getModels().getModelManager();
        this.modelRenderer = context.getRenderManager().getModelRenderer();
    }

    @Override
    public void render(PlateBlockEntity entity, float tickDelta, MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light, int overlay) {
        if (entity.getCachedState().get(PlateBlock.IS_COVERED)) {
            return;
        }

        BlockState state = entity.getCachedState();
        Item item = state.getBlock().asItem();
        PlatingModelManager manager = PlatingModelManager.getInstance();
        Identifier renderModelId = manager.getModelForActions(item, entity.getPlatingProcess().getPerformedActions());

        // 有菜时按当前菜标识分派模型；吃过的菜显示对应食用阶段模型
        Culinary dish = entity.getCulinary();
        if (dish != null) {
            Identifier dishId = dish.getIdentifier();
            if (dishId != null) {
                int eaten = dish.getEatenCount();
                int total = dish.getTotalEats();
                if (eaten > 0 && eaten < total) {
                    renderModelId = ModModelId.createEatStageModelId(item, dishId, eaten);
                } else {
                    renderModelId = ModModelId.createDishesModelId(item, dishId);
                }
            }
        }

        // 获取模型；缺失（含无序菜无静态模型）时回退为无序摆盘的逐项渲染
        BakedModel renderModel = modelManager.getModel(renderModelId);
        if (renderModel == null || renderModel == modelManager.getMissingModel()) {
            renderGenericPlating(entity, state, matrices, vertexConsumers, light, overlay);
            return;
        }

        // 渲染最终模型
        matrices.push();
        matrices.translate(0.5, 0, 0.5);
        float facing = state.get(PlateBlock.FACING).asRotation();
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(facing));
        matrices.translate(-0.5, 0, -0.5);

        modelRenderer.render(
                entity.getWorld(),
                renderModel,
                state,
                entity.getPos(),
                matrices,
                vertexConsumers.getBuffer(RenderLayer.getCutout()),
                true,
                Random.create(),
                state.getRenderingSeed(entity.getPos()),
                OverlayTexture.DEFAULT_UV
        );

        matrices.pop();
    }

    // ==================== 无序摆盘逐项渲染 ====================

    /**
     * 无序菜（generic_plating 系列）的逐项渲染：按放入顺序把每个食材实例叠加到盘面。
     *
     * <p>模型定位：{@code generic_plating/<大类>/<小类>/[cooked_]<食材path>_<槽位号>}；
     * 熟菜（最新步骤为烘烤）的模型名加 {@code cooked_} 前缀。每个模型自带盘面摆位
     * （元素坐标直接画在方块空间内），渲染时不做位移、直接叠加。</p>
     *
     * <p><b>槽位（跨类别共享盘面物理位置）</b>：右/左下两个位置，容量 2（小型单位）；
     * 中型配菜权重 2（独占一个位置）、小型配菜权重 1（两个共用一个位置），按放入顺序贪心分配；
     * 主菜居中独立（{@code _1}）。槽位号 = 中型按位置（右上 {@code _1}、左下 {@code _2}），
     * 小型按 位置×2 + 位置内序号（右上 {@code _1~_2}、左下 {@code _3~_4}）。</p>
     *
     * <p>吃阶段：每吃一口就从操作序列末尾往前移除一个计口数原料（主菜/配菜），
     * 后放的先被吃掉；调料、装饰不计口数、始终保留。本期只渲染主菜与配菜。</p>
     */
    private void renderGenericPlating(PlateBlockEntity entity, BlockState state, MatrixStack matrices,
                                      VertexConsumerProvider vertexConsumers, int light, int overlay) {
        List<PlayerAction> actions = collectActions(entity);
        if (actions.isEmpty()) {
            return;
        }

        // 熟菜（最新步骤为烘烤）时模型名加 cooked_ 前缀，如 beef_1 → cooked_beef_1
        Culinary dish = entity.getCulinary();
        boolean cooked = dish != null && dish.getLatestStep() instanceof BakingStep;

        List<CulinaryIngredient> ingredients = IngredientTableData.current().fromActions(actions);
        if (ingredients.isEmpty()) {
            return;
        }

        // 吃了几口就从操作序列末尾往前扣几个计口数原料（主菜/配菜；调料、装饰不计口数、始终保留）
        int eaten = dish != null ? dish.getEatenCount() : 0;
        Set<Integer> eatenIndices = new HashSet<>();
        if (eaten > 0) {
            List<Integer> biteIndices = new ArrayList<>();
            for (int i = 0; i < ingredients.size(); i++) {
                CulinaryIngredient candidate = ingredients.get(i);
                if (!candidate.isSeasoning() && !candidate.isDecoration()) {
                    biteIndices.add(i);
                }
            }
            int skip = Math.min(eaten, biteIndices.size());
            eatenIndices.addAll(biteIndices.subList(biteIndices.size() - skip, biteIndices.size()));
        }

        // 盘面物理位置共享：右/左下两个位置，容量 2（小型单位）。中型配菜权重 2（独占一个位置）、
        // 小型配菜权重 1（两个共用一个位置），按放入顺序贪心分配；主菜居中独立（_1）。
        // 槽位号：中型 = 位置号（右上 _1、左下 _2）；小型 = 位置号×2 + 位置内序号（右上 _1~_2、左下 _3~_4）。
        int[] positionRemaining = {2, 2};
        int[] smallCountInPosition = {0, 0};

        matrices.push();
        matrices.translate(0.5, 0, 0.5);
        float facing = state.get(PlateBlock.FACING).asRotation();
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(facing));
        matrices.translate(-0.5, 0, -0.5);

        for (int i = 0; i < ingredients.size(); i++) {
            CulinaryIngredient ingredient = ingredients.get(i);
            // 本期不渲染调料与装饰
            if (ingredient.isSeasoning() || ingredient.isDecoration()) {
                continue;
            }
            // 已吃掉的原料不再渲染
            if (eatenIndices.contains(i)) {
                continue;
            }

            IngredientCategory category = ingredient.category();
            int slot;
            if (category.kind() == IngredientCategory.Kind.MAIN) {
                slot = 1;
            } else {
                int weight = category == IngredientCategory.SIDE_MEDIUM ? 2 : 1;
                int pos = -1;
                for (int p = 0; p < positionRemaining.length; p++) {
                    if (positionRemaining[p] >= weight) {
                        pos = p;
                        break;
                    }
                }
                if (pos < 0) {
                    continue; // 两个位置都放不下（超出 2 中型 / 4 小型容量），跳过该项
                }
                positionRemaining[pos] -= weight;
                if (weight == 2) {
                    slot = pos + 1;
                } else {
                    smallCountInPosition[pos]++;
                    slot = pos * 2 + smallCountInPosition[pos];
                }
            }

            String itemName = (cooked ? "cooked_" : "") + ingredient.id().getPath();
            Identifier modelId = new Identifier(BakingProcess.MOD_ID,
                    "generic_plating/" + category.asString() + "/" + itemName + "_" + slot);
            BakedModel model = modelManager.getModel(modelId);
            if (model == null || model == modelManager.getMissingModel()) {
                continue; // 该槽位/食材模型缺失时跳过这一项
            }

            modelRenderer.render(
                    entity.getWorld(),
                    model,
                    state,
                    entity.getPos(),
                    matrices,
                    vertexConsumers.getBuffer(RenderLayer.getCutout()),
                    true,
                    Random.create(),
                    state.getRenderingSeed(entity.getPos()),
                    OverlayTexture.DEFAULT_UV
            );
        }

        matrices.pop();
    }

    /** 取当前无序摆盘的操作序列：流程活动时用进行中的序列，否则取固化菜步骤链中的摆盘步骤序列。 */
    private static List<PlayerAction> collectActions(PlateBlockEntity entity) {
        if (entity.getPlatingProcess().isActive()) {
            return entity.getPlatingProcess().getPerformedActions();
        }
        Culinary dish = entity.getCulinary();
        if (dish == null) {
            return List.of();
        }
        for (ProcessingStep step : dish.getSteps()) {
            if (step instanceof PlatingStep plating) {
                return plating.getActions();
            }
        }
        return List.of();
    }
}
