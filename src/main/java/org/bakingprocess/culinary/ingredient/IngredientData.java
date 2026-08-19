package org.bakingprocess.culinary.ingredient;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import org.bakingprocess.util.SimpleFoodComponent;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;

/**
 * <h1>食材条目数据（按大类分派）</h1>
 * <p>与纯分类 {@link IngredientCategory} 配套：每条食材条目携带一份本大类的数据，
 * 各子类持有自己的字段与 CODEC——主菜/配菜有生熟双属性，调料只有一个属性，
 * 装饰无食物属性、只有非占位追加上限。</p>
 *
 * <p><b>字段值条目级独立</b>：同一类别下的不同食材可拥有不同数据
 * （如 beef 与 chicken 同为 {@code main/regular}，食物属性不同）。</p>
 *
 * @see CulinaryIngredient
 * @see IngredientCategory
 */
public sealed interface IngredientData
        permits IngredientData.MainData, IngredientData.SideData,
                IngredientData.SeasoningData, IngredientData.DecorationData {

    /** 主菜数据：烤熟后（{@code cooked}）与直接吃（{@code raw}）双属性。 */
    record MainData(SimpleFoodComponent cooked, @Nullable SimpleFoodComponent raw) implements IngredientData {
        public static final Codec<MainData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                SimpleFoodComponent.CODEC.fieldOf("cooked").forGetter(MainData::cooked),
                SimpleFoodComponent.CODEC.optionalFieldOf("raw").forGetter(data -> Optional.ofNullable(data.raw))
        ).apply(instance, (cooked, raw) -> new MainData(cooked, raw.orElse(null))));
    }

    /** 配菜数据：与主菜一致，生熟双属性。 */
    record SideData(SimpleFoodComponent cooked, @Nullable SimpleFoodComponent raw) implements IngredientData {
        public static final Codec<SideData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                SimpleFoodComponent.CODEC.fieldOf("cooked").forGetter(SideData::cooked),
                SimpleFoodComponent.CODEC.optionalFieldOf("raw").forGetter(data -> Optional.ofNullable(data.raw))
        ).apply(instance, (cooked, raw) -> new SideData(cooked, raw.orElse(null))));
    }

    /** 调料数据：单属性（熟与不熟相同）。 */
    record SeasoningData(SimpleFoodComponent food) implements IngredientData {
        public static final Codec<SeasoningData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                SimpleFoodComponent.CODEC.fieldOf("food").forGetter(SeasoningData::food)
        ).apply(instance, SeasoningData::new));
    }

    /**
     * 装饰数据：无食物属性。
     *
     * @param maxFreeCount 作为<b>非占位装饰</b>时可追加的最大数量；
     *                     {@code 0} = 占位装饰（明写进配方，无追加上限语义）
     */
    record DecorationData(int maxFreeCount) implements IngredientData {
        public static final Codec<DecorationData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.INT.fieldOf("max_free_count").forGetter(DecorationData::maxFreeCount)
        ).apply(instance, DecorationData::new));

        /** 是否占位装饰（无追加语义，需配方明写）。 */
        public boolean isPlaceholder() {
            return maxFreeCount <= 0;
        }
    }

    /** 按食材分类大类返回对应的条目数据 Codec。 */
    static Codec<IngredientData> codecFor(IngredientCategory category) {
        if (category instanceof IngredientCategory.Main) {
            return MainData.CODEC.xmap(data -> (IngredientData) data, data -> (MainData) data);
        }
        if (category instanceof IngredientCategory.Side) {
            return SideData.CODEC.xmap(data -> (IngredientData) data, data -> (SideData) data);
        }
        if (category instanceof IngredientCategory.Seasoning) {
            return SeasoningData.CODEC.xmap(data -> (IngredientData) data, data -> (SeasoningData) data);
        }
        if (category instanceof IngredientCategory.Decoration) {
            return DecorationData.CODEC.xmap(data -> (IngredientData) data, data -> (DecorationData) data);
        }
        throw new IllegalArgumentException("Unknown category: " + category);
    }
}
