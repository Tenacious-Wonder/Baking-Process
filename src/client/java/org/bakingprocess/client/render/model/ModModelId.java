package org.bakingprocess.client.render.model;

import net.minecraft.client.util.ModelIdentifier;
import net.minecraft.item.Item;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import org.bakingprocess.BakingProcess;
import org.bakingprocess.content.ShapedDoughContent;
import org.twcore.registry.TWRegistries;

import java.util.Objects;

/**
 * 模型标识符工厂：集中提供本模组渲染所需的模型标识符，供渲染器与 {@link ModModelRules} 复用。
 * <p>模型的实际加载由 {@link ModModelRules} 通过 TW Core 的 {@link org.twcore.client.api.render.ModelRule}
 * API 与配置影响器完成，本类只负责标识符的命名规则。</p>
 */
public final class ModModelId {
    /** 菜刀插在案板上的效果模型。 */
    public static final Identifier BOARD_KITCHEN_KNIFE = new Identifier(BakingProcess.MOD_ID, "other/on_board_kitchen_knife");

    private ModModelId() {
    }

    /** 烘烤食物模型：{@code baking_process:other/{blockPath}_cooking_{foodValue}}。 */
    public static Identifier createCookingModelId(String blockPath, int foodValue) {
        String modelPath = blockPath + "_cooking_" + foodValue;
        return new Identifier(BakingProcess.MOD_ID, "other/" + modelPath);
    }

    /** 物品模型（inventory 变体）：{@code baking_process:item/{itemPath}#inventory}。 */
    public static ModelIdentifier createItemModelId(String itemPath) {
        return new ModelIdentifier(new Identifier(BakingProcess.MOD_ID, itemPath), "inventory");
    }

    /** 流程模型：{@code baking_process:process/{blockPath}}。 */
    public static Identifier createProcessModelId(String blockPath) {
        return new Identifier(BakingProcess.MOD_ID, "process/" + blockPath);
    }

    /** 切割模型：{@code baking_process:process/cut_{namespace}_{itemPath}_{cutCount}}。 */
    public static Identifier createCuttingModelId(Identifier itemId, int cutCount) {
        String modelPath = String.format("cut_%s_%s_%d",
                itemId.getNamespace(), itemId.getPath(), cutCount);
        return new Identifier(BakingProcess.MOD_ID, "process/" + modelPath);
    }

    /** 菜肴放置模型：{@code baking_process:dishes/{containerPath}_{dishPath}}。 */
    public static Identifier createDishesModelId(Item baseContainer, Identifier dishId) {
        String containerId = Registries.ITEM.getId(baseContainer).getPath();
        return new Identifier(BakingProcess.MOD_ID, "dishes/" + containerId + "_" + dishId.getPath());
    }

    /** 食用阶段模型：{@code baking_process:dishes/eat/{containerPath}_{dishPath}_{eatenCount}}。 */
    public static Identifier createEatStageModelId(Item container, Identifier dishId, int eatenCount) {
        String containerPath = Registries.ITEM.getId(container).getPath();
        return new Identifier(BakingProcess.MOD_ID, "dishes/eat/" + containerPath + "_" + dishId.getPath() + "_" + eatenCount);
    }

    /** 定型面团模型：{@code {namespace}:block/{path}}。 */
    public static Identifier createShapedDoughModelId(ShapedDoughContent content) {
        Identifier contentId = Objects.requireNonNull(TWRegistries.CONTENT.getId(content));
        return new Identifier(contentId.getNamespace(), "block/" + contentId.getPath());
    }
}
