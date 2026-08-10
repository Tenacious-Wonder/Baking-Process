package org.bakingprocess.food.culinary;

import org.bakingprocess.food.culinary.step.ProcessingStep;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * 菜肴的只读展示快照。
 *
 * <p>由 {@link Culinary#asReadOnly()} 生成，仅用于 GUI 渲染、进度展示等非实际用途：
 * 它不代表世界上真实存在的一道菜，不参与加工、转移或存档。快照在生成时拷贝当时的
 * 步骤，本身就是锁定且不可变的，之后真实菜肴的变化不会反映到快照上。</p>
 *
 * @see Culinary
 */
public final class CulinaryView {

    private final List<ProcessingStep> steps;

    CulinaryView(List<ProcessingStep> steps) {
        this.steps = List.copyOf(steps);
    }

    /** 快照时刻的加工步骤列表（不可修改）。 */
    public List<ProcessingStep> getSteps() {
        return steps;
    }

    /** 快照时刻的最新一步加工；尚未加工过则返回 {@code null}。 */
    @Nullable
    public ProcessingStep getLatestStep() {
        if (steps.isEmpty()) {
            return null;
        }
        return steps.get(steps.size() - 1);
    }
}
