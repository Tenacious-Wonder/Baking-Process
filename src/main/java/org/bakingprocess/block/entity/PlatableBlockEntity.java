package org.bakingprocess.block.entity;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.bakingprocess.block.process.PlatingProcess;
import org.bakingprocess.culinary.carrier.ServingVessel;
import org.bakingprocess.culinary.step.PlatingStep;

/**
 * <h1>可摆盘的方块实体</h1>
 * <p>摆盘流程（{@link PlatingProcess}）与方块实体之间的契约，同时是菜肴容器（{@link ServingVessel}）。</p>
 *
 * <h2>摆盘玩法</h2>
 * <ol>
 *     <li>向容器放入食材，候选池中有序菜谱与无序通用配方并行收窄；</li>
 *     <li>存在完全匹配的候选时，手持完成物品（盘盖）右键完成，
 *         用候选生成摆盘步骤并盖上盖子；</li>
 *     <li>成品菜可分次食用，也可揭开盖子还原为进行中的摆盘。</li>
 * </ol>
 *
 * @see PlatingProcess
 * @see ServingVessel
 */
public interface PlatableBlockEntity extends ServingVessel {

    /**
     * 检查物品是否为该容器的完成物品。
     *
     * <p>完成物品用于触发摆盘流程的完成步骤（如盖子、酱汁等）。
     * 当玩家手持完成物品右键摆盘方块时，流程会检查当前摆盘状态是否匹配某个候选，
     * 如果匹配则完成摆盘。</p>
     *
     * @param stack 要检查的物品堆栈
     * @return 如果是完成物品返回 {@code true}，否则返回 {@code false}
     */
    boolean isCompletionItem(ItemStack stack);

    /**
     * 当摆盘流程成功完成时调用。
     *
     * <p>此方法在摆盘流程成功完成、生成摆盘步骤后被调用，方块实体可以在此方法中：</p>
     * <ul>
     *   <li>接纳摆盘步骤为菜肴</li>
     *   <li>消耗完成物品</li>
     *   <li>播放自定义音效或粒子效果</li>
     *   <li>更新方块状态</li>
     * </ul>
     *
     * @param world 世界实例
     * @param pos 方块位置
     * @param step 完成的摆盘步骤
     * @param player 操作的玩家
     * @param hand 玩家的手
     * @param hit 操作的上下文
     */
    void onPlatingComplete(World world, BlockPos pos, PlatingStep step, PlayerEntity player, Hand hand, HitResult hit);
}
