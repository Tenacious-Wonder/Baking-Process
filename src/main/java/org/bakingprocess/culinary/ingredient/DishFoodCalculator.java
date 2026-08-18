package org.bakingprocess.culinary.ingredient;

import net.minecraft.entity.player.PlayerEntity;
import org.bakingprocess.util.SimpleFoodComponent;

import java.util.List;

/**
 * 菜肴食物属性计算器。
 *
 * <p>实现"食物属性计算"文档的机制：饥饿值纯累加，饱和度累加后乘<b>衰减倍率</b>，
 * 倍率由食材种类数与调料种类数决定（{@code M = 1 + x/(1+x)}），
 * 保证简单菜式数值克制、豪华菜式有回报但不会无限膨胀。</p>
 */
public final class DishFoodCalculator {

    private DishFoodCalculator() {}

    /**
     * 计算一组食材的最终食物属性。
     *
     * @param ingredients 食材列表（由摆盘操作序列映射而来）
     * @param useRaw      是否使用"直接吃"属性（可食用的摆盘配方）；否则使用"烤熟后"属性（加工基准）
     * @return 最终食物属性（饥饿值整数，饱和度保留两位小数）
     */
    public static SimpleFoodComponent calculate(List<CulinaryIngredient> ingredients, boolean useRaw) {
        int hunger = 0;
        float saturation = 0f;
        for (CulinaryIngredient ingredient : ingredients) {
            SimpleFoodComponent values = useRaw ? ingredient.rawOrNone() : ingredient.cooked();
            hunger += values.Hunger();
            saturation += values.SaturationModifier();
        }

        // 倍率：按"种类"（去重）计数，不按数量
        long ingredientKinds = ingredients.stream()
                .filter(ingredient -> !ingredient.isSeasoning())
                .map(CulinaryIngredient::id)
                .distinct()
                .count();
        long seasoningKinds = ingredients.stream()
                .filter(CulinaryIngredient::isSeasoning)
                .map(CulinaryIngredient::id)
                .distinct()
                .count();

        double x = 0.1 * ingredientKinds + 0.05 * seasoningKinds;
        double multiplier = 1 + x / (1 + x);
        float finalSaturation = (float) Math.round(saturation * multiplier * 100) / 100f;

        return new SimpleFoodComponent(hunger, finalSaturation);
    }

    /**
     * 按"每口等比例"施加一次吃下的食物值。
     *
     * @param player      吃的玩家
     * @param full        这道菜的完整食物属性
     * @param totalEats   总口数
     * @param currentBite 本次是第几口（保留参数，等比例实现暂不差异化）
     */
    public static void applyBite(PlayerEntity player, SimpleFoodComponent full, int totalEats, int currentBite) {
        if (totalEats <= 0) {
            return;
        }
        int percentPerEat = (int) Math.round(100.0 / totalEats);
        full.percent(percentPerEat).eat(player);
    }
}
