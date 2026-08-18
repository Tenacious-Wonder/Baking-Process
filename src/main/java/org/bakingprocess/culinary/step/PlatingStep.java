package org.bakingprocess.culinary.step;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.world.World;
import org.bakingprocess.config.IngredientTableData;
import org.bakingprocess.culinary.ingredient.DishFoodCalculator;
import org.bakingprocess.registry.ModProcessingTypes;
import org.bakingprocess.util.SimpleFoodComponent;
import org.twcore.api.process.PlayerAction;

import java.util.ArrayList;
import java.util.List;

/**
 * 摆盘加工步骤：记录一次摆盘经历，并承载配方给出的菜标识、口数与可食性。
 *
 * <p>PlatingStep 保存摆盘配方的全部数据（操作序列、目标菜标识、口数、是否摆完即食），
 * 是这道菜的<b>第一步历史</b>，无既有历史可消化（{@link #onAdded} 保持默认空实现）。</p>
 *
 * <p><b>生熟语义：</b></p>
 * <ul>
 *     <li>{@code edible=false}（默认）：摆完是<b>生菜</b>，不可食（{@link #isEdible()} 为假、
 *         {@link #getTotalEats()} 为 0），{@code eatCount} 是<b>目标熟菜口数</b>，预留给
 *         后续 {@code BakingStep} 推导；</li>
 *     <li>{@code edible=true}：摆完<b>直接可食</b>，{@code eatCount} 就是真实口数，
 *         吃的时候用原料的"直接吃"属性（{@code raw}）经 {@link DishFoodCalculator} 计算。</li>
 * </ul>
 */
public class PlatingStep extends ProcessingStep {

    /** PlatingStep 的序列化 Codec。 */
    public static final Codec<PlatingStep> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.list(Codec.STRING).xmap(PlatingStep::parseActions, PlatingStep::stringifyActions)
                    .fieldOf("actions").forGetter(PlatingStep::getActions),
            Identifier.CODEC.fieldOf("id").forGetter(PlatingStep::getIdentifier),
            Codec.INT.fieldOf("eat_count").forGetter(PlatingStep::getEatCount),
            Codec.BOOL.optionalFieldOf("edible", false).forGetter(PlatingStep::isEdible)
    ).apply(instance, PlatingStep::new));

    // ==================== 字段 ====================

    /** 摆盘操作序列（放入了什么原料、按什么顺序）。 */
    private final List<PlayerAction> actions;
    /** 目标菜标识：显示名本地化派生与渲染模型分派。 */
    private final Identifier identifier;
    /** 目标菜口数（edible=false 时为预留，edible=true 时为真实口数）。 */
    private final int eatCount;
    /** 摆完是否直接可食。 */
    private final boolean edible;

    /**
     * @param actions    摆盘操作序列
     * @param identifier 目标菜标识
     * @param eatCount   目标菜口数
     * @param edible     摆完是否直接可食
     */
    public PlatingStep(List<PlayerAction> actions, Identifier identifier, int eatCount, boolean edible) {
        this.actions = List.copyOf(actions);
        this.identifier = identifier;
        this.eatCount = eatCount;
        this.edible = edible;
    }

    // ==================== ProcessingStep 实现 ====================

    @Override
    public boolean isEdible() {
        return edible;
    }

    @Override
    public Text getDisplayName() {
        return Text.translatable(identifier.toTranslationKey());
    }

    @Override
    public ProcessingType<?> getType() {
        return ModProcessingTypes.PLATING;
    }

    @Override
    public int getTotalEats() {
        return edible ? eatCount : 0;
    }

    @Override
    public void eat(PlayerEntity player, World world, int currentBite) {
        if (!edible || eatCount <= 0) {
            return;
        }
        // 可直接食用的摆盘菜：用原料的"直接吃"属性（raw）计算
        SimpleFoodComponent full = DishFoodCalculator.calculate(
                IngredientTableData.current().fromActions(actions), true);
        DishFoodCalculator.applyBite(player, full, eatCount, currentBite);
    }

    // ==================== 访问器 ====================

    /** 摆盘操作序列（不可修改）。 */
    public List<PlayerAction> getActions() {
        return actions;
    }

    /** 目标菜标识。 */
    public Identifier getIdentifier() {
        return identifier;
    }

    /** 目标菜口数。 */
    public int getEatCount() {
        return eatCount;
    }

    // ==================== Codec 辅助 ====================

    private static List<PlayerAction> parseActions(List<String> strings) {
        List<PlayerAction> actions = new ArrayList<>();
        for (String string : strings) {
            actions.add(PlayerAction.fromString(string));
        }
        return actions;
    }

    private static List<String> stringifyActions(List<PlayerAction> actions) {
        List<String> strings = new ArrayList<>();
        for (PlayerAction action : actions) {
            strings.add(action.toString());
        }
        return strings;
    }
}
