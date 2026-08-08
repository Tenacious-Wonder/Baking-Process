package org.bakingprocess.block.process;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.item.SwordItem;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.recipe.Recipe;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Identifier;
import net.minecraft.util.collection.DefaultedList;
import net.minecraft.world.World;
import org.bakingprocess.BakingProcess;
import org.bakingprocess.recipe.CutRecipe;
import org.bakingprocess.registry.ModItems;
import org.bakingprocess.registry.ModRecipeTypes;
import org.bakingprocess.registry.ModSounds;
import org.twcore.api.block.UpPlaceBlockEntity;
import org.twcore.api.process.AbstractProcess;
import org.twcore.process.step.Step;
import org.twcore.process.step.StepBuilders;
import org.twcore.process.step.StepExecutionContext;
import org.twcore.process.step.StepResult;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

/**
 * 切菜流程处理器，支持特定物品在特定切割次数的特殊交互。
 * <p>
 * 该类管理整个切割过程，包括：
 * - 普通切割步骤
 * - 特殊物品的特殊切割步骤（如胡萝卜）
 * - 切割进度跟踪
 * - 配方匹配与恢复
 * - NBT数据持久化
 *
 * @param <T> 方块实体类型，必须继承自 {@link UpPlaceBlockEntity}
 */
public class CuttingProcess<T extends UpPlaceBlockEntity> extends AbstractProcess<T> {
    /** 普通切割步骤标识 */
    public static final String STEP_CUT = "cut";

    /** 完成步骤标识 */
    public static final String STEP_COMPLETE = "complete";

    /** 空手交互 */
    public static final String STEP_EMPTY= "cut_empty";

    /** 胡萝卜特殊步骤标识 */
    public static final String STEP_CARROT_12 = "cut_carrot_12";

    public static final String STEP_COD_8 = "cut_cod_8";
    public static final String STEP_COD_9 = "cut_cod_9";

    public static final String STEP_COOKED_COD_8 = "cut_cooked_cod_8";
    public static final String STEP_COOKED_COD_9 = "cut_cooked_cod_9";

    public static final String STEP_SALMON_6 = "cut_salmon_6";
    public static final String STEP_SALMON_7 = "cut_salmon_7";

    public static final String STEP_COOKED_SALMON_6 = "cut_cooked_salmon_6";
    public static final String STEP_COOKED_SALMON_7 = "cut_cooked_salmon_7";

    /** 当前活跃的切割配方 */
    private CutRecipe currentRecipe;
    /** 当前已完成的切割次数（从0开始） */
    private int currentCut;
    /** 配方要求的总切割次数 */
    private int totalCuts;
    /** 保存的配方ID，用于NBT数据恢复 */
    private String savedRecipeId;
    /** 输入物品堆栈 */
    private ItemStack inputStack;

    /**
     * 特殊步骤触发条件映射：物品 -> (切割次数 -> 步骤ID)
     * <p>
     * 用于定义特定物品在特定切割次数触发的特殊步骤。
     */
    private static final Map<Item, Map<Integer, String>> SPECIAL_STEP_TRIGGERS = new HashMap<>();

    /** 特殊步骤实例映射：步骤ID -> 步骤实例 */
    private static final Map<String, Function<CuttingProcess<?>, Step<UpPlaceBlockEntity>>> SPECIAL_STEP_INSTANCES = new HashMap<>();

    public CuttingProcess() {
        super();
        this.inputStack = ItemStack.EMPTY;
        this.savedRecipeId = "";

        registerSteps();
    }

    // ============ 步骤实现类 ============

    /**
     * 普通切割步骤实现。
     * <p>
     * 需要玩家手持菜刀，每次执行增加切割次数，
     * 消耗工具耐久并更新库存状态。
     */
    private class CuttingStep implements Step<T> {

        @Override
        public StepResult execute(StepExecutionContext<T> context) {
            // 验证切割工具
            if (!isValidCuttingTool(context.getHeldItemStack())) {
                return StepResult.fail(STEP_CUT, ActionResult.FAIL);
            }

            // 验证活跃配方
            if (currentRecipe == null) {
                return StepResult.fail(STEP_CUT, ActionResult.FAIL);
            }

            // 播放音效和粒子效果
            context.playSound(ModSounds.CUT);
            context.spawnItemParticles(inputStack);
            currentCut++;

            // 服务器端执行切割逻辑
            if (context.isServerSide()) {
                updateInventory(context.blockEntity(), currentCut);
                consumeToolDurability(context);
                context.blockEntity().markDirtyAndSync();
            }

            // 检查是否完成所有切割
            if (currentCut >= totalCuts) {
                return StepResult.nextStep(STEP_COMPLETE, ActionResult.SUCCESS);
            } else {
                return StepResult.continueSameStep(ActionResult.SUCCESS);
            }
        }
    }

    /**
     * 空步骤实现，用于特殊切割步骤中不需要工具检查的中间步骤。
     */
    private class EmptyStep implements Step<T> {

        @Override
        public StepResult execute(StepExecutionContext<T> context) {
            if (currentRecipe == null) {
                return StepResult.fail(STEP_CUT, ActionResult.FAIL);
            }

            context.playSound(SoundEvents.BLOCK_STONE_PLACE);

            if (context.isServerSide()) {
                currentCut++;
                updateInventory(context.blockEntity(), currentCut);
                context.blockEntity().markDirtyAndSync();
            }

            if (currentCut >= totalCuts) {
                return StepResult.nextStep(STEP_COMPLETE, ActionResult.SUCCESS);
            } else {
                return StepResult.continueSameStep(ActionResult.SUCCESS);
            }
        }
    }

    // ============ 步骤执行方法 ============

    /**
     * 执行完成步骤，给予玩家所有切割产物。
     *
     * @param context 步骤执行上下文
     * @return 操作结果
     */
    private ActionResult executeComplete(StepExecutionContext<T> context) {
        if (context.isServerSide()) {
            giveAllItemsToPlayer(context.blockEntity(), context.player());
            context.playSound(SoundEvents.ENTITY_CHICKEN_EGG);
            reset();
            context.blockEntity().markDirtyAndSync();
        } else {
            context.blockEntity().clear();
        }

        return ActionResult.SUCCESS;
    }

    // ============ 步骤注册 ============

    /**
     * 注册所有切割步骤。
     */
    private void registerSteps() {
        // 普通切割步骤
        registerStep(STEP_CUT, new CuttingStep());

        // 完成步骤
        registerStep(STEP_COMPLETE, StepBuilders.complete(this::executeComplete));

        // 空手交互特殊步骤
        registerStep(STEP_EMPTY, new EmptyStep());

        for (String id : SPECIAL_STEP_INSTANCES.keySet()) {
            Function<CuttingProcess<?>, Step<UpPlaceBlockEntity>> function = SPECIAL_STEP_INSTANCES.get(id);
            Step<UpPlaceBlockEntity> step = function.apply(this);

            @SuppressWarnings("unchecked")
            Step<T> typedStep = (Step<T>) step;
            registerStep(id, typedStep);
        }
    }

    /**
     * 注册特殊给予步骤
     *
     * @param item 触发物品
     * @param cutNumber 触发切割次数（从0开始）
     * @param stepId 步骤ID
     * @param itemsToGive 要给予的物品
     */
    public static void registerGiveStep(Item item, int cutNumber, String stepId, ItemStack... itemsToGive) {
        // 注册步骤
        SPECIAL_STEP_INSTANCES.put(stepId, process ->
                StepBuilders.simple(
                        ctx -> {
                            if (ctx.isServerSide()) {
                                process.currentCut++;
                                process.updateInventory(ctx.blockEntity(), process.currentCut);
                                for (ItemStack stack : itemsToGive) {
                                    ctx.giveStack(stack.copy());
                                }
                                ctx.playSound(SoundEvents.ENTITY_ITEM_PICKUP);
                                ctx.blockEntity().markDirtyAndSync();
                            }
                            return ActionResult.SUCCESS;
                        },
                        STEP_COMPLETE
                ));

        // 添加到触发映射
        SPECIAL_STEP_TRIGGERS
                .computeIfAbsent(item, k -> new HashMap<>())
                .put(cutNumber, stepId);
    }

    /**
     * 注册空手操作步骤映射。
     *
     * @param item 触发物品
     * @param cutNumber 触发切割次数（从0开始）
     */
    public static void registerEmptyStep(Item item, int... cutNumber) {
        for (int i : cutNumber) {
            SPECIAL_STEP_TRIGGERS
                    .computeIfAbsent(item, k -> new HashMap<>())
                    .put(i, STEP_EMPTY);
        }
    }

    // ============ 辅助方法 ============

    /**
     * 检查工具是否为有效的工具（菜刀）。
     *
     * @param stack 待检查的物品堆栈
     * @return 如果是菜刀则返回true
     */
    public boolean isValidCuttingTool(ItemStack stack) {
        return stack.getItem() instanceof SwordItem;
    }

    /**
     * 消耗工具耐久度。
     *
     * @param context 步骤执行上下文
     */
    private void consumeToolDurability(StepExecutionContext<T> context) {
        ItemStack tool = context.getHeldItemStack();
        if (!context.isCreateMode() && tool.isDamageable()) {
            tool.damage(1, context.player(), p -> p.sendToolBreakStatus(context.hand()));
        }
    }

    /**
     * 根据切割次数更新库存状态。
     *
     * @param inventory 目标库存
     * @param cutIndex 当前切割次数
     */
    private void updateInventory(Inventory inventory, int cutIndex) {
        if (currentRecipe == null || inventory == null) return;

        DefaultedList<ItemStack> state = currentRecipe.getCutState(cutIndex);
        int slots = Math.min(state.size(), 5);

        for (int i = 0; i < slots; i++) {
            ItemStack stack = state.get(i);
            inventory.setStack(i, !stack.isEmpty() ? stack.copy() : ItemStack.EMPTY);
        }
    }

    /**
     * 将库存中所有物品给予玩家。
     *
     * @param inventory 源库存
     * @param player 目标玩家
     */
    private void giveAllItemsToPlayer(Inventory inventory, PlayerEntity player) {
        if (inventory == null || player == null) return;

        for (int i = 0; i < inventory.size(); i++) {
            ItemStack stack = inventory.getStack(i);
            if (!stack.isEmpty()) {
                if (!player.giveItemStack(stack.copy())) {
                    player.dropItem(stack.copy(), false);
                }

                inventory.setStack(i, ItemStack.EMPTY);
            }
        }
    }

    /**
     * 检查当前切割是否需要执行特殊步骤。
     *
     * @return 特殊步骤ID，如果没有则返回null
     */
    private String checkSpecialStep() {
        Map<Integer, String> triggers = SPECIAL_STEP_TRIGGERS.get(inputStack.getItem());
        if (triggers != null) {
            String stepId = triggers.get(currentCut);
            if (stepId != null && steps.containsKey(stepId)) {
                return stepId;
            }
        }

        return null;
    }

    // ============ AbstractProcess方法实现 ============

    @Override
    protected String getInitialStepId() {
        return STEP_CUT;
    }

    @Override
    protected void onStart(World world, T blockEntity) {
        currentCut = 0;
        inputStack = blockEntity.getStack(0);

        Optional<CutRecipe> recipe = world.getRecipeManager()
                .getFirstMatch(ModRecipeTypes.CUT, blockEntity, world);

        if (recipe.isPresent()) {
            blockEntity.clear();
            currentRecipe = recipe.get();
            savedRecipeId = currentRecipe.getId().toString();
            totalCuts = currentRecipe.getTotalCuts();
        }
    }

    @Override
    protected void onReset() {
        currentRecipe = null;
        currentCut = 0;
        totalCuts = 0;
        savedRecipeId = "";
        inputStack = ItemStack.EMPTY;
    }

    @Override
    protected void beforeGetStep(StepExecutionContext<T> context) {
        // 尝试恢复配方
        if (currentRecipe == null) {
            restoreRecipeFromId(context.world(), savedRecipeId);
        }

        // 检查特殊步骤
        String specialStepId = checkSpecialStep();
        if (specialStepId != null) {
            jumpToStep(specialStepId);
        }
    }

    // ============ NBT持久化 ============

    @Override
    public void writeToNbt(NbtCompound nbt) {
        super.writeToNbt(nbt);

        nbt.putInt("CurrentCut", currentCut);
        nbt.putInt("TotalCuts", totalCuts);

        if (!inputStack.isEmpty()) {
            NbtCompound inputNbt = new NbtCompound();
            inputStack.writeNbt(inputNbt);
            nbt.put("InputItem", inputNbt);
        }

        if (currentRecipe != null) {
            nbt.putString("RecipeId", currentRecipe.getId().toString());
        } else {
            nbt.putString("RecipeId", savedRecipeId);
        }
    }

    @Override
    public void readFromNbt(NbtCompound nbt) {
        super.readFromNbt(nbt);

        currentCut = nbt.getInt("CurrentCut");
        totalCuts = nbt.getInt("TotalCuts");

        if (nbt.contains("InputItem")) {
            inputStack = ItemStack.fromNbt(nbt.getCompound("InputItem"));
        }

        if (nbt.contains("RecipeId")) {
            savedRecipeId = nbt.getString("RecipeId");
        }
    }

    // ============ 获取器方法 ============

    /**
     * 获取当前活跃的切割配方。
     *
     * @return 当前配方，如果没有则返回null
     */
    public CutRecipe getCurrentRecipe() {
        return currentRecipe;
    }

    /**
     * 获取当前切割进度（0.0 - 1.0）。
     *
     * @return 切割进度，0表示未开始，1表示完成
     */
    public float getProgress() {
        return totalCuts <= 0 ? 0.0f : Math.min((float) currentCut / totalCuts, 1.0f);
    }

    // ============ 设置器方法 ============

    /**
     * 设置当前切割配方。
     *
     * @param recipe 切割配方
     */
    public void setCurrentRecipe(CutRecipe recipe) {
        this.currentRecipe = recipe;

        if (recipe != null) {
            this.totalCuts = recipe.getTotalCuts();
            this.savedRecipeId = recipe.getId().toString();
        } else {
            this.totalCuts = 0;
            this.savedRecipeId = "";
        }
    }

    /**
     * 设置当前切割次数。
     *
     * @param cut 切割次数
     */
    public void setCurrentCut(int cut) {
        this.currentCut = cut;
    }

    /**
     * 从配方ID恢复切割配方。
     *
     * @param world 世界实例
     * @param recipeId 配方ID
     */
    public void restoreRecipeFromId(World world, String recipeId) {
        if (world == null || recipeId == null || recipeId.isEmpty()) {
            BakingProcess.LOGGER.warn("Recipe recovery was not successful");
            return;
        }

        try {
            Identifier id = new Identifier(recipeId);
            Optional<? extends Recipe<?>> recipe = world.getRecipeManager().get(id);

            if (recipe.isPresent() && recipe.get() instanceof CutRecipe cutRecipe) {
                setCurrentRecipe(cutRecipe);
            }
        } catch (Exception e) {
            BakingProcess.LOGGER.warn("Ineffective Recipe:{}", recipeId);
        }
    }

    @Override
    protected String getCustomStatusInfo() {
        StringBuilder info = new StringBuilder();

        // 切割进度信息
        info.append("Cut Progress: ").append(currentCut).append("/").append(totalCuts).append("\n");
        info.append("Progress: ").append(String.format("%.1f%%", getProgress() * 100)).append("\n");

        // 配方信息
        if (currentRecipe != null) {
            info.append("Current Recipe: ").append(currentRecipe.getId().getPath()).append("\n");
            info.append("Recipe Steps: ").append(totalCuts).append("\n");
        } else {
            info.append("Current Recipe: <none>\n");
        }

        // 输入物品信息
        if (!inputStack.isEmpty()) {
            info.append("Input Item: ").append(inputStack.getItem().getName().getString());
            if (inputStack.getCount() > 1) {
                info.append(" x").append(inputStack.getCount());
            }
            info.append("\n");
        } else {
            info.append("Input Item: <empty>\n");
        }

        // 特殊步骤信息
        String specialStepId = checkSpecialStep();
        if (specialStepId != null) {
            info.append("Pending Special Step: ").append(specialStepId).append("\n");
        }

        // NBT数据恢复状态
        if (savedRecipeId != null && !savedRecipeId.isEmpty()) {
            info.append("Saved Recipe ID: ").append(savedRecipeId).append("\n");
        }

        return info.toString();
    }

    // ============ 状态管理 ============

    /**
     * 获取当前切割流程的状态快照。
     *
     * @return 切割状态对象
     */
    public CuttingState getState() {
        return new CuttingState(
                currentCut,
                totalCuts,
                isActive,
                inputStack,
                checkSpecialStep() != null
        );
    }

    /**
     * 切割流程状态数据类。
     * <p>
     * 用于封装切割流程的当前状态，便于数据传输和渲染。
     *
     * @param currentCut 当前切割次数
     * @param totalCuts 总切割次数
     * @param hasRecipe 是否有活跃配方
     * @param hasPendingSpecialStep 是否有待处理的特殊步骤
     */
    public record CuttingState(
            int currentCut,
            int totalCuts,
            boolean hasRecipe,
            ItemStack inputStack,
            boolean hasPendingSpecialStep
    ) {}

    static {
        // 注册空步骤
        registerEmptyStep(Items.CARROT, 9, 10);
        registerEmptyStep(Items.APPLE, 5);
        registerEmptyStep(Items.COD, 6);
        registerEmptyStep(Items.COOKED_COD, 6);

        // 注册给予步骤
        registerGiveStep(Items.CARROT, 11, STEP_CARROT_12,
                new ItemStack(ModItems.CARROT_SLICES, 1),
                new ItemStack(ModItems.CARROT_HEAD, 1));

        registerGiveStep(Items.COD, 7, STEP_COD_8,
                new ItemStack(ModItems.COD_CUBES, 1));
        registerGiveStep(Items.COD, 8, STEP_COD_9,
                new ItemStack(ModItems.COD_CUBES, 1));

        registerGiveStep(Items.COOKED_COD, 7, STEP_COOKED_COD_8,
                new ItemStack(ModItems.COOKED_COD_CUBES, 1));
        registerGiveStep(Items.COOKED_COD, 8, STEP_COOKED_COD_9,
                new ItemStack(ModItems.COOKED_COD_CUBES, 1));

        registerGiveStep(Items.SALMON, 5, STEP_SALMON_6,
                new ItemStack(ModItems.SALMON_CUBES, 1));
        registerGiveStep(Items.SALMON, 6, STEP_SALMON_7,
                new ItemStack(ModItems.SALMON_CUBES, 1));

        registerGiveStep(Items.COOKED_SALMON, 5, STEP_COOKED_SALMON_6,
                new ItemStack(ModItems.COOKED_SALMON_CUBES, 1));
        registerGiveStep(Items.COOKED_SALMON, 6, STEP_COOKED_SALMON_7,
                new ItemStack(ModItems.COOKED_SALMON_CUBES, 1));
    }
}
