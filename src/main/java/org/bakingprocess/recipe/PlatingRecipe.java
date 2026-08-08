package org.bakingprocess.recipe;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.recipe.*;
import net.minecraft.registry.DynamicRegistryManager;
import net.minecraft.util.Identifier;
import net.minecraft.util.collection.DefaultedList;
import net.minecraft.world.World;
import org.bakingprocess.block.entity.PlatableBlockEntity;
import org.bakingprocess.block.process.PlatingProcess;
import org.bakingprocess.content.DishesContent;
import org.bakingprocess.registry.ModRecipeSerializers;
import org.bakingprocess.registry.ModRecipeTypes;
import org.jetbrains.annotations.Nullable;
import org.twcore.api.process.PlayerAction;
import org.twcore.content.Content;
import org.twcore.process.playeraction.impl.AddItemPlayerAction;

import java.util.List;

/**
 * 摆盘配方类，表示一个完整的摆盘配方。
 *
 * <p>摆盘配方由以下部分组成：</p>
 * <ul>
 *   <li><strong>容器</strong>：配方的承载容器（如铁盘、木盘）</li>
 *   <li><strong>操作序列</strong>：按顺序执行的操作列表</li>
 *   <li><strong>输出</strong>：完成摆盘后得到的最终物品</li>
 * </ul>
 *
 * <p><strong>配方匹配规则：</strong></p>
 * <ol>
 *   <li>必须使用正确的容器类型</li>
 *   <li>必须按照操作序列的顺序执行操作</li>
 *   <li>不允许跳过任何操作</li>
 *   <li>当所有操作完成后，使用特定的完成物品触发输出</li>
 * </ol>
 *
 * <p><strong>配方查找：</strong>需要按容器与菜肴反查配方时，
 * 通过 {@link #findRecipe} 从世界配方管理器实时读取。</p>
 */
public class PlatingRecipe implements Recipe<PlatingRecipe.PlatingInventory> {
    /** 配方ID，用于唯一标识此配方 */
    private final Identifier id;

    /** 容器物品类型，表示此配方所需的容器（如铁盘） */
    private final Item container;

    /** 操作序列，按顺序执行的操作列表 */
    private final List<PlayerAction> actions;

    /** 配方输出菜肴，完成所有操作后获得 */
    private final DishesContent output;

    /**
     * 创建摆盘配方。
     *
     * @param id 配方ID，用于唯一标识此配方
     * @param container 容器物品类型
     * @param actions 操作列表，列表顺序即为执行顺序
     * @param output 配方输出物品
     */
    public PlatingRecipe(Identifier id, Item container, List<PlayerAction> actions, Content output) {
        if (output instanceof DishesContent dishes) {
            this.id = id;
            this.container = container;
            this.actions = List.copyOf(actions);
            this.output = dishes;
        } else {
            throw new IllegalArgumentException("The product of the recipe for the dish must be dishes");
        }
    }

    @Override
    public boolean matches(PlatingInventory inventory, World world) {
        // 首先检查容器类型是否匹配
        if (inventory.getContainerType() != this.container) {
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
     * 获取配方所需的容器物品类型。
     */
    public Item getContainer() {
        return container;
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
     * @param index 操作索引（从0开始）
     * @return 该步骤所需的操作
     * @throws IndexOutOfBoundsException 如果索引超出范围
     */
    public PlayerAction getActionAt(int index) {
        return actions.get(index);
    }

    /**
     * 获取配方的成品
     * @return 制作出的菜肴
     */
    public DishesContent getDishes() {
        return this.output;
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

    // ==================== 静态方法 ====================

    /**
     * 通过世界配方管理器查找指定容器与菜肴对应的配方。
     *
     * <p>配方只从 {@code RecipeManager} 实时读取，不存储任何配方实例，
     * 避免数据包重载后引用失效。运行时由调用方保证世界非空。</p>
     *
     * @param world 世界实例
     * @param container 容器物品类型
     * @param dishes 成品菜肴
     * @return 匹配的配方，未找到返回 {@code null}
     */
    @Nullable
    public static PlatingRecipe findRecipe(World world, Item container, DishesContent dishes) {
        if (world == null || container == null || dishes == null) {
            return null;
        }

        return world.getRecipeManager().listAllOfType(ModRecipeTypes.PLATING).stream()
                .filter(recipe -> recipe.getContainer() == container)
                .filter(recipe -> recipe.getDishes() == dishes)
                .findFirst()
                .orElse(null);
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
        return String.format("PlatingRecipe{id=%s, container=%s, actions=%d, output=%s}",
                id, container, actions.size(), output);
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
     *
     * <p>注意：该适配器刻意保持轻薄（核心是 {@link #getStack} 与 {@link #isEmpty}），
     * 方便未来原版把配方接口从 {@code Recipe<C extends Inventory>} 迁移为
     * 仅需物品堆栈访问与空判断的新接口时，改动只集中在本类。</p>
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
         * 获取配方的容器类型。
         */
        public Item getContainerType() {
            return plate.getContainerType();
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
