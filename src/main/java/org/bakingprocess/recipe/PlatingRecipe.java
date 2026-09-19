package org.bakingprocess.recipe;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import net.minecraft.recipe.*;
import net.minecraft.registry.DynamicRegistryManager;
import net.minecraft.util.Identifier;
import net.minecraft.util.collection.DefaultedList;
import net.minecraft.world.World;
import org.bakingprocess.block.entity.PlatableBlockEntity;
import org.bakingprocess.block.process.PlatingProcess;
import org.bakingprocess.culinary.step.PlatingStep;
import org.bakingprocess.registry.ModRecipeSerializers;
import org.bakingprocess.registry.ModRecipeTypes;
import org.jetbrains.annotations.Nullable;
import org.twcore.api.process.PlayerAction;
import org.twcore.process.playeraction.impl.AddItemPlayerAction;

import java.util.List;

/**
 * <h1>摆盘配方</h1>
 * <p>按"容器 + 有序操作序列"产出摆盘加工步骤（{@link PlatingStep}）。</p>
 *
 * <h2>职责</h2>
 * <ul>
 *     <li><b>匹配</b>：{@link #matches} / {@link #matchesPrefix} 判定给定操作序列
 *         是否构成（或前缀匹配）本配方；</li>
 *     <li><b>生成步骤</b>：{@link #createStep()} 用配方数据（菜标识、口数、可食性）
 *         生成 PlatingStep，供流程组合菜肴。</li>
 * </ul>
 *
 * <p>菜标识（{@code dish_name}）是纯 {@link Identifier}，不依赖内容物注册，
 * 摆盘完成的菜由此派生显示名与渲染模型。</p>
 */
public class PlatingRecipe implements Recipe<PlatingRecipe.PlatingInventory> {
    /** 配方ID，用于唯一标识此配方 */
    private final Identifier id;

    /** 容器标识，表示此配方所需的容器（如 {@code baking_process:iron_plate}） */
    private final Identifier containerId;

    /** 操作序列，按顺序执行的操作列表 */
    private final List<PlayerAction> actions;

    /** 目标菜标识（显示名与渲染模型派生的依据） */
    private final Identifier dishName;

    /** 目标菜口数（edible=false 时为熟菜口数，edible=true 时为真实口数） */
    private final int eatCount;

    /** 摆完是否直接可食 */
    private final boolean edible;

    public PlatingRecipe(Identifier id, Identifier containerId, List<PlayerAction> actions,
                         Identifier dishName, int eatCount, boolean edible) {
        this.id = id;
        this.containerId = containerId;
        this.actions = List.copyOf(actions);
        this.dishName = dishName;
        this.eatCount = eatCount;
        this.edible = edible;
    }

    @Override
    public boolean matches(PlatingInventory inventory, World world) {
        // 首先检查容器标识是否匹配
        if (!inventory.getContainerId().equals(this.containerId)) {
            return false;
        }

        // 获取当前已执行的操作列表
        List<PlayerAction> performedActions = inventory.getPerformedActions();

        // 检查操作数量是否匹配
        if (performedActions.size() != this.actions.size()) {
            return false;
        }

        // 检查每个操作是否匹配
        for (int i = 0; i < actions.size(); i++) {
            if (!actions.get(i).matches(performedActions.get(i))) {
                return false;
            }
        }

        return true;
    }

    @Override
    public ItemStack craft(PlatingInventory inventory, DynamicRegistryManager registryManager) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean fits(int width, int height) {
        // 摆盘配方不使用传统的物品栏格子，总是返回true
        return true;
    }

    @Override
    public ItemStack getOutput(DynamicRegistryManager registryManager) {
        return ItemStack.EMPTY;
    }

    @Override
    public Identifier getId() {
        return id;
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return ModRecipeSerializers.PLATING;
    }

    @Override
    public RecipeType<?> getType() {
        return ModRecipeTypes.PLATING;
    }

    /**
     * 生成摆盘加工步骤：菜标识、口数与可食性均来自配方。
     */
    public PlatingStep createStep() {
        return new PlatingStep(actions, dishName, eatCount, edible, false);
    }

    /**
     * 获取配方所需的容器标识。
     */
    public Identifier getContainerId() {
        return containerId;
    }

    /**
     * 获取配方的操作序列。
     *
     * <p>返回的是操作序列的不可修改副本，确保外部不能修改内部状态。</p>
     */
    public List<PlayerAction> getActions() {
        return actions;
    }

    /**
     * 获取配方操作数量。
     */
    public int getActionCount() {
        return actions.size();
    }

    /**
     * 获取指定索引的操作。
     *
     * @param index 索引（从0开始）
     * @return 该步骤所需的操作
     * @throws IndexOutOfBoundsException 如果索引超出范围
     */
    public PlayerAction getActionAt(int index) {
        return actions.get(index);
    }

    /**
     * 获取目标菜标识。
     */
    public Identifier getDishName() {
        return dishName;
    }

    /**
     * 获取目标菜口数。
     */
    public int getEatCount() {
        return eatCount;
    }

    /**
     * 摆完是否直接可食。
     */
    public boolean isEdible() {
        return edible;
    }

    // ==================== 配方匹配辅助方法 ====================

    /**
     * 检查给定的已执行操作列表是否与配方的所有操作完全匹配。
     */
    public boolean matchesActions(List<PlayerAction> performedActions) {
        // 检查操作数量是否相同
        if (performedActions.size() != this.actions.size()) {
            return false;
        }

        // 检查每个操作
        for (int i = 0; i < actions.size(); i++) {
            if (!actions.get(i).matches(performedActions.get(i))) {
                return false;
            }
        }

        return true;
    }

    /**
     * 检查给定的已执行操作列表是否是此配方的有效前缀。
     */
    public boolean matchesPrefix(List<PlayerAction> performedActions) {
        // 如果已执行的操作数量超过配方操作数量，则不是有效前缀
        if (performedActions.size() > this.actions.size()) {
            return false;
        }

        // 检查已执行的每个操作是否与对应操作匹配
        for (int i = 0; i < performedActions.size(); i++) {
            if (!actions.get(i).matches(performedActions.get(i))) {
                return false;
            }
        }

        return true;
    }

    /**
     * 获取配方所需的输入物品列表（用于UI显示）。
     */
    public DefaultedList<Ingredient> getIngredients() {
        DefaultedList<Ingredient> ingredients = DefaultedList.of();

        // 将每个操作转换为Ingredient
        for (PlayerAction action : actions) {
            ItemStack stack = action.toItemStack();
            if (!stack.isEmpty()) {
                ingredients.add(Ingredient.ofStacks(stack));
            } else {
                // 对于没有物品表示的操作，添加空Ingredient
                ingredients.add(Ingredient.EMPTY);
            }
        }

        return ingredients;
    }

    /**
     * 装盘是设备配方，不在原版配方书的分类体系中；标记忽略可避免客户端每次加载都报告未知配方分类。
     */
    @Override
    public boolean isIgnoredInRecipeBook() {
        return true;
    }

    /**
     * 获取下一个操作（如果有）。
     */
    @Nullable
    public PlayerAction getNextAction(int currentStep) {
        if (currentStep < 0 || currentStep >= actions.size()) {
            return null;
        }
        return actions.get(currentStep);
    }

    @Override
    public String toString() {
        return String.format("PlatingRecipe{id=%s, container=%s, actions=%d, dish=%s}",
                id, containerId, actions.size(), dishName);
    }

    // ==================== 配方匹配适配器 ====================

    /**
     * 摆盘配方匹配使用的物品栏适配器。
     *
     * <p>原版 {@link Recipe} 接口要求操作对象实现 {@link Inventory}，而摆盘方块实体
     * 的语义不是物品栏（操作序列由 {@link PlatingProcess} 统一管理），因此提供此
     * 轻量适配器：它只用于在配方匹配时按需读取容器类型与已执行操作，不持有任何状态。</p>
     *
     * <p>适配器的核心方法仅供 {@link PlatingRecipe#matches} 使用；通过
     * {@link Inventory} 接口暴露的写入方法只是对流程操作序列的委托，
     * 便于外部系统（如原版物品栏交互）复用。</p>
     */
    public static final class PlatingInventory implements Inventory {
        /** 持有操作序列的摆盘流程 */
        private final PlatingProcess<?> process;
        /** 提供容器身份的摆盘方块实体 */
        private final PlatableBlockEntity plate;

        public PlatingInventory(PlatingProcess<?> process, PlatableBlockEntity plate) {
            this.process = process;
            this.plate = plate;
        }

        /**
         * 获取配方的容器标识。
         */
        public Identifier getContainerId() {
            return plate.getContainerId();
        }

        /**
         * 获取流程当前已执行的操作序列。
         */
        public List<PlayerAction> getPerformedActions() {
            return process.getPerformedActions();
        }

        @Override
        public int size() {
            // 固定容量仅用于满足 Inventory 接口约束；实际容量由配方决定
            return 16;
        }

        @Override
        public boolean isEmpty() {
            return process.getPerformedActions().isEmpty();
        }

        @Override
        public ItemStack getStack(int slot) {
            List<PlayerAction> actions = process.getPerformedActions();
            if (slot >= 0 && slot < actions.size()) {
                PlayerAction action = actions.get(slot);
                return action != null ? action.toItemStack() : ItemStack.EMPTY;
            }
            return ItemStack.EMPTY;
        }

        @Override
        public ItemStack removeStack(int slot) {
            PlayerAction action = process.removeAction(slot);
            return action != null ? action.toItemStack() : ItemStack.EMPTY;
        }

        @Override
        public ItemStack removeStack(int slot, int amount) {
            return removeStack(slot);
        }

        @Override
        public void setStack(int slot, ItemStack stack) {
            if (slot < 0 || slot >= size()) {
                return;
            }

            if (!stack.isEmpty()) {
                PlayerAction action = createActionFromItemStack(stack);
                if (action != null) {
                    process.performAction(slot, action);
                }
            } else {
                process.removeAction(slot);
            }
        }

        @Override
        public boolean canPlayerUse(PlayerEntity player) {
            return true;
        }

        @Override
        public void clear() {
            process.clearPerformedActions();
        }

        @Override
        public void markDirty() {
            // 适配器为瞬态对象，不持有任何状态，无需标记脏数据
        }

        /**
         * 将物品堆栈转换为默认的添加物品操作。
         */
        private static PlayerAction createActionFromItemStack(ItemStack stack) {
            if (stack.isEmpty()) {
                return null;
            }
            return new AddItemPlayerAction(stack.getItem(), stack.getCount());
        }
    }
}
