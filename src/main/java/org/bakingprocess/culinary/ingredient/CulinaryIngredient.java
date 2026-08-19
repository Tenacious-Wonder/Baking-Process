package org.bakingprocess.culinary.ingredient;

import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.RecordBuilder;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.util.Identifier;
import org.bakingprocess.util.SimpleFoodComponent;

/**
 * <h1>食材条目</h1>
 * <p>原料的来源、纯分类与条目数据，由食材表配置（{@code food_ingredients}）承载。</p>
 *
 * <p>数据与分类分离：{@code category} 是纯分类（可直接作无序配方 key），
 * {@code data} 是按大类分派的条目数据（{@link IngredientData}），
 * 字段值条目级独立（beef 与 chicken 同为 {@code main/regular} 但属性不同）。</p>
 *
 * @param source   原料来源
 * @param id       原料 id（物品或内容物注册表 id）
 * @param category 纯分类（主/配/调料/装饰）
 * @param data     该大类下的条目数据
 */
public record CulinaryIngredient(
        IngredientSource<?> source,
        Identifier id,
        IngredientCategory category,
        IngredientData data
) {

    /** 前三字段（source/id/category）的中间解析，data 依赖 category 需分阶段。 */
    private static final Codec<Partial> PARTIAL = RecordCodecBuilder.create(instance -> instance.group(
            IngredientSource.CODEC.fieldOf("source").forGetter(Partial::source),
            Identifier.CODEC.fieldOf("id").forGetter(Partial::id),
            IngredientCategory.CODEC.fieldOf("category").forGetter(Partial::category)
    ).apply(instance, Partial::new));

    /**
     * 食材条目的序列化 Codec：source/id/category 平级，data 按 category 大类分派。
     * <pre>{@code {
     *   "source": "item",
     *   "id": "minecraft:beef",
     *   "category": "main/regular",
     *   "data": { "cooked": {...}, "raw": {...} }
     * }}</pre>
     */
    public static final Codec<CulinaryIngredient> CODEC = new Codec<>() {
        @Override
        public <T> DataResult<T> encode(CulinaryIngredient ingredient, DynamicOps<T> ops, T prefix) {
            RecordBuilder<T> builder = ops.mapBuilder();
            builder.add("source", ingredient.source(), IngredientSource.CODEC);
            builder.add("id", ingredient.id(), Identifier.CODEC);
            builder.add("category", ingredient.category(), IngredientCategory.CODEC);
            builder.add("data", ingredient.data(), IngredientData.codecFor(ingredient.category()));
            return builder.build(prefix);
        }

        @Override
        public <T> DataResult<Pair<CulinaryIngredient, T>> decode(DynamicOps<T> ops, T input) {
            return PARTIAL.decode(ops, input).flatMap(pair -> {
                Partial partial = pair.getFirst();
                return ops.get(input, "data")
                        .flatMap(dataValue -> IngredientData.codecFor(partial.category()).parse(ops, dataValue))
                        .map(data -> Pair.of(
                                new CulinaryIngredient(partial.source(), partial.id(), partial.category(), data),
                                pair.getSecond()));
            });
        }
    };

    // ==================== 便利方法（按 data 分派） ====================

    /** 加工基准（烤熟后）食物属性；装饰无属性返回零值。 */
    public SimpleFoodComponent cooked() {
        if (data instanceof IngredientData.MainData d) {
            return d.cooked();
        }
        if (data instanceof IngredientData.SideData d) {
            return d.cooked();
        }
        if (data instanceof IngredientData.SeasoningData d) {
            return d.food();
        }
        return new SimpleFoodComponent(0, 0f);
    }

    /** 直接食用属性（{@code raw}）；未配置或装饰时返回零值。 */
    public SimpleFoodComponent rawOrNone() {
        if (data instanceof IngredientData.MainData d) {
            return d.raw() != null ? d.raw() : new SimpleFoodComponent(0, 0f);
        }
        if (data instanceof IngredientData.SideData d) {
            return d.raw() != null ? d.raw() : new SimpleFoodComponent(0, 0f);
        }
        if (data instanceof IngredientData.SeasoningData d) {
            return d.food();
        }
        return new SimpleFoodComponent(0, 0f);
    }

    /** 是否调料（食物属性计算的倍率中计为"调料种数"）。 */
    public boolean isSeasoning() {
        return category.isSeasoning();
    }

    /** 是否装饰（属性计算完全跳过；无序中为成品尾缀）。 */
    public boolean isDecoration() {
        return category.isDecoration();
    }

    /** 装饰条目的最大非占位追加数；非装饰条目返回 0。 */
    public int decorationMaxFreeCount() {
        if (data instanceof IngredientData.DecorationData decoration) {
            return decoration.maxFreeCount();
        }
        return 0;
    }

    /** 中间解析结果：source / id / category（不含 data）。 */
    private record Partial(IngredientSource<?> source, Identifier id, IngredientCategory category) {}
}
