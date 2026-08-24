package org.bakingprocess.culinary.step;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.world.World;
import org.bakingprocess.config.IngredientTableData;
import org.bakingprocess.culinary.ingredient.CulinaryIngredient;
import org.bakingprocess.culinary.ingredient.DishFoodCalculator;
import org.bakingprocess.culinary.ingredient.IngredientCategory;
import org.bakingprocess.culinary.ingredient.IngredientSource;
import org.bakingprocess.registry.ModProcessingTypes;
import org.bakingprocess.util.SimpleFoodComponent;
import org.twcore.api.process.PlayerAction;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * <h1>摆盘加工步骤</h1>
 * <p>记录一次摆盘经历，承载配方给出的菜标识、口数与可食性；是这道菜的<b>第一步历史</b>，
 * 无既有历史可消化（{@link #onAdded} 保持默认空实现）。</p>
 *
 * <h2>生熟语义</h2>
 * <ul>
 *     <li>{@code edible=false}（默认）：摆完是<b>生菜</b>，不可食（{@link #isEdible()} 为假、
 *         {@link #getTotalEats()} 为 0），{@code eatCount} 是<b>目标熟菜口数</b>，预留给
 *         后续 {@code BakingStep} 推导；</li>
 *     <li>{@code edible=true}：摆完<b>直接可食</b>，{@code eatCount} 是真实口数，
 *         吃的时候用原料的"直接吃"属性（{@code raw}）经 {@link DishFoodCalculator} 计算。</li>
 * </ul>
 */
public class PlatingStep extends ProcessingStep {

    /** PlatingStep 的序列化 Codec。 */
    public static final Codec<PlatingStep> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.list(Codec.STRING).xmap(PlatingStep::parseActions, PlatingStep::stringifyActions)
                    .fieldOf("actions").forGetter(PlatingStep::getActions),
            Identifier.CODEC.fieldOf("id").forGetter(PlatingStep::getIdentifier),
            Codec.INT.fieldOf("eat_count").forGetter(PlatingStep::getEatCount),
            Codec.BOOL.optionalFieldOf("edible", false).forGetter(PlatingStep::isEdible),
            Codec.BOOL.optionalFieldOf("generic", false).forGetter(PlatingStep::isGeneric)
    ).apply(instance, PlatingStep::new));

    // ==================== 字段 ====================

    /** 摆盘操作序列（放入了什么原料、按什么顺序）。 */
    private final List<PlayerAction> actions;
    /** 目标菜标识（显示名本地化派生与渲染模型分派）。 */
    private final Identifier identifier;
    /** 目标菜口数（edible=false 时为预留，edible=true 时为真实口数）。 */
    private final int eatCount;
    /** 摆完是否直接可食。 */
    private final boolean edible;
    /** 是否无序摆盘（有序菜谱=false，无序通用配方=true；决定显示名生成方式）。 */
    private final boolean generic;

    /**
     * @param actions    摆盘操作序列
     * @param identifier 目标菜标识
     * @param eatCount   目标菜口数
     * @param edible     摆完是否直接可食
     * @param generic    是否无序摆盘
     */
    public PlatingStep(List<PlayerAction> actions, Identifier identifier, int eatCount, boolean edible, boolean generic) {
        this.actions = List.copyOf(actions);
        this.identifier = identifier;
        this.eatCount = eatCount;
        this.edible = edible;
        this.generic = generic;
    }

    // ==================== 菜肴名称 ====================

    /**
     * 生成无序菜肴的菜名本体（不含"未烤制"前缀）。
     *
     * @param actions 摆放的原料序列（未加工的原始放入顺序）
     * @return 菜名本体文本
     */
    public static Text display(List<PlayerAction> actions) {
        StringBuilder mains = new StringBuilder();
        StringBuilder sides = new StringBuilder();
        Set<Identifier> seenMains = new HashSet<>();
        Set<Identifier> seenSides = new HashSet<>();

        for (CulinaryIngredient ingredient : IngredientTableData.current().fromActions(actions)) {
            if (ingredient.isDecoration() || ingredient.isSeasoning()) {
                continue; // 装饰与调料暂不进入命名（调料位置规则待定）
            }
            if (ingredient.category().kind() == IngredientCategory.Kind.MAIN) {
                if (seenMains.add(ingredient.id())) {
                    mains.append(nameOf(ingredient));
                }
            } else if (ingredient.category().kind() == IngredientCategory.Kind.SIDE) {
                if (seenSides.add(ingredient.id())) {
                    sides.append(nameOf(ingredient));
                }
            }
        }

        String mainPart = mains.toString();
        String sidePart = sides.toString();

        if (mainPart.isEmpty()) {
            return Text.translatable("culinary.generic.side", sidePart);
        }
        if (sidePart.isEmpty()) {
            return Text.translatable("culinary.generic.main", mainPart);
        }
        return Text.translatable("culinary.generic", mainPart, sidePart);
    }

    /** 原料的本地化名；内容物来源暂用注册表 id 字符串。 */
    private static String nameOf(CulinaryIngredient ingredient) {
        if (ingredient.source() == IngredientSource.ITEM) {
            Item item = Registries.ITEM.getOrEmpty(ingredient.id()).orElse(null);
            return item != null ? item.getName().getString() : ingredient.id().toString();
        }
        return ingredient.id().toString();
    }

    // ==================== ProcessingStep 实现 ====================

    @Override
    public Identifier getIdentifier() {
        return identifier;
    }

    @Override
    public Text getDisplayName() {
        // 生菜显示名 = "未烤制"前缀 + 菜名本体
        return RAW_PREFIX.copy().append(getSuffixName());
    }

    /**
     * 菜名本体（不含"未烤制"前缀）：
     * 有序 = 菜标识对应的语言键（{@code culinary.<标识>}）；
     * 无序 = 按摆放内容动态生成。供 {@code BakingStep} 推导熟菜显示名复用。
     */
    public Text getSuffixName() {
        if (generic) {
            return display(actions);
        }
        return Text.translatable("culinary." + getIdentifier().toTranslationKey());
    }

    @Override
    public boolean isEdible() {
        return edible;
    }

    @Override
    public ProcessingType<?> getType() {
        return ModProcessingTypes.PLATING;
    }

    @Override
    public int getTotalEats() {
        return edible ? eatCount : 0;
    }

    @Override
    public void eat(PlayerEntity player, World world, int currentBite) {
        if (!edible || eatCount <= 0) {
            return;
        }
        // 可直接食用的摆盘菜：用原料的"直接吃"属性（raw）计算
        SimpleFoodComponent full = DishFoodCalculator.calculate(
                IngredientTableData.current().fromActions(actions), true);
        DishFoodCalculator.applyBite(player, full, eatCount, currentBite);
    }

    // ==================== 访问器 ====================

    /** 摆盘操作序列（不可修改）。 */
    public List<PlayerAction> getActions() {
        return actions;
    }

    /** 目标菜口数。 */
    public int getEatCount() {
        return eatCount;
    }

    /** 是否无序摆盘（决定显示名按内容生成）。 */
    public boolean isGeneric() {
        return generic;
    }

    // ==================== Codec 辅助 ====================

    private static List<PlayerAction> parseActions(List<String> strings) {
        List<PlayerAction> actions = new ArrayList<>();
        for (String string : strings) {
            actions.add(PlayerAction.fromString(string));
        }
        return actions;
    }

    private static List<String> stringifyActions(List<PlayerAction> actions) {
        List<String> strings = new ArrayList<>();
        for (PlayerAction action : actions) {
            strings.add(action.toString());
        }
        return strings;
    }
}
