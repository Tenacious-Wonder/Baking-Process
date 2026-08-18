package org.bakingprocess.culinary.carrier;

import net.minecraft.util.Identifier;
import org.bakingprocess.culinary.Culinary;
import org.bakingprocess.culinary.CulinaryHandle;
import org.jetbrains.annotations.Nullable;

/**
 * 菜肴容器契约：世界上能"盛放"一道菜的东西实现。
 *
 * <p>本接口只约定"容器能回答什么、能被做什么"，不规定内部如何持有菜肴——实现方可以
 * 存成字段，也可以按自身状态（如进行中的摆盘流程）动态推导，二者在本接口下等价。</p>
 *
 * <p><b>权威与写权限：</b>容器是菜肴的权威归属；对菜肴的所有操作（加工、吃、状态推进）
 * 只能经 {@link #getCulinaryHandle()} 交给容器执行。{@link #getCulinary()} 返回完整拷贝
 * （只读快照），修改它不影响容器。</p>
 *
 * @see Culinary
 * @see CulinaryHandle
 */
public interface ServingVessel {

    /** 容器以物品堆栈形式携带菜肴数据时使用的 NBT 键（菜数据为统一的 Culinary 序列化）。 */
    String CULINARY_NBT_KEY = "Culinary";

    /**
     * 返回本容器的<b>类型标识符</b>（如 {@code baking_process:iron_plate}），
     * 用于"容器 + 菜肴 → 模型"的渲染分派等按标识的匹配。
     */
    Identifier getContainerId();

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

    /** 让容器抛弃当前菜肴（菜肴数据由调用方负责处理）。 */
    void clearCulinary();

    /**
     * 获取对容器内部真实菜肴的操作句柄。
     *
     * <p>对菜肴的加工、吃与状态推进一律经此句柄，由容器认可后才生效；
     * 句柄的实现不会把真实菜肴对象交给调用方。</p>
     */
    CulinaryHandle getCulinaryHandle();
}
