package org.bakingprocess.recipe;

import net.minecraft.util.Identifier;
import org.bakingprocess.culinary.step.PlatingStep;
import org.twcore.api.process.PlayerAction;

import java.util.List;

/**
 * <h1>摆盘候选</h1>
 * <p>有序菜谱（{@link PlatingRecipe}）与无序通用配方（{@link GenericPlatingRecipe}）的统一
 * 匹配视图，供 {@code PlatingProcess} 收窄候选、判定完成与生成摆盘步骤使用。</p>
 *
 * <p>两类候选在流程中<b>并行共存</b>（双轨）：每次追加先试有序、后试无序，
 * 任一轨可完成即接受；完成时有序优先。</p>
 */
public interface PlatingCandidate {

    /** 候选对应配方 / 规则的 id。 */
    Identifier getId();

    /** 第一个原料能否开始本候选（有序=第一步匹配；无序=加入后可完成）。 */
    boolean canStart(PlayerAction first);

    /** 已放入序列后，加入 next 是否仍合法（有序=仍是合法前缀；无序=可完成且不违顺序）。 */
    boolean canContinue(List<PlayerAction> actions, PlayerAction next);

    /** 当前序列是否仍是本候选的合法前缀（有序=前缀匹配；无序=可完成）。用于候选恢复。 */
    boolean matchesPrefix(List<PlayerAction> actions);

    /** 已放入序列是否已构成一份成品（有序=完全匹配；无序=计数落在规则区间）。 */
    boolean isComplete(List<PlayerAction> actions);

    /** 用玩家实际放入的序列生成摆盘步骤。 */
    PlatingStep createStep(List<PlayerAction> actualActions);
}
