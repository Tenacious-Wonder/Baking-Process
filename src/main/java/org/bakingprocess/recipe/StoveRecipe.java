package org.bakingprocess.recipe;

import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import net.minecraft.recipe.Recipe;
import net.minecraft.recipe.RecipeSerializer;
import net.minecraft.recipe.RecipeType;
import net.minecraft.registry.DynamicRegistryManager;
import net.minecraft.util.Identifier;
import net.minecraft.world.World;
import org.bakingprocess.culinary.Culinary;
import org.bakingprocess.culinary.carrier.ItemStackVessel;
import org.bakingprocess.culinary.step.BakingStep;
import org.bakingprocess.registry.ModRecipeSerializers;
import org.bakingprocess.registry.ModRecipeTypes;
import org.twcore.api.content.ContainerUtil;
import org.twcore.content.Content;

/**
 * 烤炉配方：输入 / 输出可为物品堆栈、内容物或菜标识（culinary）。
 *
 * <p><b>culinary 适配：</b>输入为菜标识时，槽位堆栈需能转为 {@link ItemStackVessel}
 * 且菜肴身份（{@link Culinary#getIdentifier()}）与标识一致；输出为菜标识时，
 * 烤制完成生成 {@link BakingStep} 应用到容器内菜肴（时长来自配方，名称与口数由
 * {@code BakingStep} 从摆盘步骤推导）。</p>
 */
public class StoveRecipe implements Recipe<Inventory> {

    /**
     * 配方组件的三态：物品堆栈 / 内容物 / 菜标识（culinary）。
     */
    public sealed interface Component {
        /** 普通物品堆栈。 */
        record ItemComp(ItemStack stack) implements Component {}

        /** 内容物。 */
        record ContentComp(Content content) implements Component {}

        /** 菜标识（经 {@link ItemStackVessel} 与容器内菜肴的身份匹配）。 */
        record CulinaryComp(Identifier dishId) implements Component {}
    }

    protected final Identifier id;
    protected final Component input;
    protected final Component output;
    protected final int bakingTime;
    protected final int MaxInputCount;

    public StoveRecipe(Identifier id, Component input, Component output, int MaxInputCount, int bakingTime) {
        this.id = id;
        this.input = input;
        this.output = output;
        this.MaxInputCount = MaxInputCount;
        this.bakingTime = bakingTime;
    }

    public int getMaxInputCount() {
        return MaxInputCount;
    }

    @Override
    public boolean matches(Inventory inventory, World world) {
        ItemStack stack = inventory.getStack(0);

        if (input instanceof Component.ItemComp comp) {
            return ItemStack.areItemsEqual(stack, comp.stack());
        }
        if (input instanceof Component.ContentComp comp) {
            return comp.content().equals(ContainerUtil.extractContent(stack));
        }
        if (input instanceof Component.CulinaryComp comp) {
            return matchesCulinary(stack, comp.dishId());
        }
        return false;
    }

    /** 槽位堆栈能否转为容器且菜肴身份与目标标识一致。 */
    private static boolean matchesCulinary(ItemStack stack, Identifier dishId) {
        ItemStackVessel vessel = ItemStackVessel.of(stack).orElse(null);
        Culinary dish = vessel != null ? vessel.getCulinary() : null;
        return dish != null && dishId.equals(dish.getIdentifier());
    }

    @Override
    public ItemStack craft(Inventory inventory, DynamicRegistryManager registryManager) {
        ItemStack stack = inventory.getStack(0);
        int count = Math.min(stack.getCount(), MaxInputCount);

        if (output instanceof Component.ItemComp comp) {
            return comp.stack().copyWithCount(count);
        }
        if (output instanceof Component.ContentComp comp) {
            return ContainerUtil.analyze(stack)
                    .map(containerStack -> containerStack.replaceContent(comp.content()))
                    .orElse(stack);
        }
        if (output instanceof Component.CulinaryComp comp) {
            // 就地加工：对容器内菜肴应用烘烤步骤（时长来自配方）
            ItemStackVessel.of(stack).ifPresent(vessel ->
                    vessel.getCulinaryHandle().applyStep(new BakingStep(bakingTime)));
            return stack;
        }
        return ItemStack.EMPTY;
    }

    @Override
    public boolean fits(int width, int height) {
        return true;
    }

    @Override
    public ItemStack getOutput(DynamicRegistryManager registryManager) {
        // 内容物 / 菜标识没有固定的物品表示
        if (output instanceof Component.ItemComp comp) {
            return comp.stack();
        }
        return ItemStack.EMPTY;
    }

    @Override
    public Identifier getId() {
        return this.id;
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return ModRecipeSerializers.STOVE;
    }

    @Override
    public RecipeType<?> getType() {
        return ModRecipeTypes.STOVE;
    }

    /**
     * 获取烘培该配方需要的总时间，这与输入的数量有关。
     * <p>输入的数量如果超过了此配方的{@linkplain StoveRecipe#MaxInputCount}，则会按照配方的最大数量处理</p>
     *
     * @param count 烘烤的数量
     * @return 烘烤需要的总时间
     */
    public int getBakingTimeForInput(int count) {
        if (count <= 0) {
            return bakingTime;
        }
        return bakingTime * Math.min(MaxInputCount, count);
    }

    public int getBakingTime() {
        return bakingTime;
    }

    /**
     * 获取输入。
     */
    public Component getInput() {
        return input;
    }

    /**
     * 获取输出。
     */
    public Component getOutput() {
        return output;
    }
}
