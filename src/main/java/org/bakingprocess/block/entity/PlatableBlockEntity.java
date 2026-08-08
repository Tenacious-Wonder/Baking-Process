package org.bakingprocess.block.entity;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.bakingprocess.block.process.PlatingProcess;
import org.bakingprocess.content.DishesContent;
import org.bakingprocess.recipe.PlatingRecipe;
import org.jetbrains.annotations.Nullable;

/**
 * 可摆盘的方块实体接口，定义摆盘/食用流程与方块实体之间的契约。
 *
 * <p><strong>摆盘设计：</strong></p>
 * <p>摆盘是"按配方顺序向容器中放入食材，最后以完成物品封口成菜"的多步骤交互玩法。
 * 一次完整的摆盘过程如下：</p>
 * <ol>
 *   <li><strong>放置容器</strong>：摆盘在特定的容器方块上进行（如铁盘），
 *       容器类型决定可用的配方集合</li>
 *   <li><strong>按序放入食材</strong>：玩家依次向容器中放入食材（物品或内容物），
 *       每次放入都会被流程与候选配方进行匹配，只有与某配方下一步相符的食材
 *       才会被接受</li>
 *   <li><strong>候选配方收敛</strong>：流程维护候选配方集合，随放入的食材按前缀
 *       匹配不断收窄，最终唯一确定玩家正在制作的配方</li>
 *   <li><strong>完成封口</strong>：当操作序列与配方完全一致时，玩家手持完成物品
 *       （如盘盖）右键容器，流程判定配方完成，生成菜肴并盖上盖子</li>
 *   <li><strong>后续处理</strong>：完成的菜肴可被分次食用；也可揭开盖子还原为
 *       进行中的摆盘继续调整，或直接取走整盘</li>
 * </ol>
 *
 * <p><strong>职责划分：</strong></p>
 * <ul>
 *   <li><strong>方块实体</strong>：作为容器的身份载体，持有菜肴
 *       （{@link DishesContent}），负责容器类型、完成物品判断与流程完成回调</li>
 *   <li><strong>{@link PlatingProcess}</strong>：驱动摆盘流程，管理操作序列、
 *       候选配方与配方匹配</li>
 *   <li><strong>{@link PlatingRecipe}</strong>：定义摆盘配方
 *       （容器 + 操作序列 + 输出菜肴），提供匹配与反查能力</li>
 * </ul>
 *
 * <p><strong>核心概念：</strong></p>
 * <ol>
 *   <li><strong>容器类型</strong>：配方的承载容器，决定可用的配方集合</li>
 *   <li><strong>操作序列</strong>：玩家按顺序执行的操作（{@code PlayerAction}），
 *       是配方匹配的依据，由流程统一管理</li>
 *   <li><strong>菜肴</strong>：摆盘完成后的最终产物，由方块实体持有</li>
 *   <li><strong>完成物品</strong>：触发流程完成的特殊物品（如盘子盖）</li>
 * </ol>
 *
 * @see PlatingProcess
 * @see PlatingRecipe
 */
public interface PlatableBlockEntity {

    // ==================== 容器信息方法 ====================

    /**
     * 获取容器的物品类型。
     *
     * <p>此方法返回容器本身的物品类型（如铁盘、陶瓷盘等），用于配方匹配。
     * 不同的容器类型支持不同的配方集合。</p>
     *
     * <p><strong>实现要求：</strong></p>
     * <ul>
     *   <li>必须返回非空物品类型</li>
     *   <li>对于同一类型的容器，此方法应始终返回相同的物品</li>
     *   <li>如果容器类型可能改变，应通过方块状态或 NBT 数据管理</li>
     * </ul>
     *
     * @return 容器的物品类型，不能为 {@code null}
     */
    Item getContainerType();

    /**
     * 获取当前容器中可能的菜肴。
     *
     * <p>当摆盘流程完成时，容器中会生成菜肴内容。
     * 如果流程尚未完成，此方法应返回 {@code null}。</p>
     *
     * @return 当前容器中的菜肴，没有时为 {@code null}
     */
    @Nullable
    DishesContent getOutcome();

    // ==================== 流程回调方法 ====================

    /**
     * 检查物品是否为该容器的完成物品。
     *
     * <p>完成物品用于触发摆盘流程的完成步骤（如盖子、酱汁等）。
     * 当玩家手持完成物品右键摆盘方块时，流程会检查当前摆盘状态是否匹配某个配方，
     * 如果匹配则输出最终菜肴。</p>
     *
     * <p><strong>实现建议：</strong></p>
     * <ul>
     *   <li>可以通过物品标签或特定物品类型来判断</li>
     *   <li>不同的容器类型可以有相同的完成物品（如通用盖子）</li>
     *   <li>也可以有容器特定的完成物品（如特定酱汁）</li>
     * </ul>
     *
     * @param stack 要检查的物品堆栈
     * @return 如果是完成物品返回 {@code true}，否则返回 {@code false}
     */
    boolean isCompletionItem(ItemStack stack);

    /**
     * 当摆盘流程成功完成时调用。
     *
     * <p>此方法在摆盘流程成功完成、输出菜肴后被调用，方块实体可以在此方法中：</p>
     * <ul>
     *   <li>设置菜肴内容</li>
     *   <li>消耗完成物品</li>
     *   <li>播放自定义音效或粒子效果</li>
     *   <li>更新方块状态</li>
     *   <li>触发其他事件</li>
     * </ul>
     *
     * <p><strong>注意：</strong>此方法由摆盘流程在完成步骤中调用，
     * 操作序列的清理由流程自身完成，方块实体无需（也不应）直接操作。</p>
     *
     * @param world 世界实例
     * @param pos 方块位置
     * @param recipe 完成的配方
     * @param player 操作的玩家
     * @param hand 玩家的手
     * @param hit 操作的上下文
     */
    void onPlatingComplete(World world, BlockPos pos, PlatingRecipe recipe, PlayerEntity player, Hand hand, HitResult hit);

    /**
     * 当盘子中的食物被吃完时调用。
     *
     * @param world 世界实例
     * @param pos 方块位置
     * @param player 操作的玩家
     * @param hand 玩家的手
     * @param hit 操作的上下文
     */
    void onEatComplete(World world, BlockPos pos, PlayerEntity player, Hand hand, HitResult hit);

    // ==================== 状态查询方法 ====================

    /**
     * 检查是否可以开始新的摆盘流程。
     *
     * <p>此方法检查当前摆盘状态是否允许开始新的流程。
     * 通常当没有菜肴时允许开始新的流程（操作序列是否为空由流程自身保证）。</p>
     *
     * @return 如果可以开始新流程返回 {@code true}，否则返回 {@code false}
     */
    default boolean canStartNewProcess() {
        return getOutcome() == null;
    }

    /**
     * 检查是否已有完成的菜肴。
     *
     * <p>这是一个便捷方法，通常用于 UI 显示完成状态。</p>
     *
     * @return 如果当前已有菜肴则返回 {@code true}
     */
    default boolean hasCompleteRecipe() {
        return getOutcome() != null;
    }
}
