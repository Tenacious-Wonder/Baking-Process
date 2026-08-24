package org.bakingprocess.culinary.ingredient;

import net.minecraft.util.StringIdentifiable;

import java.util.Arrays;

/**
 * <h1>食材分类（枚举）</h1>
 * <p>主菜 / 配菜 / 调料 / 装饰及其子分类的平铺枚举；每个常量携带序列化 id
 * （如 {@code main/regular}）与所属原料大类 {@link Kind}。</p>
 *
 * <p>分类对应的<b>条目数据</b>见 {@link IngredientData}：按 {@link #kind()} 分派，
 * 各子类持有自己的字段与 CODEC（主菜/配菜生熟双属性、调料单属性、装饰仅追加上限）。</p>
 *
 * @see IngredientData
 */
public enum IngredientCategory implements StringIdentifiable {
    // ===== 主菜 =====
    MAIN_REGULAR("main/regular", Kind.MAIN),
    MAIN_LARGE("main/large", Kind.MAIN),

    // ===== 配菜 =====
    SIDE_MEDIUM("side/medium", Kind.SIDE),
    SIDE_SMALL("side/small", Kind.SIDE),

    // ===== 调料 =====
    SEASONING_SOLID("seasoning/solid", Kind.SEASONING),
    SEASONING_POWDER("seasoning/powder", Kind.SEASONING),
    SEASONING_LIQUID("seasoning/liquid", Kind.SEASONING),

    // ===== 装饰（占位/非占位由条目数据表达） =====
    DECORATION("decoration", Kind.DECORATION);

    /** 原料大类：主菜 / 配菜 / 调料 / 装饰，用于大类级约束（如无序配方中"调料总量"）。 */
    public enum Kind implements StringIdentifiable {
        MAIN("main"),
        SIDE("side"),
        SEASONING("seasoning"),
        DECORATION("decoration");

        private final String id;

        Kind(String id) {
            this.id = id;
        }

        @Override
        public String asString() {
            return id;
        }

        /** 按字符串 id 查找。 */
        public static Kind fromId(String id) {
            return Arrays.stream(values())
                    .filter(value -> value.id.equals(id))
                    .findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("Unknown ingredient kind: " + id));
        }
    }

    private final String id;
    private final Kind kind;

    IngredientCategory(String id, Kind kind) {
        this.id = id;
        this.kind = kind;
    }

    @Override
    public String asString() {
        return id;
    }

    /** 所属原料大类。 */
    public Kind kind() {
        return kind;
    }

    /** 是否调料（食物属性计算的倍率中计为"调料种数"）。 */
    public boolean isSeasoning() {
        return kind == Kind.SEASONING;
    }

    /** 是否装饰（属性计算完全跳过；无序中为成品尾缀）。 */
    public boolean isDecoration() {
        return kind == Kind.DECORATION;
    }

    /** 按字符串 id 查找（网络 / 配方序列化用）。 */
    public static IngredientCategory fromString(String value) {
        return Arrays.stream(values())
                .filter(category -> category.id.equals(value))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Invalid ingredient category: " + value));
    }

    /** 序列化 Codec：按 {@link #asString()} 的 id（非 name），常量改名不破坏数据。 */
    public static final com.mojang.serialization.Codec<IngredientCategory> CODEC =
            StringIdentifiable.createCodec(IngredientCategory::values);
}
