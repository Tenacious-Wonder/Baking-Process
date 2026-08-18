package org.bakingprocess.block.entity;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.bakingprocess.block.process.PlatingProcess;
import org.bakingprocess.culinary.carrier.ServingVessel;
import org.bakingprocess.recipe.PlatingRecipe;

/**
 * <h1>可摆盘的方块实体</h1>
 * <p>摆盘流程（{@link PlatingProcess}）与方块实体之间的契约，同时是菜肴容器（{@link ServingVessel}）。</p>
 *
 * <h2>摆盘玩法</h2>
 * <ol>
 *     <li>按配方顺序向容器放入食材，每次与候选配方做<b>前缀匹配</b>收窄候选；</li>
 *     <li>操作序列与配方完全一致时，手持完成物品（盘盖）右键完成，
 *         用配方生成摆盘步骤并盖上盖子；</li>
 *     <li>成品菜可分次食用，也可揭开盖子还原为进行中的摆盘。</li>
 * </ol>
 *
 * @see PlatingProcess
 * @see PlatingRecipe
 * @see ServingVessel
 */
public interface PlatableBlockEntity extends ServingVessel {

    /**
     * 检查物品是否为该容器的完成物品。
     *
     * <p>完成物品用于触发摆盘流程的完成步骤（如盖子、酱汁等）。
     * 当玩家手持完成物品右键摆盘方块时，流程会检查当前摆盘状态是否匹配某个配方，
     * 如果匹配则完成摆盘。</p>
     *
     * @param stack 要检查的物品堆栈
     * @return 如果是完成物品返回 {@code true}，否则返回 {@code false}
     */
    boolean isCompletionItem(ItemStack stack);

    /**
     * 当摆盘流程成功完成时调用。
     *
     * <p>此方法在摆盘流程成功完成、生成菜肴后被调用，方块实体可以在此方法中：</p>
     * <ul>
     *   <li>用配方生成摆盘步骤并接纳为菜肴</li>
     *   <li>消耗完成物品</li>
     *   <li>播放自定义音效或粒子效果</li>
     *   <li>更新方块状态</li>
     * </ul>
     *
     * @param world 世界实例
     * @param pos 方块位置
     * @param recipe 完成的配方
     * @param player 操作的玩家
     * @param hand 玩家的手
     * @param hit 操作的上下文
     */
    void onPlatingComplete(World world, BlockPos pos, PlatingRecipe recipe, PlayerEntity player, Hand hand, HitResult hit);
}
