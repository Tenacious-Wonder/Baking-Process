package org.bakingprocess.block.process;

import net.minecraft.block.entity.BlockEntity;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.ActionResult;
import net.minecraft.world.World;
import org.bakingprocess.block.BasePlatableBlock;
import org.bakingprocess.block.entity.PlatableBlockEntity;
import org.twcore.api.process.AbstractProcess;
import org.twcore.process.step.Step;
import org.twcore.process.step.StepExecutionContext;
import org.twcore.process.step.StepResult;

/**
 * 食用流程外壳：驱动右键食用的交互条件（空手、饥饿、音效等）。
 *
 * <p>实际"这一口吃什么"委托给容器句柄（{@link org.bakingprocess.culinary.CulinaryHandle#eat}，
 * 对内部真实菜肴执行），口数由 {@code CulinaryState} 单一记账，本流程不再持久化口数。</p>
 */
public class EatDishesProcess<T extends BlockEntity & PlatableBlockEntity> extends AbstractProcess<T> {
    public static final String STEP_EAT = "eat";

    public EatDishesProcess() {
        registerStep(STEP_EAT, new EatStep());
    }

    @Override
    protected String getInitialStepId() {
        return STEP_EAT;
    }

    @Override
    protected void onStart(World world, T blockEntity) {
        // 无菜或不可食（如未烘烤的生菜）时不启动
        if (!blockEntity.getCulinaryHandle().isEdible()) {
            reset();
        }
    }

    private class EatStep implements Step<T> {
        @Override
        public StepResult execute(StepExecutionContext<T> context) {
            T plate = context.blockEntity();

            // 无菜或不可食、或盖着盖子：直接结束
            if (!plate.getCulinaryHandle().isEdible() || context.blockState().get(BasePlatableBlock.IS_COVERED)) {
                return StepResult.complete(ActionResult.PASS);
            }

            // 空手才能吃
            if (!context.getHeldItemStack().isEmpty()) {
                return StepResult.fail(null, ActionResult.PASS);
            }

            // 饥饿不满（或无敌）才能吃
            boolean canEat = context.player().getAbilities().invulnerable
                    || context.player().getHungerManager().isNotFull();
            if (!canEat) {
                return StepResult.fail(STEP_EAT, ActionResult.PASS);
            }

            // 吃一口：句柄对容器内部真实菜肴执行，吃完最后一口自动清空
            if (!plate.getCulinaryHandle().eat(context.player(), context.world())) {
                return StepResult.fail(STEP_EAT, ActionResult.FAIL);
            }

            context.playSound(SoundEvents.ENTITY_GENERIC_EAT);
            plate.markDirty();

            // 吃完（容器已清空）则结束，否则停留在本步骤等待下一次右键
            if (plate.getCulinaryHandle().getRemainingEats() <= 0) {
                return StepResult.complete(ActionResult.SUCCESS);
            }
            return StepResult.continueSameStep(ActionResult.SUCCESS);
        }
    }
}
