package org.bakingprocess.culinary.step;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Identifier;
import net.minecraft.world.World;
import org.bakingprocess.culinary.CulinaryView;
import org.bakingprocess.config.IngredientTableData;
import org.bakingprocess.culinary.ingredient.CulinaryIngredient;
import org.bakingprocess.culinary.ingredient.DishFoodCalculator;
import org.bakingprocess.registry.ModProcessingTypes;
import org.bakingprocess.util.SimpleFoodComponent;

import java.util.List;

/**
 * <h1>烘烤加工步骤</h1>
 * <p>表示这道菜经历了"烤制"这一加工；独有数据是烘烤时间（{@code bakeTime}）。</p>
 *
 * <h2>从摆盘步骤推导并固化</h2>
 * <p>加入一道菜时（{@link #onAdded}）读取既有历史中的 {@link PlatingStep}，推导烤熟后的表现：
 * 口数沿用目标菜口数、食物属性按原料"烤熟后"属性（{@code cooked}）经
 * {@link DishFoodCalculator} 计算、菜标识由摆盘标识派生（路径加 {@code cooked_} 前缀）。</p>
 */
public class BakingStep extends ProcessingStep {

    /** BakingStep 的序列化 Codec。 */
    public static final Codec<BakingStep> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.INT.fieldOf("bake_time").forGetter(BakingStep::getBakeTime),
            Codec.INT.fieldOf("eat_count").forGetter(BakingStep::getEatCount),
            Identifier.CODEC.fieldOf("cooked_id").forGetter(BakingStep::getIdentifier),
            SimpleFoodComponent.CODEC.fieldOf("food").forGetter(BakingStep::getCookedFood)
    ).apply(instance, BakingStep::fromDerived));

    // ==================== 字段 ====================

    /** 烘烤时间（tick）。 */
    private final int bakeTime;

    // 以下推导字段在 onAdded 时固化；反序列化时由 fromDerived 直接填充
    /** 烤熟后的总口数（沿自摆盘步骤的目标口数）。 */
    private int eatCount;
    /** 烤熟后的菜标识（显示名与渲染分派用）。 */
    private Identifier cookedIdentifier;
    /** 烤熟后的食物属性。 */
    private SimpleFoodComponent cookedFood;
    /** 是否已完成推导。 */
    private boolean derived;

    /**
     * @param bakeTime 烘烤时间（tick）
     */
    public BakingStep(int bakeTime) {
        this.bakeTime = bakeTime;
    }

    // ==================== ProcessingStep 实现 ====================

    @Override
    public Identifier getIdentifier() {
        return cookedIdentifier;
    }

    @Override
    public boolean isEdible() {
        return true;
    }

    @Override
    public ProcessingType<?> getType() {
        return ModProcessingTypes.BAKING;
    }

    @Override
    public int getTotalEats() {
        return eatCount;
    }

    @Override
    public void eat(PlayerEntity player, World world, int currentBite) {
        if (cookedFood == null || eatCount <= 0) {
            return;
        }
        DishFoodCalculator.applyBite(player, cookedFood, eatCount, currentBite);
    }

    // ==================== 历史消化 ====================

    @Override
    public void onAdded(CulinaryView history) {
        if (derived) {
            return;
        }
        ProcessingStep latest = history.getLatestStep();
        if (!(latest instanceof PlatingStep plating)) {
            return;
        }

        // 从摆盘步骤推导并固化烤熟后的表现
        this.eatCount = plating.getEatCount();
        this.cookedIdentifier = new Identifier(
                plating.getIdentifier().getNamespace(),
                "cooked_" + plating.getIdentifier().getPath()
        );
        List<CulinaryIngredient> ingredients = IngredientTableData.current().fromActions(plating.getActions());
        this.cookedFood = DishFoodCalculator.calculate(ingredients, false);
        this.derived = true;
    }

    // ==================== 访问器 ====================

    /** 烘烤时间（tick）。 */
    public int getBakeTime() {
        return bakeTime;
    }

    /** 烤熟后的总口数。 */
    public int getEatCount() {
        return eatCount;
    }

    /** 烤熟后的食物属性。 */
    public SimpleFoodComponent getCookedFood() {
        return cookedFood;
    }

    // ==================== Codec 辅助 ====================

    /** 从已推导字段构造（反序列化路径）。 */
    private static BakingStep fromDerived(int bakeTime, int eatCount, Identifier cookedIdentifier,
                                          SimpleFoodComponent cookedFood) {
        BakingStep step = new BakingStep(bakeTime);
        step.eatCount = eatCount;
        step.cookedIdentifier = cookedIdentifier;
        step.cookedFood = cookedFood;
        step.derived = true;
        return step;
    }
}
