package org.bakingprocess.culinary;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.world.World;
import org.bakingprocess.culinary.carrier.ServingVessel;
import org.bakingprocess.culinary.step.ProcessingStep;

/**
 * 菜肴操作句柄：由容器（{@link ServingVessel#getCulinaryHandle()}）提供，对容器内部
 * 真实菜肴执行操作的唯一通道，与 {@link ServingVessel#getCulinary()}（只读快照）互补。
 *
 * <p>外部对菜肴的所有操作（加工、吃、状态推进）都必须经此句柄，由容器认可后才生效；
 * 调用方拿不到真实菜肴对象，无法绕过容器。读方法在容器无菜时返回安全默认值。</p>
 *
 * @see ServingVessel
 * @see Culinary
 */
public interface CulinaryHandle {

    // ==================== 动态状态读 ====================

    /** 已吃口数；无菜时为 0。 */
    int getEatenCount();

    /** 是否已被吃过至少一口（此后不允许再加工）；无菜时为 {@code false}。 */
    boolean isConsumed();

    /** 剩余可吃口数；无菜时为 0。 */
    int getRemainingEats();

    // ==================== 表现读（由最新步骤派生） ====================

    /** 当前是否可食（由最新加工步骤决定）；无菜或不可食时为 {@code false}。 */
    boolean isEdible();

    /** 总共可吃口数（由最新加工步骤决定）；无菜时为 0。 */
    int getTotalEats();

    // ==================== 操作 ====================

    /**
     * 尝试追加一次加工步骤。
     *
     * <p>是否接受由容器决定：容器认为当前没有菜肴、或不应接受这类加工（如摆盘流程
     * 仍活动、菜肴已食用）时返回 {@code false}。</p>
     *
     * @param step 要追加的加工步骤
     * @return 加工被接受并生效则 {@code true}，否则 {@code false}
     */
    boolean applyStep(ProcessingStep step);

    /**
     * 吃下这道菜的一口。
     *
     * <p>对容器内部真实菜肴执行，吃完最后一口时容器自动清空菜肴。</p>
     *
     * @param player 吃的玩家
     * @param world  当前世界
     * @return 是否成功吃下一口；无菜、不可食或已吃完时为 {@code false}
     */
    boolean eat(PlayerEntity player, World world);
}
