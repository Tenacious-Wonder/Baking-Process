package org.bakingprocess.recipe;

import net.minecraft.util.Identifier;
import org.bakingprocess.config.IngredientTableData;
import org.bakingprocess.culinary.ingredient.CulinaryIngredient;
import org.bakingprocess.culinary.ingredient.IngredientCategory;
import org.bakingprocess.culinary.step.PlatingStep;
import org.bakingprocess.recipe.GenericPlatingRecipe.RequirementKey;
import org.twcore.api.process.PlayerAction;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * <h1>无序摆盘候选</h1>
 * <p>包装 {@link GenericPlatingRecipe}：按"类别 + 数量"组合匹配，不要求操作顺序。</p>
 *
 * <h2>匹配语义</h2>
 * <ul>
 *     <li>{@link #canStart} / {@link #canContinue} 是<b>可完成性</b>判定：加入原料后，
 *         规则仍有可能扩展成一份完整成品（各维度不超上限、min 可达、且满足
 *         先主配后调料再装饰的顺序约束）；</li>
 *     <li>{@link #isComplete} 判定各维度计数落在规则区间，且未列出的类别（非占位装饰除外）
 *         计数为 0；</li>
 *     <li>口数不在配方中存储，{@link #createStep} 时动态计算 = 主菜 + 非装饰配菜的数量和。</li>
 * </ul>
 */
public class GenericPlatingCandidate implements PlatingCandidate {

    private final GenericPlatingRecipe rule;

    public GenericPlatingCandidate(GenericPlatingRecipe rule) {
        this.rule = rule;
    }

    @Override
    public Identifier getId() {
        return rule.getId();
    }

    @Override
    public boolean canStart(PlayerAction first) {
        return feasible(List.of(first));
    }

    @Override
    public boolean canContinue(List<PlayerAction> actions, PlayerAction next) {
        List<PlayerAction> extended = new ArrayList<>(actions);
        extended.add(next);
        return feasible(extended);
    }

    @Override
    public boolean matchesPrefix(List<PlayerAction> actions) {
        return feasible(actions);
    }

    @Override
    public boolean isComplete(List<PlayerAction> actions) {
        // 不在食材表的原料（如把盘子当原料放入）不算合法成品
        if (hasUnknownIngredient(actions)) {
            return false;
        }
        Map<IngredientCategory, Integer> counts = count(actions);

        for (Map.Entry<RequirementKey, GenericPlatingRecipe.CountRange> entry : rule.getRequirements().entrySet()) {
            if (!entry.getValue().contains(countOf(entry.getKey(), counts))) {
                return false;
            }
        }
        // 未列出的类别禁止放入：任何计数 > 0 的非装饰类别必须被某维度覆盖
        for (Map.Entry<IngredientCategory, Integer> entry : counts.entrySet()) {
            if (entry.getValue() > 0 && !entry.getKey().isDecoration() && !isCovered(entry.getKey())) {
                return false;
            }
        }
        return true;
    }

    /**
     * 冗余维度数：规则声明的维度中当前计数为 0 的数量。
     * 完成时多个无序候选同时满足时取冗余最少者（"最直接"）。
     */
    public int redundantDimensions(List<PlayerAction> actions) {
        Map<IngredientCategory, Integer> counts = count(actions);
        int redundant = 0;
        for (RequirementKey key : rule.getRequirements().keySet()) {
            if (countOf(key, counts) == 0) {
                redundant++;
            }
        }
        return redundant;
    }

    @Override
    public PlatingStep createStep(List<PlayerAction> actualActions) {
        return new PlatingStep(actualActions, rule.getDishName(), dynamicEatCount(actualActions),
                rule.isEdible(), true);
    }

    // ==================== 可完成性 ====================

    /** 加入原料后，规则是否仍可能扩展成完整成品（不超上限、min 可达、顺序允许）。 */
    private boolean feasible(List<PlayerAction> actions) {
        // 不在食材表的原料（如把盘子当原料放入）直接拒绝
        if (hasUnknownIngredient(actions)) {
            return false;
        }
        Map<IngredientCategory, Integer> counts = count(actions);
        Stage stage = stageOf(actions);

        // 未列出的类别禁止放入：任何计数 > 0 的非装饰类别必须被某维度覆盖
        for (Map.Entry<IngredientCategory, Integer> entry : counts.entrySet()) {
            if (entry.getValue() > 0 && !entry.getKey().isDecoration() && !isCovered(entry.getKey())) {
                return false;
            }
        }

        for (Map.Entry<RequirementKey, GenericPlatingRecipe.CountRange> entry : rule.getRequirements().entrySet()) {
            RequirementKey key = entry.getKey();
            GenericPlatingRecipe.CountRange range = entry.getValue();
            int current = countOf(key, counts);

            // 超出上限：不可完成
            if (current > range.max()) {
                return false;
            }
            // 未达下限：必须还能继续放该类原料（阶段允许 + 未到上限）
            if (current < range.min() && !canAddMore(key, counts, stage)) {
                return false;
            }
        }
        return true;
    }

    /** 该维度还能否继续放入（阶段窗口允许且未超上限）。 */
    private boolean canAddMore(RequirementKey key, Map<IngredientCategory, Integer> counts, Stage stage) {
        if (countOf(key, counts) >= maxOf(key)) {
            return false;
        }
        return switch (stage) {
            case DECORATION -> isDecorationKey(key);        // 已放装饰：只能再放装饰
            case SEASONING -> !isMainSideKey(key);          // 已放调料：可放调料或装饰
            case MAIN_SIDE -> true;                         // 未放调料/装饰：任何类别
        };
    }

    private int maxOf(RequirementKey key) {
        return rule.getRequirements().get(key).max();
    }

    // ==================== 类别统计 ====================

    /** 序列中是否存在不在食材表的原料（非合法食材，如把盘子当原料放入）。 */
    private static boolean hasUnknownIngredient(List<PlayerAction> actions) {
        for (PlayerAction action : actions) {
            if (IngredientTableData.current().findFor(action) == null) {
                return true;
            }
        }
        return false;
    }

    /** 把操作序列映射为类别计数；非占位装饰（有追加上限）不计入任何维度。 */
    private Map<IngredientCategory, Integer> count(List<PlayerAction> actions) {
        Map<IngredientCategory, Integer> counts = new EnumMap<>(IngredientCategory.class);
        for (PlayerAction action : actions) {
            CulinaryIngredient ingredient = IngredientTableData.current().findFor(action);
            if (ingredient == null || (ingredient.isDecoration() && ingredient.decorationMaxFreeCount() > 0)) {
                continue;
            }
            counts.merge(ingredient.category(), 1, Integer::sum);
        }
        return counts;
    }

    /** 维度当前计数：子类级取该分类计数；大类级取该大类所有子类计数之和。 */
    private static int countOf(RequirementKey key, Map<IngredientCategory, Integer> counts) {
        if (key instanceof RequirementKey.Exact exact) {
            return counts.getOrDefault(exact.category(), 0);
        }
        if (key instanceof RequirementKey.Group group) {
            int sum = 0;
            for (IngredientCategory category : IngredientCategory.values()) {
                if (category.kind() == group.kind()) {
                    sum += counts.getOrDefault(category, 0);
                }
            }
            return sum;
        }
        return 0;
    }

    /** 计数为正的类别是否被某维度覆盖（子类级或所属大类级）。 */
    private boolean isCovered(IngredientCategory category) {
        for (RequirementKey key : rule.getRequirements().keySet()) {
            if (key instanceof RequirementKey.Exact exact && exact.category() == category) {
                return true;
            }
            if (key instanceof RequirementKey.Group group && group.kind() == category.kind()) {
                return true;
            }
        }
        return false;
    }

    // ==================== 顺序阶段 ====================

    /** 摆放阶段：主配 → 调料 → 装饰，仅允许按此顺序推进。 */
    private enum Stage { MAIN_SIDE, SEASONING, DECORATION }

    /** 由已放原料推导当前阶段（出现调料进入调料阶段，出现装饰进入装饰阶段）。 */
    private static Stage stageOf(List<PlayerAction> actions) {
        boolean seasoning = false;
        boolean decoration = false;
        for (PlayerAction action : actions) {
            CulinaryIngredient ingredient = IngredientTableData.current().findFor(action);
            if (ingredient == null) {
                continue;
            }
            if (ingredient.isDecoration()) {
                decoration = true;
            } else if (ingredient.category().kind() == IngredientCategory.Kind.SEASONING) {
                seasoning = true;
            }
        }
        if (decoration) {
            return Stage.DECORATION;
        }
        if (seasoning) {
            return Stage.SEASONING;
        }
        return Stage.MAIN_SIDE;
    }

    private static boolean isDecorationKey(RequirementKey key) {
        return key instanceof RequirementKey.Exact exact && exact.category().isDecoration()
                || key instanceof RequirementKey.Group group && group.kind() == IngredientCategory.Kind.DECORATION;
    }

    private static boolean isMainSideKey(RequirementKey key) {
        if (key instanceof RequirementKey.Exact exact) {
            return exact.category().kind() == IngredientCategory.Kind.MAIN
                    || exact.category().kind() == IngredientCategory.Kind.SIDE;
        }
        if (key instanceof RequirementKey.Group group) {
            return group.kind() == IngredientCategory.Kind.MAIN || group.kind() == IngredientCategory.Kind.SIDE;
        }
        return false;
    }

    // ==================== 动态口数 ====================

    /** 口数 = 主菜 + 非装饰配菜的数量和（调料、装饰不计）。 */
    private static int dynamicEatCount(List<PlayerAction> actions) {
        int count = 0;
        for (PlayerAction action : actions) {
            CulinaryIngredient ingredient = IngredientTableData.current().findFor(action);
            if (ingredient == null || ingredient.isDecoration()) {
                continue;
            }
            IngredientCategory.Kind kind = ingredient.category().kind();
            if (kind == IngredientCategory.Kind.MAIN || kind == IngredientCategory.Kind.SIDE) {
                count++;
            }
        }
        return count;
    }
}
