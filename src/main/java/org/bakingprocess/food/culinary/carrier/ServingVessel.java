package org.bakingprocess.food.culinary.carrier;

import org.bakingprocess.food.culinary.Culinary;
import org.jetbrains.annotations.Nullable;

/**
 * 菜肴载体接口。
 *
 * <p>由世界上能“盛放”一道菜的东西实现（盘子、碗、面包船……）。
 * 载体持有的就是那道真实菜肴对象：外部代码可通过 {@link #getCulinary()} 获取并直接加工它，
 * 通过 {@link #addIfAbsent} 把菜盛入容器，通过 {@link #clearCulinary()} 让载体抛弃对该菜肴的引用。
 * 转移一道菜时应先让目标载体接住（{@link #addIfAbsent}），再清空原载体（{@link #clearCulinary()}）。</p>
 *
 * @see org.bakingprocess.food.culinary.Culinary
 */
public interface ServingVessel {

    /** 当前盛放的菜肴；容器为空则返回 {@code null}。 */
    @Nullable
    Culinary getCulinary();

    /**
     * 若容器当前为空，则将指定菜肴盛入并返回 {@code true}；
     * 容器已有菜肴时拒绝并返回 {@code false}。成功后容器持有传入的同一个对象。
     */
    boolean addIfAbsent(Culinary culinary);

    /** 让容器抛弃当前持有的菜肴引用（菜肴的去向由调用方负责）。 */
    void clearCulinary();
}
