package org.bakingprocess.culinary;

import org.bakingprocess.culinary.step.ProcessingStep;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * <h1>菜肴的只读展示快照</h1>
 * <p>由 {@link Culinary#asReadOnly()} 生成，供 GUI 渲染等展示用途。</p>
 *
 * <ul>
 *     <li>快照生成时锁定，不参与加工、转移或存档；</li>
 *     <li>之后真实菜肴的变化不会反映到快照上。</li>
 * </ul>
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
