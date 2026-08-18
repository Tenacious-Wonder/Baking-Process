package org.bakingprocess.block.process;

import com.mojang.datafixers.util.Pair;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.item.FoodComponent;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.ActionResult;
import net.minecraft.world.World;
import org.bakingprocess.block.PlateBlock;
import org.bakingprocess.block.entity.PlatableBlockEntity;
import org.bakingprocess.content.DishesContent;
import org.bakingprocess.util.SimpleFoodComponent;
import org.twcore.api.process.AbstractProcess;
import org.twcore.process.step.Step;
import org.twcore.process.step.StepExecutionContext;
import org.twcore.process.step.StepResult;

public class EatDishesProcess<T extends BlockEntity & PlatableBlockEntity> extends AbstractProcess<T> {
    public static final String STEP_EAT = "eat";

    private int remainingEats;
    private int totalEats;

    public EatDishesProcess() {
        registerStep(STEP_EAT, new EatStep());
    }

    public int getEatenCount() {
        return totalEats - remainingEats;
    }

    public int getTotalEats() {
        return totalEats;
    }

    @Override
    protected String getInitialStepId() {
        return STEP_EAT;
    }

    @Override
    protected void onStart(World world, T blockEntity) {
        DishesContent outcome = blockEntity.getOutcome();
        if (outcome == null || !outcome.canEat()) {
            reset();
            return;
        }

        // 如果 remainingEats 为 0（首次启动或重置后），初始化
        if (remainingEats <= 0) {
            totalEats = outcome.getEatCount();
            remainingEats = totalEats;
        }
    }

    @Override
    protected void onReset() {
        remainingEats = 0;
        totalEats = 0;
    }

    @Override
    public void readFromNbt(NbtCompound nbt) {
        super.readFromNbt(nbt);
        remainingEats = nbt.getInt("remaining_eats");
        totalEats = nbt.getInt("total_eats");
    }

    @Override
    public void writeToNbt(NbtCompound nbt) {
        super.writeToNbt(nbt);
        nbt.putInt("remaining_eats", remainingEats);
        nbt.putInt("total_eats", totalEats);
    }

    private class EatStep implements Step<T> {
        @Override
        public StepResult execute(StepExecutionContext<T> context) {
            // 检查条件
            DishesContent outcome = context.blockEntity().getOutcome();
            if (outcome == null || context.blockState().get(PlateBlock.IS_COVERED)) {
                return StepResult.complete(ActionResult.PASS);
            }

            if (!context.getHeldItemStack().isEmpty()) {
                return StepResult.fail(null, ActionResult.PASS);
            }

            // 计算本次应得的食物比例
            if (totalEats <= 0) {
                return StepResult.fail(null, ActionResult.FAIL);
            }

            // 检查食用条件
            boolean canEat = (context.player().getAbilities().invulnerable || outcome.getFoodComponent().isAlwaysEdible() || context.player().getHungerManager().isNotFull());
            if (!canEat) {
                if (remainingEats == totalEats) {
                    return StepResult.complete(ActionResult.PASS);
                }

                return StepResult.fail(STEP_EAT, ActionResult.PASS);
            }

            // 开始食用
            eat(outcome, context);

            // 播放音效和粒子
            context.playSound(SoundEvents.ENTITY_GENERIC_EAT);

            // 减少剩余次数
            remainingEats--;
            context.blockEntity().markDirty(); // 保存流程状态

            if (remainingEats <= 0) {
                // 吃完了，清空盘子
                context.blockEntity().onEatComplete(context.world(), context.pos(), context.player(), context.hand(), context.hit());
                return StepResult.complete(ActionResult.SUCCESS);
            } else {
                // 还有剩余，继续停留在当前步骤，等待下一次右键
                return StepResult.continueSameStep(ActionResult.SUCCESS);
            }
        }

        protected void eat(DishesContent outcome, StepExecutionContext<T> context) {
            // 每次吃一口，比例 = 1 / totalEats
            FoodComponent fullFood = outcome.getFoodComponent();
            SimpleFoodComponent fullSimple = SimpleFoodComponent.fromFoodComponent(fullFood);
            // 计算百分比（整数百分比，最后一口可能多一点点，但可接受）
            int percentPerEat = (int) Math.round(100.0 / totalEats);
            SimpleFoodComponent perEatFood = fullSimple.percent(percentPerEat);
            // 应用饥饿值和饱和度
            perEatFood.eat(context.player());

            // 处理状态效果：每个效果持续时间除以 totalEats，概率不变
            for (Pair<StatusEffectInstance, Float> pair : fullFood.getStatusEffects()) {
                StatusEffectInstance effect = pair.getFirst();
                float probability = pair.getSecond();
                int newDuration = Math.max(1, effect.getDuration() / totalEats);
                StatusEffectInstance newEffect = new StatusEffectInstance(
                        effect.getEffectType(),
                        newDuration,
                        effect.getAmplifier(),
                        effect.isAmbient(),
                        effect.shouldShowParticles(),
                        effect.shouldShowIcon()
                );
                // 应用效果时考虑概率
                if (probability >= 1.0f || context.world().random.nextFloat() < probability) {
                    context.player().addStatusEffect(newEffect);
                }
            }
        }
    }

    @Override
    protected String getCustomStatusInfo() {
        return "Remaining Eats: " + remainingEats + "\n";
    }
}
