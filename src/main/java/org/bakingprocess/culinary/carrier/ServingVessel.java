package org.bakingprocess.culinary.carrier;

import net.minecraft.util.Identifier;
import org.bakingprocess.culinary.Culinary;
import org.bakingprocess.culinary.step.ProcessingStep;
import org.jetbrains.annotations.Nullable;

/**
 * <h1>菜肴容器。</h1>
 * <p>
 * 由世界上能"盛放"一道菜的东西实现。本接口是一份
 * <b>行为契约</b>，而非实现纲要：它只约定"容器能回答什么、能被做什么"，
 * 不规定容器内部如何持有菜肴——实现方可以把它存成字段，也可以按自身当前状态
 * （例如正在进行的摆盘流程）动态推导，二者在本接口下等价。
 * </p>
 *
 * <h2>权威与写权限</h2>
 * <ul>
 *     <li><b>容器是菜肴的权威归属</b>：一道菜的当前状态由持有它的容器决定，
 *         {@link Culinary} 只是随容器流转、可被完整拷贝的数据团，没有独立身份；</li>
 *     <li><b>写权限唯一</b>：对菜肴的加工只能通过 {@link #applyStep} 交给容器执行，
 *         飘在容器外的拷贝、快照或只读视图一律不可写，改它们不影响任何容器；</li>
 *     <li><b>读写一致</b>：{@link #tryAddCulinary}、{@link #applyStep}、
 *         {@link #clearCulinary} 成功之后，紧接着的 {@link #getCulinary()} 应当反映
 *         新的状态；实现方大体上应遵守此约定，有特殊语义的实现可自行说明偏离。</li>
 * </ul>
 *
 * <h2>值语义</h2>
 * <ul>
 *     <li>{@link #getCulinary()} 返回容器当前菜肴的<b>完整拷贝</b>，不是内部对象：
 *         调用方可以随意修改它而不影响容器；拿到之后是转移、复制还是取只读视图，
 *         由调用方自行决定，接口不做约束；</li>
 *     <li>{@link #tryAddCulinary} 成功后，容器以自己认为合适的方式持有这份数据，
 *         <b>不承诺保留传入的同一个对象引用</b>；调用方传入后是否继续使用原对象
 *         由调用方自行负责。</li>
 * </ul>
 *
 * @see Culinary
 */
public interface ServingVessel {

    /**
     * 返回本容器的<b>类型标识符</b>（如 {@code baking_process:iron_plate}），
     * 用于摆盘配方匹配与"容器 + 菜肴 → 模型"的渲染分派。
     */
    Identifier getContainerType();

    /** @return <b>当前容器认为自己持有的菜肴</b>：容器为空（或按自身语义认为无菜）则返回 {@code null}。 */
    @Nullable
    Culinary getCulinary();

    /**
     * 尝试将一份菜肴放入容器。
     *
     * @param culinary 要放入的菜肴数据
     * @return 容器接纳则 {@code true}，拒绝（例如已满）则 {@code false}
     */
    boolean tryAddCulinary(Culinary culinary);

    /**
     * 尝试对当前容器内的菜肴执行一次加工。
     *
     * <p>是否接受由容器自行决定：容器认为当前没有菜肴、或不应接受这类加工时返回
     * {@code false}；容器虽没有"语义上的菜"，但接受该加工作为第一刀时也可返回
     * {@code true}。实现方只需保持"尝试加工并如实报告是否成功"这一契约。</p>
     *
     * @param step 要追加的加工步骤
     * @return 加工被接受并生效则 {@code true}，否则 {@code false}
     */
    boolean applyStep(ProcessingStep step);

    /** 让容器抛弃当前菜肴（菜肴数据由调用方负责处理）。 */
    void clearCulinary();
}
