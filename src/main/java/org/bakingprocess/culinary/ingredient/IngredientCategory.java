package org.bakingprocess.culinary.ingredient;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;

import java.util.Arrays;
import java.util.Locale;
import java.util.function.Function;

/**
 * 食材分类：主菜 / 配菜 / 调料，各自拥有独立的子分类。
 *
 * <ul>
 *     <li>{@link Main}：常规 / 大型主菜；</li>
 *     <li>{@link Side}：中型 / 小型 / 装饰配菜；</li>
 *     <li>{@link Seasoning}：固体 / 粉状 / 液态调料。</li>
 * </ul>
 */
public sealed interface IngredientCategory
        permits IngredientCategory.Main, IngredientCategory.Side, IngredientCategory.Seasoning {

    /** 主菜：常规 / 大型。 */
    record Main(Size size) implements IngredientCategory {
        public enum Size { REGULAR, LARGE }
    }

    /** 配菜：中型 / 小型 / 装饰。 */
    record Side(Size size) implements IngredientCategory {
        public enum Size { MEDIUM, SMALL, DECORATIVE }
    }

    /** 调料：固体 / 粉状 / 液态。 */
    record Seasoning(Form form) implements IngredientCategory {
        public enum Form { SOLID, POWDER, LIQUID }
    }

    /** 是否调料（食物属性计算的倍率中计为"调料种数"）。 */
    default boolean isSeasoning() {
        return this instanceof Seasoning;
    }

    /** 序列化 Codec：以 "大类/子类" 表示，如 {@code main/regular}、{@code seasoning/liquid}。 */
    Codec<IngredientCategory> CODEC = Codec.STRING.comapFlatMap(IngredientCategory::parse, IngredientCategory::stringify);

    private static DataResult<IngredientCategory> parse(String value) {
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
