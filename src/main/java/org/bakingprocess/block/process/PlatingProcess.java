package org.bakingprocess.block.process;

import net.minecraft.block.entity.BlockEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.recipe.RecipeManager;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Identifier;
import net.minecraft.world.World;
import org.bakingprocess.block.entity.PlatableBlockEntity;
import org.bakingprocess.culinary.step.PlatingStep;
import org.bakingprocess.recipe.GenericPlatingCandidate;
import org.bakingprocess.recipe.GenericPlatingRecipe;
import org.bakingprocess.recipe.PlatingCandidate;
import org.bakingprocess.recipe.PlatingRecipe;
import org.bakingprocess.recipe.RecipePlatingCandidate;
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
import java.util.Comparator;
import java.util.List;

/**
 * <h1>摆盘流程</h1>
 * <p>管理摆盘的多步骤交互。</p>
 *
 * <h2>职责</h2>
 * <ul>
 *     <li>按顺序持有玩家已执行的操作（只追加，不允许跳过 / 回填）；</li>
 *     <li>候选池中<b>有序与无序候选并行共存</b>：每次追加先试有序、后试无序，
 *         任一轨可继续即接受；完成时有序优先，无序取"最直接"（冗余维度最少）。</li>
 * </ul>
 *
 * <h2>状态与恢复</h2>
 * <ul>
 *     <li>候选在首次放入或世界就绪（{@code setWorld}）时建立；</li>
 *     <li>操作序列随 NBT 持久化，重启后自动恢复；</li>
 *     <li>移除中间步骤会连锁移除其后所有操作。</li>
 * </ul>
 */
public class PlatingProcess<T extends BlockEntity & PlatableBlockEntity> extends AbstractProcess<T> {
    /** 执行操作步骤的ID */
    public static final String STEP_PERFORM_ACTION = "perform_action";
    /** 完成流程步骤的ID */
    public static final String STEP_COMPLETE = "complete";

    /** 操作序列：按顺序执行的玩家操作，由本流程统一管理 */
    private final List<PlayerAction> performedActions = new ArrayList<>();

    /** 当前候选池：有序与无序候选并行共存 */
    private final List<PlatingCandidate> candidateRecipes = new ArrayList<>();

    /** 当前完全匹配的候选（有序优先；存在时完成步骤使用） */
    @Nullable
    private PlatingCandidate matchedCandidate = null;

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

    // ==================== 候选与匹配 ====================

    /**
     * 建立候选池：取出容器匹配的有序配方与无序规则，包装为候选，
     * 按当前操作序列过滤出合法者。
     *
     * @param world 世界实例
     * @param plate 摆盘方块实体
     * @return 找到至少一个候选返回 {@code true}
     */
    private boolean initializeCandidates(World world, PlatableBlockEntity plate) {
        isMatchingRecipes = true;
        try {
            RecipeManager recipeManager = world.getRecipeManager();
            List<PlayerAction> actions = getPerformedActions();
            Identifier containerId = plate.getContainerId();

            List<PlatingCandidate> candidates = new ArrayList<>();
            for (PlatingRecipe recipe : recipeManager.listAllOfType(ModRecipeTypes.PLATING)) {
                if (recipe.getContainerId().equals(containerId)) {
                    candidates.add(new RecipePlatingCandidate(recipe));
                }
            }
            for (GenericPlatingRecipe rule : recipeManager.listAllOfType(ModRecipeTypes.GENERIC_PLATING)) {
                if (rule.getContainerId().equals(containerId)) {
                    candidates.add(new GenericPlatingCandidate(rule));
                }
            }
            if (candidates.isEmpty()) {
                return false;
            }

            // 按当前序列过滤：空序列保留全部；否则保留"当前序列合法"的候选
            List<PlatingCandidate> accepted = actions.isEmpty()
                    ? candidates
                    : candidates.stream().filter(candidate -> candidate.matchesPrefix(actions)).toList();
            if (accepted.isEmpty()) {
                return false;
            }

            candidateRecipes.clear();
            candidateRecipes.addAll(accepted);

            // 恢复/建立后同步检查完全匹配（揭盖还原、进世界恢复时 matched 得以恢复）
            checkForExactMatch();
            return true;
        } finally {
            isMatchingRecipes = false;
        }
    }

    /**
     * 根据当前已执行的操作序列建立候选池（世界就绪 / 重启兜底共用）。
     *
     * @param world 世界实例
     * @param plate 摆盘方块实体
     * @return 找到至少一个候选返回 {@code true}
     */
    public boolean restoreCandidates(World world, PlatableBlockEntity plate) {
        return initializeCandidates(world, plate);
    }

    /**
     * 世界设置完成后的恢复入口。
     *
     * <p>由方块实体的 {@code setWorld} 重写调用：仅当流程处于活动状态
     * （游戏重启前摆盘尚未完成）且候选列表为空（尚未建立）时，按当前操作序列
     * 建立候选池。</p>
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
     * 按下一步操作过滤候选池：保留加入 next 后仍合法的候选。
     */
    private List<PlatingCandidate> filterCandidatesByNextAction(PlayerAction nextAction, List<PlayerAction> actions) {
        return candidateRecipes.stream()
                .filter(candidate -> candidate.canContinue(actions, nextAction))
                .toList();
    }

    /**
     * 检查候选池中是否存在完全匹配的候选：有序优先，无序取"最直接"（冗余维度最少）。
     */
    public void checkForExactMatch() {
        List<PlayerAction> actions = getPerformedActions();

        for (PlatingCandidate candidate : candidateRecipes) {
            if (candidate instanceof RecipePlatingCandidate && candidate.isComplete(actions)) {
                matchedCandidate = candidate;
                return;
            }
        }

        matchedCandidate = candidateRecipes.stream()
                .filter(candidate -> candidate instanceof GenericPlatingCandidate && candidate.isComplete(actions))
                .min(Comparator
                        .comparingInt((PlatingCandidate candidate) -> ((GenericPlatingCandidate) candidate).redundantDimensions(actions))
                        .thenComparing(candidate -> candidate.getId().toString()))
                .orElse(null);
    }

    /**
     * 重置候选状态，等待下一次初始化。
     */
    private void resetCandidateState() {
        candidateRecipes.clear();
        matchedCandidate = null;
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
     *   <li>候选池为空（尚未建立）时：空盘按第一步筛选，已有操作序列按当前序列恢复</li>
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

            // 候选池为空（尚未建立）时建立候选
            if (candidateRecipes.isEmpty()) {
                if (!initializeCandidates(context.world(), plate)) {
                    resetCandidateState();
                    return StepResult.fail(STEP_PERFORM_ACTION, ActionResult.FAIL);
                }
            }

            // 按本次操作过滤候选（有序前缀 / 无序可完成，至少一轨能通才接受）
            List<PlatingCandidate> matching = filterCandidatesByNextAction(expectedAction, getPerformedActions());
            if (matching.isEmpty()) {
                // 失败：已放原料保留，候选重置等待重新建立
                resetCandidateState();
                return StepResult.fail(STEP_PERFORM_ACTION, ActionResult.FAIL);
            }

            candidateRecipes.clear();
            candidateRecipes.addAll(matching);

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

            // 检查是否有完全匹配的候选
            checkForExactMatch();

            return StepResult.continueSameStep(ActionResult.SUCCESS);
        }
    }

    /**
     * 完成流程步骤：用完全匹配的候选生成摆盘步骤并完成。
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

            // 检查是否有完全匹配的候选
            if (matchedCandidate == null) {
                checkForExactMatch();
                if (matchedCandidate == null) {
                    return StepResult.fail(STEP_PERFORM_ACTION, ActionResult.FAIL);
                }
            }

            // 用玩家实际放入的序列生成摆盘步骤并完成
            PlatingStep step = matchedCandidate.createStep(getPerformedActions());
            plate.onPlatingComplete(context.world(), context.pos(), step, context.player(), context.hand(), context.hit());
            return StepResult.complete(ActionResult.SUCCESS);
        }
    }

    // ==================== 流程控制钩子 ====================

    /**
     * 步骤获取前的预处理钩子。
     *
     * <p>作为 {@link #restoreAfterWorldSet} 的兜底：候选列表未初始化且已有操作序列时，
     * 按当前序列恢复候选；若玩家手持完成物品且存在完全匹配的候选，直接跳转到完成步骤。</p>
     */
    @Override
    protected void beforeGetStep(StepExecutionContext<T> context) {
        T plate = context.blockEntity();
        ItemStack heldItem = context.getHeldItemStack();

        // 兜底：世界恢复未成功时，按当前操作序列重建候选
        if (candidateRecipes.isEmpty() && !performedActions.isEmpty()) {
            restoreCandidates(context.world(), plate);
        }

        // 手持完成物品且存在完全匹配的候选时，跳转到完成步骤
        if (plate.isCompletionItem(heldItem) && !heldItem.isEmpty()) {
            if (matchedCandidate != null) {
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
     * <p>操作序列直接从 NBT 恢复；候选依赖世界无法序列化，
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
     * 获取当前候选数量。
     */
    public int getCandidateRecipeCount() {
        return candidateRecipes.size();
    }

    /**
     * 获取当前完全匹配的候选（有序优先）。
     */
    public @Nullable PlatingCandidate getMatchedCandidate() {
        return matchedCandidate;
    }

    /**
     * 检查是否已找到完全匹配的候选。
     */
    public boolean hasExactMatch() {
        return matchedCandidate != null;
    }

    /**
     * 检查候选是否已建立。
     *
     * <p>候选由当前操作序列推导，未建立与"候选为空"等价：
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

        // 候选信息
        info.append("Candidates: ").append(candidateRecipes.size()).append("\n");

        // 匹配的候选信息
        if (matchedCandidate != null) {
            info.append("Matched Candidate: ").append(matchedCandidate.getId().getPath()).append("\n");
        } else {
            info.append("Matched Candidate: <none>\n");
        }

        // 初始化状态
        info.append("Candidates Initialized: ").append(isCandidatesInitialized()).append("\n");

        // 匹配状态
        info.append("Matching: ").append(isMatchingRecipes).append("\n");

        // 候选详情（仅显示前3个，避免输出过长）
        if (!candidateRecipes.isEmpty()) {
            info.append("Candidate List:\n");
            int limit = Math.min(candidateRecipes.size(), 3);
            for (int i = 0; i < limit; i++) {
                PlatingCandidate candidate = candidateRecipes.get(i);
                info.append("  ").append(i + 1).append(". ")
                        .append(candidate.getId().getPath())
                        .append(candidate instanceof RecipePlatingCandidate ? " (ordered)" : " (generic)")
                        .append("\n");
            }
            if (candidateRecipes.size() > limit) {
                info.append("  ... and ").append(candidateRecipes.size() - limit).append(" more not shown\n");
            }
        }
        return info.toString();
    }
}
