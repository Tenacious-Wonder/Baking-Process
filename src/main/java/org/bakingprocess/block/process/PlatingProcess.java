package org.bakingprocess.block.process;

import net.minecraft.block.entity.BlockEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.recipe.RecipeManager;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Identifier;
import net.minecraft.world.World;
import org.bakingprocess.block.entity.PlatableBlockEntity;
import org.bakingprocess.recipe.PlatingRecipe;
import org.bakingprocess.registry.ModRecipeTypes;
import org.jetbrains.annotations.Nullable;
import org.twcore.api.process.AbstractProcess;
import org.twcore.api.process.PlayerAction;
import org.twcore.process.playeraction.PlayerActionCreators;
import org.twcore.process.playeraction.PlayerActionFactory;
import org.twcore.process.playeraction.PlayerActionListUtil;
import org.twcore.process.playeraction.impl.AddContentPlayerAction;
import org.twcore.process.playeraction.impl.AddItemPlayerAction;
import org.twcore.process.step.Step;
import org.twcore.process.step.StepExecutionContext;
import org.twcore.process.step.StepResult;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 摆盘流程：管理摆盘的多步骤交互。
 *
 * <p><b>职责：</b>按顺序持有玩家已执行的操作（只追加，不允许跳过/回填），
 * 用当前序列做配方前缀匹配收窄候选，序列完整时记录精确匹配配方供完成步骤使用。</p>
 *
 * <p><b>状态与恢复：</b>候选配方在首次放入或世界就绪（{@code setWorld}）时建立；
 * 操作序列随 NBT 持久化，重启后自动恢复；移除中间步骤会连锁移除其后所有操作。</p>
 */
public class PlatingProcess<T extends BlockEntity & PlatableBlockEntity> extends AbstractProcess<T> {
    /** 执行操作步骤的ID */
    public static final String STEP_PERFORM_ACTION = "perform_action";
    /** 完成流程步骤的ID */
    public static final String STEP_COMPLETE = "complete";

    /** 操作序列：按顺序执行的玩家操作，由本流程统一管理 */
    private final List<PlayerAction> performedActions = new ArrayList<>();

    /** 当前步骤的候选配方列表 */
    private final List<PlatingRecipe> candidateRecipes = new ArrayList<>();

    /** 当前完全匹配的配方（如果存在） */
    @Nullable
    private PlatingRecipe matchedRecipe = null;

    /** 标志：是否正在匹配配方，防止重入 */
    private boolean isMatchingRecipes = false;

    // ==================== 构造器 ====================

    public PlatingProcess() {
        registerSteps();
    }

    private void registerSteps() {
        registerStep(STEP_PERFORM_ACTION, new PerformActionStep());
        registerStep(STEP_COMPLETE, new CompleteStep());
    }

    // ==================== 操作管理方法 ====================

    /**
     * 获取当前已执行的操作序列。
     *
     * <p>返回的列表按照执行顺序排列，第一个元素是第一步执行的操作。
     * 操作序列必须是连续的，不允许有空位或跳过步骤。</p>
     *
     * @return 已执行操作的列表副本，按步骤顺序排列
     */
    public List<PlayerAction> getPerformedActions() {
        return new ArrayList<>(performedActions);
    }

    /**
     * 获取当前已完成的步骤数量。
     *
     * <p>这是一个便捷方法，等价于 {@code getPerformedActions().size()}。</p>
     *
     * @return 已执行操作的数量（当前步骤数）
     */
    public int getStepCount() {
        return performedActions.size();
    }

    /**
     * 检查是否可以在指定步骤执行操作。
     *
     * <p>操作序列始终按顺序追加（不允许空位、回填或覆盖），
     * 因此只有在目标步骤等于当前步骤数时才能执行操作。</p>
     *
     * @param step 要检查的步骤索引
     * @return 如果可以在该步骤执行操作返回 {@code true}
     */
    public boolean canPerformActionAtStep(int step) {
        return step == getStepCount();
    }

    /**
     * 在指定步骤位置执行操作。
     *
     * <p>操作序列必须按顺序追加：目标步骤必须等于当前步骤数
     * （不允许跳过、回填或覆盖），且流程必须处于活动状态。</p>
     *
     * @param step 步骤索引（从 0 开始）
     * @param action 要执行的操作
     * @return 如果执行成功返回 {@code true}，否则返回 {@code false}
     */
    public boolean performAction(int step, PlayerAction action) {
        // 只有流程激活时才允许执行操作
        if (!isActive()) {
            return false;
        }

        // 参数校验：步骤必须非负且操作非空
        if (step < 0 || action == null) {
            return false;
        }

        // 操作序列只能顺序追加
        if (step != performedActions.size()) {
            return false;
        }

        // 添加操作
        performedActions.add(action);
        return true;
    }

    /**
     * 移除指定步骤的操作。
     *
     * <p>根据摆盘逻辑，如果移除的是中间步骤的操作，
     * 则后续所有步骤的操作都会被移除，以保持步骤连续性。</p>
     *
     * @param step 要移除操作的步骤索引（从 0 开始）
     * @return 被移除的操作，如果步骤为空或无效则返回 {@code null}
     */
    @Nullable
    public PlayerAction removeAction(int step) {
        // 验证参数
        if (step < 0 || step >= performedActions.size()) {
            return null;
        }

        PlayerAction removed = performedActions.get(step);
        if (removed == null) {
            return null;
        }

        // 移除该步骤及之后的所有操作（保持连续性）
        while (performedActions.size() > step) {
            performedActions.remove(performedActions.size() - 1);
        }

        return removed;
    }

    /**
     * 清空所有已执行的操作，将摆盘状态重置为初始空状态。
     */
    public void clearPerformedActions() {
        performedActions.clear();
    }

    /**
     * 用给定的完整操作序列恢复摆盘进度。
     *
     * <p>用于揭开盖子时把成品菜肴还原为进行中的摆盘：以菜肴摆盘步骤携带的操作序列
     * 填充流程，候选配方留待后续（世界可用时）恢复。</p>
     *
     * @param actions 配方的完整操作序列
     * @return 恢复成功返回 {@code true}
     */
    public boolean restoreActions(List<PlayerAction> actions) {
        if (actions == null || actions.isEmpty()) {
            return false;
        }

        this.performedActions.clear();
        this.performedActions.addAll(actions);

        resetCandidateState();
        return true;
    }

    // ==================== 候选配方与匹配 ====================

    /**
     * 首次放入物品：以第一步操作筛选初始候选配方。
     *
     * <p>当盘子为空、玩家放入第一个物品时调用，找出所有容器匹配且第一步操作
     * 与所放物品一致的配方，作为本次摆盘的候选集合。</p>
     *
     * @param world 世界实例
     * @param plate 摆盘方块实体
     * @param firstAction 玩家放入的第一个操作
     * @return 如果找到至少一个候选配方返回 {@code true}
     */
    private boolean initializeCandidatesWithFirstAction(World world, PlatableBlockEntity plate, PlayerAction firstAction) {
        isMatchingRecipes = true;
        try {
            RecipeManager recipeManager = world.getRecipeManager();
            List<PlatingRecipe> allRecipes = recipeManager.listAllOfType(ModRecipeTypes.PLATING);

            if (allRecipes.isEmpty() || firstAction == null) {
                return false;
            }

            Identifier containerId = plate.getContainerId();
            List<PlatingRecipe> candidates = allRecipes.stream()
                    .filter(recipe -> recipe.getContainerId().equals(containerId))
                    .filter(recipe -> recipe.getActionCount() > 0
                            && recipe.getActionAt(0).matches(firstAction))
                    .toList();

            if (candidates.isEmpty()) {
                return false;
            }

            candidateRecipes.clear();
            candidateRecipes.addAll(candidates);
            return true;
        } finally {
            isMatchingRecipes = false;
        }
    }

    /**
     * 根据当前已执行的操作序列恢复候选配方。
     *
     * <p>以操作序列为前缀匹配所有配方，并检查是否存在完全匹配。
     * 用于世界就绪（{@code setWorld}）后恢复游戏重启前的摆盘进度，也可作为兜底手段。</p>
     *
     * @param world 世界实例
     * @param plate 摆盘方块实体
     * @return 如果找到至少一个候选配方返回 {@code true}
     */
    public boolean restoreCandidates(World world, PlatableBlockEntity plate) {
        isMatchingRecipes = true;
        try {
            RecipeManager recipeManager = world.getRecipeManager();
            List<PlatingRecipe> allRecipes = recipeManager.listAllOfType(ModRecipeTypes.PLATING);

            if (allRecipes.isEmpty()) {
                return false;
            }

            List<PlayerAction> performedActions = getPerformedActions();
            if (performedActions.isEmpty()) {
                return false;
            }

            Identifier containerId = plate.getContainerId();
            List<PlatingRecipe> candidates = allRecipes.stream()
                    .filter(recipe -> recipe.getContainerId().equals(containerId))
                    .filter(recipe -> recipe.matchesPrefix(performedActions))
                    .toList();

            if (candidates.isEmpty()) {
                return false;
            }

            candidateRecipes.clear();
            candidateRecipes.addAll(candidates);

            // 检查完全匹配，恢复已匹配的配方
            checkForExactMatch(plate, world);
            return true;
        } finally {
            isMatchingRecipes = false;
        }
    }

    /**
     * 世界设置完成后的恢复入口。
     *
     * <p>由方块实体的 {@code setWorld} 重写调用：仅当流程处于活动状态
     * （游戏重启前摆盘尚未完成）且候选列表为空（尚未建立）时，按当前操作序列
     * 恢复候选配方与精确匹配。</p>
     *
     * @param world 世界实例
     * @param entity 摆盘方块实体
     * @return 恢复成功返回 {@code true}
     */
    public boolean restoreAfterWorldSet(World world, T entity) {
        if (!isActive() || !candidateRecipes.isEmpty()) {
            return false;
        }
        return restoreCandidates(world, entity);
    }

    /**
     * 根据下一步操作过滤候选配方。
     *
     * @param nextAction 下一步要执行的操作
     * @param performedActions 已执行的操作列表
     * @return 过滤后的候选配方列表，只包含下一步匹配的配方
     */
    private List<PlatingRecipe> filterCandidatesByNextAction(PlayerAction nextAction, List<PlayerAction> performedActions) {
        return candidateRecipes.stream()
                .filter(recipe -> {
                    // 如果已执行操作数量 >= 配方操作数量，不是有效候选
                    if (performedActions.size() >= recipe.getActionCount()) {
                        return false;
                    }

                    // 检查已执行操作是否是配方的有效前缀
                    if (!recipe.matchesPrefix(performedActions)) {
                        return false;
                    }

                    // 检查下一步是否匹配
                    PlayerAction nextRecipeAction = recipe.getNextAction(performedActions.size());
                    return nextRecipeAction != null && nextAction.matches(nextRecipeAction);
                })
                .collect(Collectors.toList());
    }

    /**
     * 检查当前摆盘状态是否有完全匹配的配方。
     */
    public void checkForExactMatch(PlatableBlockEntity plate, World world) {
        matchedRecipe = candidateRecipes.stream()
                .filter(recipe -> recipe.matches(new PlatingRecipe.PlatingInventory(this, plate), world))
                .findFirst()
                .orElse(null);
    }

    /**
     * 重置候选配方状态，等待下一次初始化。
     */
    private void resetCandidateState() {
        candidateRecipes.clear();
        matchedRecipe = null;
        isMatchingRecipes = false;
    }

    // ==================== 步骤实现类 ====================

    /**
     * 执行操作步骤，处理配方操作的执行。
     *
     * <p>此步骤按如下顺序执行：</p>
     * <ol>
     *   <li>防止配方匹配重入</li>
     *   <li>从上下文创建本次交互对应的操作（空手则直接通过）</li>
     *   <li>候选列表未初始化时：首次放入物品按第一步筛选初始配方；
     *       已有操作序列（重启兜底）则按当前序列恢复候选</li>
     *   <li>按本次操作过滤候选，执行操作并检查是否完全匹配</li>
     * </ol>
     */
    protected class PerformActionStep implements Step<T> {
        private static final PlayerActionFactory.PlayerActionCreator CREATOR =
                PlayerActionCreators.firstNonNull(
                        PlayerActionFactory.getRegisteredCreator(AddContentPlayerAction.TYPE),
                        PlayerActionFactory.getRegisteredCreator(AddItemPlayerAction.TYPE)
                );

        @Override
        public StepResult execute(StepExecutionContext<T> context) {
            // 防止配方匹配重入
            if (isMatchingRecipes) {
                return StepResult.continueSameStep(ActionResult.PASS);
            }

            T plate = context.blockEntity();

            // 从上下文创建本次交互对应的操作
            PlayerAction expectedAction = CREATOR.create(context);

            // 空手交互不产生操作
            if (expectedAction == null) {
                return StepResult.continueSameStep(ActionResult.PASS);
            }

            // 候选列表为空（尚未建立）时，按当前状态建立候选
            if (candidateRecipes.isEmpty()) {
                if (getStepCount() == 0) {
                    // 首次放入物品：以第一步操作筛选初始候选配方
                    if (!initializeCandidatesWithFirstAction(context.world(), plate, expectedAction)) {
                        resetCandidateState();
                        return StepResult.fail(STEP_PERFORM_ACTION, ActionResult.FAIL);
                    }
                } else {
                    // 重启后的兜底：按当前操作序列恢复候选（正常情况下 setWorld 已完成）
                    if (!restoreCandidates(context.world(), plate)) {
                        resetCandidateState();
                        return StepResult.fail(STEP_PERFORM_ACTION, ActionResult.FAIL);
                    }
                }
            }

            // 按下一步操作过滤候选配方
            List<PlatingRecipe> matchingRecipes = filterCandidatesByNextAction(expectedAction, getPerformedActions());
            if (matchingRecipes.isEmpty()) {
                resetCandidateState();
                return StepResult.fail(STEP_PERFORM_ACTION, ActionResult.FAIL);
            }

            candidateRecipes.clear();
            candidateRecipes.addAll(matchingRecipes);

            return executeAction(context, plate, expectedAction, getStepCount());
        }

        /**
         * 执行操作逻辑的公共部分。
         */
        private StepResult executeAction(StepExecutionContext<T> context, T plate,
                                         PlayerAction action, int currentStep) {
            // 验证是否可以在此步骤执行操作
            if (!canPerformActionAtStep(currentStep)) {
                resetCandidateState();
                return StepResult.fail(STEP_PERFORM_ACTION, ActionResult.FAIL);
            }

            // 尝试执行操作
            if (!performAction(currentStep, action)) {
                resetCandidateState();
                return StepResult.fail(STEP_PERFORM_ACTION, ActionResult.FAIL);
            }

            // 执行操作的消耗逻辑
            action.consume(context);
            plate.markDirty();

            // 检查是否有完全匹配的配方
            checkForExactMatch(plate, null);

            return StepResult.continueSameStep(ActionResult.SUCCESS);
        }
    }

    /**
     * 完成流程步骤，处理配方的完成和输出。
     */
    private class CompleteStep implements Step<T> {
        @Override
        public StepResult execute(StepExecutionContext<T> context) {
            PlatableBlockEntity plate = context.blockEntity();
            ItemStack heldItem = context.getHeldItemStack();

            // 检查是否为完成物品
            if (!plate.isCompletionItem(heldItem) || heldItem.isEmpty()) {
                return StepResult.fail(STEP_PERFORM_ACTION, ActionResult.FAIL);
            }

            // 检查是否有完全匹配的配方
            if (matchedRecipe == null) {
                // 如果没有匹配的配方，但玩家手持完成物品，尝试重新检查
                checkForExactMatch(plate, context.world());
                if (matchedRecipe == null) {
                    return StepResult.fail(STEP_PERFORM_ACTION, ActionResult.FAIL);
                }
            }

            // 执行完成逻辑
            plate.onPlatingComplete(context.world(), context.pos(), matchedRecipe, context.player(), context.hand(), context.hit());
            return StepResult.complete(ActionResult.SUCCESS);
        }
    }

    // ==================== 流程控制钩子 ====================

    /**
     * 步骤获取前的预处理钩子。
     *
     * <p>作为 {@link #restoreAfterWorldSet} 的兜底：候选列表未初始化且已有操作序列时，
     * 按当前序列恢复候选；若玩家手持完成物品且存在完全匹配的配方，直接跳转到完成步骤。</p>
     */
    @Override
    protected void beforeGetStep(StepExecutionContext<T> context) {
        T plate = context.blockEntity();
        ItemStack heldItem = context.getHeldItemStack();

        // 兜底：世界恢复未成功时，按当前操作序列重建候选
        if (candidateRecipes.isEmpty() && !performedActions.isEmpty()) {
            restoreCandidates(context.world(), plate);
        }

        // 手持完成物品且存在完全匹配的配方时，跳转到完成步骤
        if (plate.isCompletionItem(heldItem) && !heldItem.isEmpty()) {
            if (matchedRecipe != null) {
                jumpToStep(STEP_COMPLETE);
            }
        }
    }

    // ==================== 生命周期方法 ====================

    @Override
    protected String getInitialStepId() {
        return STEP_PERFORM_ACTION;
    }

    @Override
    protected void onStart(World world, T blockEntity) {
        // 开始新流程时重置候选状态
        resetCandidateState();
    }

    @Override
    protected void onReset() {
        resetCandidateState();
    }

    // ==================== 序列化方法 ====================

    /**
     * 将流程状态写入NBT。
     *
     * <p>除了基类的步骤状态外，还会序列化当前已执行的操作序列。</p>
     *
     * @param nbt 要写入的NBT复合标签
     */
    @Override
    public void writeToNbt(NbtCompound nbt) {
        super.writeToNbt(nbt);
        PlayerActionListUtil.writeActionsToNbt(nbt, performedActions);
    }

    /**
     * 从NBT读取流程状态。
     *
     * <p>操作序列直接从 NBT 恢复；候选配方列表依赖世界无法序列化，
     * 会在世界就绪（{@code setWorld}）后通过 {@link #restoreAfterWorldSet} 恢复。</p>
     *
     * @param nbt 要读取的NBT复合标签
     */
    @Override
    public void readFromNbt(NbtCompound nbt) {
        super.readFromNbt(nbt);
        this.performedActions.clear();
        this.performedActions.addAll(PlayerActionListUtil.readActionsFromNbt(nbt));
        resetCandidateState();
    }

    // ==================== 状态查询方法 ====================

    /**
     * 获取当前候选配方数量。
     */
    public int getCandidateRecipeCount() {
        return candidateRecipes.size();
    }

    /**
     * 获取当前匹配的配方。
     */
    public @Nullable PlatingRecipe getMatchedRecipe() {
        return matchedRecipe;
    }

    /**
     * 检查是否已找到完全匹配的配方。
     */
    public boolean hasExactMatch() {
        return matchedRecipe != null;
    }

    /**
     * 检查候选配方是否已建立。
     *
     * <p>候选列表由当前操作序列推导，未建立与"候选列表为空"等价：
     * 建立成功后候选必然非空，失败或重置后候选为空。</p>
     */
    public boolean isCandidatesInitialized() {
        return !candidateRecipes.isEmpty();
    }

    @Override
    protected String getCustomStatusInfo() {
        StringBuilder info = new StringBuilder();

        // 操作序列信息
        info.append("Performed Actions: ").append(performedActions.size()).append("\n");

        // 候选配方信息
        info.append("Candidate Recipes: ").append(candidateRecipes.size()).append("\n");

        // 匹配的配方信息
        if (matchedRecipe != null) {
            info.append("Matched Recipe: ").append(matchedRecipe.getId().getPath()).append("\n");
            info.append("Recipe Actions: ").append(matchedRecipe.getActionCount()).append("\n");
            info.append("Output Dish: ").append(matchedRecipe.getDishName()).append("\n");
        } else {
            info.append("Matched Recipe: <none>\n");
        }

        // 初始化状态
        info.append("Candidates Initialized: ").append(isCandidatesInitialized()).append("\n");

        // 匹配状态
        info.append("Matching Recipes: ").append(isMatchingRecipes).append("\n");

        // 候选配方详情（仅显示前3个，避免输出过长）
        if (!candidateRecipes.isEmpty()) {
            info.append("Candidate Recipe List:\n");
            int limit = Math.min(candidateRecipes.size(), 3);
            for (int i = 0; i < limit; i++) {
                PlatingRecipe recipe = candidateRecipes.get(i);
                info.append("  ").append(i + 1).append(". ")
                        .append(recipe.getId().getPath())
                        .append(" (actions: ").append(recipe.getActionCount()).append(")\n");
            }
            if (candidateRecipes.size() > limit) {
                info.append("  ... and ").append(candidateRecipes.size() - limit).append(" more recipes not shown\n");
            }
        }
        return info.toString();
    }
}
