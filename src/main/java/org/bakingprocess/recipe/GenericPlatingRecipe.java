package org.bakingprocess.recipe;

import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import net.minecraft.recipe.Recipe;
import net.minecraft.recipe.RecipeSerializer;
import net.minecraft.recipe.RecipeType;
import net.minecraft.registry.DynamicRegistryManager;
import net.minecraft.util.Identifier;
import net.minecraft.util.StringIdentifiable;
import net.minecraft.world.World;
import org.bakingprocess.culinary.ingredient.IngredientCategory;
import org.bakingprocess.registry.ModRecipeSerializers;
import org.bakingprocess.registry.ModRecipeTypes;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * <h1>无序通用摆盘配方</h1>
 * <p>按"类别 + 数量"组合匹配的配方：不要求精确操作序列，只要求放入的原料在
 * 类别与数量上满足 {@link RequirementKey} 声明的区间。与定序摆盘（{@link PlatingRecipe}）
 * 相对，匹配语义由 {@code GenericPlatingCandidate}（可完成性 + 顺序约束）实现，
 * 本类仅作为数据包承载与注册入口。</p>
 *
 * <h2>产物</h2>
 * <ul>
 *     <li>同大类的配方共享目标菜标识 {@code dish_name}（如 {@code baking_process:generic/raw}）；</li>
 *     <li>{@code edible} 由配方声明（默认 false = 生菜，需后续烘焙）；</li>
 *     <li>口数不在配方中存储，由 {@code GenericPlatingCandidate.getEatCount} 动态计算
 *         （主菜 + 非装饰配菜的数量和）。</li>
 * </ul>
 */
public class GenericPlatingRecipe implements Recipe<Inventory> {

    /** 配方 id。 */
    private final Identifier id;
    /** 容器标识（如 {@code baking_process:iron_plate}）。 */
    private final Identifier containerId;
    /** 归属大类（原始 / 调味 / 小食）。 */
    private final DishClass dishClass;
    /** 成品菜标识（同大类共享）。 */
    private final Identifier dishName;
    /** 摆完是否直接可食。 */
    private final boolean edible;
    /** 类别约束维度 → 数量区间；未列出的维度禁止放入（非占位装饰除外）。 */
    private final Map<RequirementKey, CountRange> requirements;

    public GenericPlatingRecipe(Identifier id, Identifier containerId, DishClass dishClass,
                                Identifier dishName, boolean edible,
                                Map<RequirementKey, CountRange> requirements) {
        this.id = id;
        this.containerId = containerId;
        this.dishClass = dishClass;
        this.dishName = dishName;
        this.edible = edible;
        this.requirements = Map.copyOf(requirements);
    }

    public Identifier getContainerId() {
        return containerId;
    }

    public DishClass getDishClass() {
        return dishClass;
    }

    public Identifier getDishName() {
        return dishName;
    }

    public boolean isEdible() {
        return edible;
    }

    /** 类别约束维度 → 数量区间（不可修改视图，构造时已拷贝）。 */
    public Map<RequirementKey, CountRange> getRequirements() {
        return requirements;
    }

    // ==================== Recipe 实现（匹配由候选实现，本类不参与 Inventory 匹配） ====================

    @Override
    public boolean matches(Inventory inventory, World world) {
        return false;
    }

    @Override
    public ItemStack craft(Inventory inventory, DynamicRegistryManager registryManager) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean fits(int width, int height) {
        return true;
    }

    @Override
    public ItemStack getOutput(DynamicRegistryManager registryManager) {
        return ItemStack.EMPTY;
    }

    @Override
    public Identifier getId() {
        return id;
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return ModRecipeSerializers.GENERIC_PLATING;
    }

    @Override
    public RecipeType<?> getType() {
        return ModRecipeTypes.GENERIC_PLATING;
    }

    // ==================== 嵌套类型 ====================

    /**
     * <h1>无序摆盘大类</h1>
     * <p>无序通用配方的归属大类：原始 / 调味 / 小食。同大类的配方共享目标菜标识
     * （{@code baking_process:generic/raw|seasoned|snack}）与产物语义。</p>
     */
    public enum DishClass implements StringIdentifiable {
        /** 原始型：无需调料。 */
        RAW("raw"),
        /** 调味型：原始型基础上加调料。 */
        SEASONED("seasoned"),
        /** 小食型：无需主食。 */
        SNACK("snack");

        private final String id;

        DishClass(String id) {
            this.id = id;
        }

        @Override
        public String asString() {
            return id;
        }

        /** 按字符串 id 查找。 */
        public static DishClass fromId(String id) {
            return Arrays.stream(values())
                    .filter(value -> value.id.equals(id))
                    .findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("Unknown dish class: " + id));
        }

        /** 序列化 Codec：以小写 id 编码。 */
        public static final com.mojang.serialization.Codec<DishClass> CODEC =
                StringIdentifiable.createCodec(DishClass::values);
    }

    /**
     * <h1>数量区间</h1>
     * <p>无序配方 {@code requirements} 中某类别的允许数量范围：固定值 {@code n} 等价
     * {@code [n, n]}。Codec 支持整数或二元数组两种形态。</p>
     *
     * @param min 最小数量
     * @param max 最大数量
     */
    public record CountRange(int min, int max) {

        public CountRange {
            if (min < 0 || max < min) {
                throw new IllegalArgumentException("Invalid count range: [" + min + ", " + max + "]");
            }
        }

        /** 固定数量区间。 */
        public static CountRange fixed(int n) {
            return new CountRange(n, n);
        }

        /** 数量是否落在区间内。 */
        public boolean contains(int count) {
            return count >= min && count <= max;
        }

        /** 序列化 Codec：整数 = 固定数量；二元数组 = [min, max]。 */
        public static final Codec<CountRange> CODEC = Codec.either(
                Codec.INT,
                Codec.INT.listOf().comapFlatMap(
                        list -> list.size() == 2
                                ? DataResult.success(new CountRange(list.get(0), list.get(1)))
                                : DataResult.error(() -> "CountRange requires exactly 2 elements: [min, max]"),
                        range -> List.of(range.min(), range.max())
                )
        ).xmap(
                either -> either.map(CountRange::fixed, range -> range),
                range -> range.min() == range.max() ? Either.left(range.min()) : Either.right(range)
        );
    }

    /**
     * <h1>requirements 约束维度 key</h1>
     * <p>无序配方的类别约束既可以是<b>子类级</b>（如 {@code side/medium}，精确到子类），
     * 也可以是<b>大类级</b>（如 {@code seasoning}，统计该大类所有子类数量之和）。
     * 本类型统一两种形态：{@link Exact} 对应子类级，{@link Group} 对应大类级。</p>
     */
    public sealed interface RequirementKey {

        /** 子类级：精确到某一分类（如 {@code main/regular}）。 */
        record Exact(IngredientCategory category) implements RequirementKey {}

        /** 大类级：统计某一原料大类的全部条目数量（如 {@code seasoning} 含三种形态调料）。 */
        record Group(IngredientCategory.Kind kind) implements RequirementKey {}

        /** 序列化字符串：子类级为分类字符串，大类级为大类 id。 */
        default String asString() {
            if (this instanceof Exact exact) {
                return exact.category().asString();
            }
            if (this instanceof Group group) {
                return group.kind().asString();
            }
            throw new IllegalStateException("Unknown requirement key: " + this);
        }

        /** 按字符串解析：含 {@code /} 视为子类级，否则视为大类级。 */
        static RequirementKey fromString(String value) {
            if (value.contains("/")) {
                return new Exact(IngredientCategory.fromString(value));
            }
            return new Group(IngredientCategory.Kind.fromId(value));
        }
    }
}
