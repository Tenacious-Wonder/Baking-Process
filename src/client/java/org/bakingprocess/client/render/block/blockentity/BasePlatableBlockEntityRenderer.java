package org.bakingprocess.client.render.block.blockentity;

import net.minecraft.block.BlockState;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.RenderLayers;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.block.BlockModelRenderer;
import net.minecraft.client.render.block.BlockRenderManager;
import net.minecraft.client.render.block.entity.BlockEntityRenderer;
import net.minecraft.client.render.block.entity.BlockEntityRendererFactory;
import net.minecraft.client.render.model.BakedModel;
import net.minecraft.client.render.model.BakedModelManager;
import net.minecraft.client.util.ModelIdentifier;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.Item;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.World;
import org.bakingprocess.BakingProcess;
import org.bakingprocess.block.BasePlatableBlock;
import org.bakingprocess.block.entity.BasePlatableBlockEntity;
import org.bakingprocess.client.render.model.ModModelId;
import org.bakingprocess.client.render.model.PlatingModelManager;
import org.bakingprocess.config.IngredientTableData;
import org.bakingprocess.culinary.Culinary;
import org.bakingprocess.culinary.ingredient.CulinaryIngredient;
import org.bakingprocess.culinary.ingredient.IngredientCategory;
import org.bakingprocess.culinary.step.BakingStep;
import org.bakingprocess.culinary.step.PlatingStep;
import org.bakingprocess.culinary.step.ProcessingStep;
import org.bakingprocess.registry.ModItems;
import org.twcore.api.process.PlayerAction;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class BasePlatableBlockEntityRenderer implements BlockEntityRenderer<BasePlatableBlockEntity> {
    /** 铁盖（{@link ModItems#PLATE_LID}）的物品模型：盖盖态直接复用盖物品模型，像方块模型一样渲染。 */
    private static final ModelIdentifier LID_MODEL_ID = ModModelId.createItemModelId(
            Registries.ITEM.getId(ModItems.PLATE_LID).getPath());
    /** PLATE_LID 模型底在 y=0，渲染时抬升 1px，使盖底略微搭入盘沿（视觉略高于盘体）。 */
    private static final float LID_Y_LIFT = 1.0F / 16.0F;

    private final BakedModelManager modelManager;
    private final BlockRenderManager blockRenderManager;
    private final BlockModelRenderer modelRenderer;

    public BasePlatableBlockEntityRenderer(BlockEntityRendererFactory.Context context) {
        this.blockRenderManager = context.getRenderManager();
        this.modelManager = blockRenderManager.getModels().getModelManager();
        this.modelRenderer = blockRenderManager.getModelRenderer();
    }

    /**
     * 渲染可摆盘方块的内容层。
     *
     * <p><b>世界形态</b>：盘体由方块状态模型渲染，这里只补渲染内容——
     * 未盖盖时渲染盘面菜肴（成品菜模型 / 无序摆盘逐项叠加），盖盖时渲染铁盖物品
     * （{@link ModItems#PLATE_LID}）的模型并抬至盘沿之上（不再替换为二合一状态模型）。</p>
     *
     * <p><b>物品形态</b>（{@code world == null}，经 {@code BuiltinItemRendererRegistry} 复用本渲染器）：
     * 方块状态模型不会参与物品渲染，需先自绘盘体，再按盖盖与否叠加盖或菜肴，效果与世界形态一致。</p>
     */
    @Override
    public void render(BasePlatableBlockEntity entity, float tickDelta, MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light, int overlay) {
        BlockState state = entity.getCachedState();
        boolean covered = state.get(BasePlatableBlock.IS_COVERED);

        if (entity.getWorld() == null) {
            renderBodyInHand(state, matrices, vertexConsumers, light, overlay);
            if (covered) {
                renderCoveredContent(entity, state, matrices, vertexConsumers, light, overlay);
            } else {
                renderPlatedFood(entity, state, matrices, vertexConsumers, light, overlay);
            }
            return;
        }

        if (covered) {
            renderCoveredContent(entity, state, matrices, vertexConsumers, light, overlay);
            return;
        }
        renderPlatedFood(entity, state, matrices, vertexConsumers, light, overlay);
    }

    // ==================== 盖盖态：渲染铁盖物品模型 ====================

    /**
     * 渲染盖盖态的内容：直接从模型管理器取铁盖物品（{@link ModItems#PLATE_LID}）的模型，
     * 像渲染方块一样绘制在盘沿之上（模型底在 y=0，抬升 {@value #LID_Y_LIFT}）。
     * 世界与物品形态共用同一渲染路径，不再整体替换为二合一模型。
     */
    private void renderCoveredContent(BasePlatableBlockEntity entity, BlockState state, MatrixStack matrices,
                                      VertexConsumerProvider vertexConsumers, int light, int overlay) {
        BakedModel lidModel = modelManager.getModel(LID_MODEL_ID);
        if (lidModel == null || lidModel == modelManager.getMissingModel()) {
            return; // 盖物品模型缺失时跳过
        }

        matrices.push();
        rotateToFacing(state, matrices);
        matrices.translate(0.0F, LID_Y_LIFT, 0.0F);
        renderBakedModel(entity, lidModel, state, matrices, vertexConsumers, light, overlay);
        matrices.pop();
    }

    // ==================== 菜肴内容渲染 ====================

    /**
     * 渲染盘面菜肴：有菜时按当前菜标识分派成品 / 食用阶段模型；模型缺失
     * （含无序菜无静态模型）时回退为无序摆盘的逐项渲染。
     */
    private void renderPlatedFood(BasePlatableBlockEntity entity, BlockState state, MatrixStack matrices,
                                  VertexConsumerProvider vertexConsumers, int light, int overlay) {
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
        rotateToFacing(state, matrices);
        renderBakedModel(entity, renderModel, state, matrices, vertexConsumers, light, overlay);
        matrices.pop();
    }

    /** 按方块水平朝向绕方块中心旋转（内容模型以朝北为默认姿态导出）。 */
    private static void rotateToFacing(BlockState state, MatrixStack matrices) {
        matrices.translate(0.5, 0, 0.5);
        float facing = state.get(BasePlatableBlock.FACING).asRotation();
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(facing));
        matrices.translate(-0.5, 0, -0.5);
    }

    /**
     * 渲染单个烘焙模型：世界形态用带剔除与种子随机的方块模型渲染；
     * 物品形态（无世界）用无世界重载，盘体之外的模型都经此渲染。
     */
    private void renderBakedModel(BasePlatableBlockEntity entity, BakedModel model, BlockState state, MatrixStack matrices,
                                  VertexConsumerProvider vertexConsumers, int light, int overlay) {
        World world = entity.getWorld();
        if (world == null) {
            // 与 renderBlockAsEntity 同取方块层，保证盘体与内容在同一渲染层内叠加
            modelRenderer.render(matrices.peek(),
                    vertexConsumers.getBuffer(RenderLayers.getBlockLayer(state)),
                    state, model, 1.0F, 1.0F, 1.0F, light, overlay);
        } else {
            modelRenderer.render(
                    world,
                    model,
                    state,
                    entity.getPos(),
                    matrices,
                    vertexConsumers.getBuffer(RenderLayer.getCutout()),
                    true,
                    Random.create(),
                    state.getRenderingSeed(entity.getPos()),
                    overlay
            );
        }
    }

    /** 物品形态：盘体不参与方块状态模型渲染，直接按本体方块模型自绘（盖盖与否盘体不变）。 */
    private void renderBodyInHand(BlockState state, MatrixStack matrices,
                                  VertexConsumerProvider vertexConsumers, int light, int overlay) {
        BlockState bodyState = state.getBlock().getDefaultState().with(BasePlatableBlock.IS_COVERED, false);
        blockRenderManager.renderBlockAsEntity(bodyState, matrices, vertexConsumers, light, overlay);
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
    private void renderGenericPlating(BasePlatableBlockEntity entity, BlockState state, MatrixStack matrices,
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
        float facing = state.get(BasePlatableBlock.FACING).asRotation();
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

            renderBakedModel(entity, model, state, matrices, vertexConsumers, light, overlay);
        }

        matrices.pop();
    }

    /** 取当前无序摆盘的操作序列：流程活动时用进行中的序列，否则取固化菜步骤链中的摆盘步骤序列。 */
    private static List<PlayerAction> collectActions(BasePlatableBlockEntity entity) {
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
