package org.bakingprocess.config;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtOps;
import net.minecraft.util.Identifier;
import org.bakingprocess.BakingProcess;
import org.bakingprocess.culinary.ingredient.CulinaryIngredient;
import org.bakingprocess.culinary.ingredient.IngredientCategory;
import org.bakingprocess.culinary.ingredient.IngredientSource;
import org.bakingprocess.util.SimpleFoodComponent;
import org.jetbrains.annotations.Nullable;
import org.twcore.api.config.ConfigInfluencer;
import org.twcore.api.config.TwConfig;
import org.twcore.api.process.PlayerAction;

import java.util.ArrayList;
import java.util.List;

/**
 * <h1>食材表数据</h1>
 * <p>原料集合（来源 + id + 分类 + 食物属性），供摆盘操作查询与食物属性计算使用。</p>
 *
 * <h2>数据来源</h2>
 * <ul>
 *     <li>内置基础食材集 {@link #DEFAULT}；</li>
 *     <li>{@link #withInfluencers} 聚合各影响器注入的额外食材。</li>
 * </ul>
 * <p>聚合结果由 {@link ModConfigs#FOOD_INGREDIENTS} 配置承载，
 * 运行时用 {@link #current()} 读取当前生效值。</p>
 *
 * <h2>方法一览</h2>
 * <ul>
 *     <li>{@link #CODEC}：食材表的序列化 Codec（配置持久化）；</li>
 *     <li>{@link #DEFAULT}：内置默认食材集（本模组的基础数据）；</li>
 *     <li>{@link #current()}：读取当前生效的食材表，配置缺失时回退 {@link #DEFAULT}；</li>
 *     <li>{@link #withInfluencers}：配置默认值工厂，以 {@link #DEFAULT} 为基底聚合影响器负载；</li>
 *     <li>{@link #findFor}：按摆盘操作（{@code add_item}/{@code add_content}）查询原料；</li>
 *     <li>{@link #find}：按"来源 + id"查询原料；</li>
 *     <li>{@link #fromActions}：把操作序列映射成原料列表，查不到的忽略。</li>
 * </ul>
 *
 * <h2>影响器 NBT 结构</h2>
 * <p>影响器负载为 NbtList，每项是 {@link CulinaryIngredient} 的 NBT 编码：</p>
 * <pre>{@code  {
 *   // "item"（物品）| "content"（内容物）
 *   "source": "item",
 *   // 物品或内容物注册表 id
 *   "id": "minecraft:beef",
 *   // main/… | side/… | seasoning/…
 *   "category": "main/regular",
 *   // 烤熟后属性，必填
 *   "cooked": { "hunger": 8, "saturation": 0.8 },
 *   // 直接吃属性，可选；缺失 = 不可直接吃
 *   "raw": { "hunger": 3, "saturation": 0.3 }
 * }}
 * </pre>
 */
public record IngredientTableData(List<CulinaryIngredient> ingredients) {

    public static final Codec<IngredientTableData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.list(CulinaryIngredient.CODEC).fieldOf("ingredients").forGetter(IngredientTableData::ingredients)
    ).apply(instance, IngredientTableData::new));

    public static final IngredientTableData DEFAULT = new IngredientTableData(List.of(
            // ===== 主菜（常规） =====
            item("minecraft:beef", main(IngredientCategory.Main.Size.REGULAR), food(8, 0.8f), food(3, 0.3f)),
            item("minecraft:porkchop", main(IngredientCategory.Main.Size.REGULAR), food(8, 0.8f), food(3, 0.3f)),
            item("minecraft:chicken", main(IngredientCategory.Main.Size.REGULAR), food(6, 0.6f), food(2, 0.3f)),
            item("minecraft:mutton", main(IngredientCategory.Main.Size.REGULAR), food(6, 0.8f), food(2, 0.3f)),
            item("minecraft:rabbit", main(IngredientCategory.Main.Size.REGULAR), food(5, 0.6f), food(3, 0.3f)),
            item("minecraft:cod", main(IngredientCategory.Main.Size.REGULAR), food(5, 0.6f), food(2, 0.1f)),
            item("minecraft:salmon", main(IngredientCategory.Main.Size.REGULAR), food(6, 0.8f), food(2, 0.1f)),

            // ===== 配菜 =====
            item("minecraft:potato", side(IngredientCategory.Side.Size.SMALL), food(5, 0.6f), food(1, 0.3f)),
            item("minecraft:carrot", side(IngredientCategory.Side.Size.SMALL), food(6, 1.2f), food(3, 0.6f)),
            item("minecraft:brown_mushroom", side(IngredientCategory.Side.Size.SMALL), food(6, 0.6f), null),
            item("minecraft:red_mushroom", side(IngredientCategory.Side.Size.SMALL), food(6, 0.6f), null),
            item("minecraft:sweet_berries", side(IngredientCategory.Side.Size.SMALL), food(2, 0.1f), food(2, 0.1f)),
            item("minecraft:glow_berries", side(IngredientCategory.Side.Size.SMALL), food(2, 0.1f), food(2, 0.1f)),

            // ===== 调料 =====
            seasoning("baking_process:salt_flour", IngredientCategory.Seasoning.Form.POWDER, food(0, 0.05f), food(0, 0.05f)),
            seasoning("baking_process:salt_cubes", IngredientCategory.Seasoning.Form.SOLID, food(0, 0.05f), food(0, 0.05f)),
            seasoning("minecraft:honey_bottle", IngredientCategory.Seasoning.Form.LIQUID, food(6, 0.1f), food(6, 0.1f))
    ));

    /** 读取当前生效的食材表 */
    public static IngredientTableData current() {
        IngredientTableData data = TwConfig.get(BakingProcess.MOD_ID, ModConfigs.FOOD_INGREDIENTS);
        return data != null ? data : DEFAULT;
    }

    /** 配置默认值工厂：以 {@link #DEFAULT} 为基底，聚合各影响器负载中的额外食材。 */
    public static IngredientTableData withInfluencers(List<ConfigInfluencer<?>> influencers) {
        List<CulinaryIngredient> defaults = new ArrayList<>(DEFAULT.ingredients());
        for (ConfigInfluencer<?> influencer : influencers) {
            if (influencer.payload() instanceof NbtList list) {
                for (NbtElement element : list) {
                    if (element instanceof NbtCompound compound) {
                        CulinaryIngredient.CODEC
                                .parse(NbtOps.INSTANCE, compound)
                                .resultOrPartial(error -> BakingProcess.LOGGER.warn("Invalid ingredient influencer: {}", error))
                                .ifPresent(defaults::add);
                    }
                }
            }
        }
        return new IngredientTableData(defaults);
    }

    /** 查询一个操作对应的原料数据；操作不属于任何已注册来源或原料未登记时返回 {@code null}。 */
    @Nullable
    public CulinaryIngredient findFor(PlayerAction action) {
        for (IngredientSource<?> source : IngredientSource.values()) {
            Identifier id = source.extractId(action);
            if (id != null) {
                return find(source, id);
            }
        }
        return null;
    }

    /** 把操作序列映射成原料列表；查不到的原料会被忽略。 */
    public List<CulinaryIngredient> fromActions(List<PlayerAction> actions) {
        List<CulinaryIngredient> result = new ArrayList<>();
        for (PlayerAction action : actions) {
            CulinaryIngredient ingredient = findFor(action);
            if (ingredient != null) {
                result.add(ingredient);
            }
        }
        return result;
    }

    /** 按来源与 id 查询原料。 */
    @Nullable
    public CulinaryIngredient find(IngredientSource<?> source, Identifier id) {
        for (CulinaryIngredient ingredient : ingredients) {
            if (ingredient.source().getId().equals(source.getId()) && ingredient.id().equals(id)) {
                return ingredient;
            }
        }
        return null;
    }

    // ==================== 默认数据构造辅助 ====================

    private static CulinaryIngredient item(String id, IngredientCategory category,
                                           SimpleFoodComponent cooked, @Nullable SimpleFoodComponent raw) {
        return new CulinaryIngredient(IngredientSource.ITEM, requireId(id), category, cooked, raw);
    }

    private static CulinaryIngredient seasoning(String id, IngredientCategory.Seasoning.Form form,
                                                SimpleFoodComponent cooked, @Nullable SimpleFoodComponent raw) {
        return new CulinaryIngredient(IngredientSource.ITEM, requireId(id), new IngredientCategory.Seasoning(form), cooked, raw);
    }

    private static IngredientCategory main(IngredientCategory.Main.Size size) {
        return new IngredientCategory.Main(size);
    }

    private static IngredientCategory side(IngredientCategory.Side.Size size) {
        return new IngredientCategory.Side(size);
    }

    private static Identifier requireId(String id) {
        Identifier identifier = Identifier.tryParse(id);
        if (identifier == null) {
            throw new IllegalArgumentException("Invalid default ingredient id: " + id);
        }
        return identifier;
    }

    private static SimpleFoodComponent food(int hunger, float saturation) {
        return new SimpleFoodComponent(hunger, saturation);
    }
}
