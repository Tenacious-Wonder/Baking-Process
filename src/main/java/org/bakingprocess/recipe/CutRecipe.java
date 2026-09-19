package org.bakingprocess.recipe;

import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import net.minecraft.recipe.Ingredient;
import net.minecraft.recipe.Recipe;
import net.minecraft.recipe.RecipeSerializer;
import net.minecraft.recipe.RecipeType;
import net.minecraft.registry.DynamicRegistryManager;
import net.minecraft.util.Identifier;
import net.minecraft.util.collection.DefaultedList;
import net.minecraft.world.World;
import org.bakingprocess.registry.ModRecipeSerializers;
import org.bakingprocess.registry.ModRecipeTypes;

import java.util.Map;

/**
 * 支持多步骤切割的切菜配方
 */
public class CutRecipe implements Recipe<Inventory> {
    private final Identifier id;
    private final Ingredient input;
    private final int totalCuts; // 总共需要切的次数
    private final Map<Integer, DefaultedList<ItemStack>> cutStateMap; // 第几刀对应的库存状态
    private final DefaultedList<ItemStack> defaultState; // 默认库存状态（5个槽位）

    public CutRecipe(Identifier id, Ingredient input, int totalCuts,
                     Map<Integer, DefaultedList<ItemStack>> cutStateMap,
                     DefaultedList<ItemStack> defaultState) {
        this.id = id;
        this.input = input;
        this.totalCuts = totalCuts;
        this.cutStateMap = cutStateMap;
        this.defaultState = defaultState;
    }

    @Override
    public boolean matches(Inventory inventory, World world) {
        // 只检查主槽位（索引0）
        return input.test(inventory.getStack(0));
    }

    @Override
    public ItemStack craft(Inventory inventory, DynamicRegistryManager registryManager) {
        // 返回最后一刀时的库存状态第一个物品
        DefaultedList<ItemStack> finalState = getCutState(totalCuts);
        if (!finalState.isEmpty() && !finalState.get(0).isEmpty()) {
            return finalState.get(0).copy();
        }
        return ItemStack.EMPTY;
    }

    @Override
    public boolean fits(int width, int height) {
        return true;
    }

    @Override
    public ItemStack getOutput(DynamicRegistryManager registryManager) {
        // 返回最后一刀时的库存状态第一个物品
        DefaultedList<ItemStack> finalState = getCutState(totalCuts);
        if (!finalState.isEmpty() && !finalState.get(0).isEmpty()) {
            return finalState.get(0).copy();
        }
        return ItemStack.EMPTY;
    }

    public ItemStack getOutput() {
        // 返回最后一刀时的库存状态第一个物品
        DefaultedList<ItemStack> finalState = getCutState(totalCuts);
        if (!finalState.isEmpty() && !finalState.get(0).isEmpty()) {
            return finalState.get(0).copy();
        }
        return ItemStack.EMPTY;
    }

    @Override
    public Identifier getId() {
        return id;
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return ModRecipeSerializers.CUT;
    }

    @Override
    public RecipeType<?> getType() {
        return ModRecipeTypes.CUT;
    }

    public Ingredient getInput() {
        return input;
    }

    public int getTotalCuts() {
        return totalCuts;
    }

    public DefaultedList<ItemStack> getCutState(int cutIndex) {
        return cutStateMap.getOrDefault(cutIndex, defaultState);
    }

    public DefaultedList<ItemStack> getDefaultState() {
        return defaultState;
    }

    public Map<Integer, DefaultedList<ItemStack>> getCutStateMap() {
        return cutStateMap;
    }

    @Override
    public DefaultedList<Ingredient> getIngredients() {
        DefaultedList<Ingredient> ingredients = DefaultedList.of();
        ingredients.add(input);
        return ingredients;
    }

    /**
     * 切菜是设备配方，不在原版配方书的分类体系中；标记忽略可避免客户端每次加载都报告未知配方分类。
     */
    @Override
    public boolean isIgnoredInRecipeBook() {
        return true;
    }

    /**
     * 获取完成切割后的输出数量
     */
    public int getOutputCount() {
        DefaultedList<ItemStack> finalState = getCutState(totalCuts);
        if (!finalState.isEmpty() && !finalState.get(0).isEmpty()) {
            return finalState.get(0).getCount();
        }
        return 0;
    }
}