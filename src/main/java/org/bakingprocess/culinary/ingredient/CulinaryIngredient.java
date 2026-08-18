package org.bakingprocess.culinary.ingredient;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.util.Identifier;
import org.bakingprocess.util.SimpleFoodComponent;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;

/**
 * <h1>食材条目</h1>
 * <p>原料的来源、分类与食物属性，由食材表配置（{@code food_ingredients}）承载。</p>
 *
 * <h2>食物属性</h2>
 * <ul>
 *     <li>{@link #cooked()}：<b>烤熟后</b>属性（加工基准，必填）；</li>
 *     <li>{@link #raw()}：<b>直接吃</b>属性（可选，缺失 = 不可直接吃）；
 *         可直接食用的摆盘配方用 {@code raw} 计算。</li>
 * </ul>
 *
 * @param source   原料来源
 * @param id       原料 id（物品或内容物注册表 id）
 * @param category 食材分类（含大类与子分类）
 * @param cooked   烤熟后的食物属性
 * @param raw      直接吃的食物属性（可为 {@code null}）
 */
public record CulinaryIngredient(
        IngredientSource<?> source,
        Identifier id,
        IngredientCategory category,
        SimpleFoodComponent cooked,
        @Nullable SimpleFoodComponent raw
) {

    public static final Codec<CulinaryIngredient> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            IngredientSource.CODEC.fieldOf("source").forGetter(CulinaryIngredient::source),
            Identifier.CODEC.fieldOf("id").forGetter(CulinaryIngredient::id),
            IngredientCategory.CODEC.fieldOf("category").forGetter(CulinaryIngredient::category),
            SimpleFoodComponent.CODEC.fieldOf("cooked").forGetter(CulinaryIngredient::cooked),
            SimpleFoodComponent.CODEC.optionalFieldOf("raw").forGetter(ingredient -> Optional.ofNullable(ingredient.raw))
    ).apply(instance, (source, id, category, cooked, raw) ->
            new CulinaryIngredient(source, id, category, cooked, raw.orElse(null))));

    /** 直接食用属性；未配置（raw 缺失）时返回零值。 */
    public SimpleFoodComponent rawOrNone() {
        return raw != null ? raw : new SimpleFoodComponent(0, 0f);
    }

    /** 是否调料（食物属性计算的倍率中计为"调料种数"）。 */
    public boolean isSeasoning() {
        return category.isSeasoning();
    }
}
