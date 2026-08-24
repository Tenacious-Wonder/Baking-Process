package org.bakingprocess.recipe;

import net.minecraft.util.Identifier;
import org.bakingprocess.culinary.step.PlatingStep;
import org.twcore.api.process.PlayerAction;

import java.util.List;

/**
 * <h1>有序摆盘候选</h1>
 * <p>包装 {@link PlatingRecipe}：按操作序列做<b>有序前缀匹配</b>——
 * {@link #canStart} 匹配第一步、{@link #canContinue} 匹配下一步、{@link #isComplete} 完全一致。</p>
 */
public class RecipePlatingCandidate implements PlatingCandidate {

    private final PlatingRecipe recipe;

    public RecipePlatingCandidate(PlatingRecipe recipe) {
        this.recipe = recipe;
    }

    /** 被包装的有序配方。 */
    public PlatingRecipe getRecipe() {
        return recipe;
    }

    @Override
    public Identifier getId() {
        return recipe.getId();
    }

    @Override
    public boolean canStart(PlayerAction first) {
        return recipe.getActionCount() > 0 && recipe.getActionAt(0).matches(first);
    }

    @Override
    public boolean canContinue(List<PlayerAction> actions, PlayerAction next) {
        int size = actions.size();
        if (size >= recipe.getActionCount() || !recipe.matchesPrefix(actions)) {
            return false;
        }
        PlayerAction nextRecipeAction = recipe.getActionAt(size);
        return nextRecipeAction != null && next.matches(nextRecipeAction);
    }

    @Override
    public boolean matchesPrefix(List<PlayerAction> actions) {
        return recipe.matchesPrefix(actions);
    }

    @Override
    public boolean isComplete(List<PlayerAction> actions) {
        return recipe.matchesActions(actions);
    }

    @Override
    public PlatingStep createStep(List<PlayerAction> actualActions) {
        return recipe.createStep();
    }
}
