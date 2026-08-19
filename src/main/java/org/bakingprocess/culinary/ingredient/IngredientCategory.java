package org.bakingprocess.culinary.ingredient;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;

import java.util.Arrays;
import java.util.Locale;
import java.util.function.Function;

/**
 * <h1>食材分类（纯分类）</h1>
 * <p>主菜 / 配菜 / 调料 / 装饰，各自拥有独立的子分类；本接口<b>不携带任何数据字段</b>，
 * 是纯粹的类别标签，可直接用作无序配方 {@code requirements} 的 key 与匹配分组键。</p>
 * <ul>
 *     <li>{@link Main}：常规 / 大型主菜；</li>
 *     <li>{@link Side}：中型 / 小型配菜；</li>
 *     <li>{@link Seasoning}：固体 / 粉状 / 液态调料；</li>
 *     <li>{@link Decoration}：装饰（单一大类，无子类；占位/非占位由条目数据
 *         {@link IngredientData.DecorationData#maxFreeCount()} 表达）。</li>
 * </ul>
 *
 * <p>分类对应的<b>条目数据</b>见 {@link IngredientData}：按大类分派，各自持有字段与 CODEC。</p>
 *
 * @see IngredientData
 */
public sealed interface IngredientCategory
        permits IngredientCategory.Main, IngredientCategory.Side,
                IngredientCategory.Seasoning, IngredientCategory.Decoration {

    /** 主菜：常规 / 大型。 */
    record Main(Size size) implements IngredientCategory {
        public enum Size { REGULAR, LARGE }
    }

    /** 配菜：中型 / 小型（装饰已单开为大类，不再属于配菜）。 */
    record Side(Size size) implements IngredientCategory {
        public enum Size { MEDIUM, SMALL }
    }

    /** 调料：固体 / 粉状 / 液态。 */
    record Seasoning(Form form) implements IngredientCategory {
        public enum Form { SOLID, POWDER, LIQUID }
    }

    /** 装饰：单一大类，无子类；占位（0）/ 非占位（&gt;0）由条目数据表达。 */
    record Decoration() implements IngredientCategory {}

    /** 是否调料（食物属性计算的倍率中计为"调料种数"）。 */
    default boolean isSeasoning() {
        return this instanceof Seasoning;
    }

    /** 是否装饰（属性计算完全跳过、无序中为成品尾缀）。 */
    default boolean isDecoration() {
        return this instanceof Decoration;
    }

    /** 序列化 Codec：以 "大类/子类" 表示，如 {@code main/regular}、{@code seasoning/liquid}；
     * 装饰无子类，序列化为单个 {@code decoration}。 */
    Codec<IngredientCategory> CODEC = Codec.STRING.comapFlatMap(IngredientCategory::parse, IngredientCategory::stringify);

    private static DataResult<IngredientCategory> parse(String value) {
        if (value.equals("decoration")) {
            return DataResult.success(new Decoration());
        }
        String[] parts = value.split("/", 2);
        if (parts.length != 2) {
            return DataResult.error(() -> "Invalid ingredient category: " + value);
        }
        return switch (parts[0]) {
            case "main" -> parseVariant(Main.Size.class, parts[1], Main::new);
            case "side" -> parseVariant(Side.Size.class, parts[1], Side::new);
            case "seasoning" -> parseVariant(Seasoning.Form.class, parts[1], Seasoning::new);
            default -> DataResult.error(() -> "Unknown category kind: " + parts[0]);
        };
    }

    private static String stringify(IngredientCategory category) {
        if (category instanceof Main main) {
            return "main/" + main.size().name().toLowerCase(Locale.ROOT);
        }
        if (category instanceof Side side) {
            return "side/" + side.size().name().toLowerCase(Locale.ROOT);
        }
        if (category instanceof Seasoning seasoning) {
            return "seasoning/" + seasoning.form().name().toLowerCase(Locale.ROOT);
        }
        if (category instanceof Decoration) {
            return "decoration";
        }
        throw new IllegalStateException("Unknown ingredient category: " + category);
    }

    private static <E extends Enum<E>, R extends IngredientCategory> DataResult<IngredientCategory> parseVariant(
            Class<E> enumClass, String name, Function<E, R> factory) {
        return Arrays.stream(enumClass.getEnumConstants())
                .filter(variant -> variant.name().equalsIgnoreCase(name))
                .findFirst()
                .map(variant -> DataResult.<IngredientCategory>success(factory.apply(variant)))
                .orElseGet(() -> DataResult.error(() -> "Unknown category variant: " + name));
    }
}
