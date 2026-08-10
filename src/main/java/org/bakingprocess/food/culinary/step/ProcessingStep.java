package org.bakingprocess.food.culinary.step;

import net.minecraft.text.Text;

/**
 * 加工步骤抽象基类。
 *
 * <p>代表一道菜经历过的某一次加工（切块、腌制、烤制……）之后的状态快照。
 * 具体步骤类型继承本类，定义各自特有的属性字段，并通过 {@link #getType()} 声明
 * 自己属于哪种 {@link ProcessingType}，由该类型提供步骤的序列化 Codec。</p>
 *
 * @see ProcessingType
 */
public abstract class ProcessingStep {

    /** 经历这一步加工后，这道菜是否已经可以食用。 */
    public abstract boolean isEdible();

    /** 这一步加工在 GUI / 提示中的显示名称。 */
    public abstract Text getDisplayName();

    /** 本步骤所属的加工类型，用于序列化调度与类型识别。 */
    public abstract ProcessingType<?> getType();
}
