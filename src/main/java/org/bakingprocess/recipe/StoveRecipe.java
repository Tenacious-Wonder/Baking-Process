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
import org.bakingprocess.culinary.carrier.ServingVessel;
import org.bakingprocess.culinary.step.BakingStep;
import org.bakingprocess.registry.ModRecipeSerializers;
import org.bakingprocess.registry.ModRecipeTypes;
import org.twcore.api.content.ContainerUtil;
import org.twcore.content.Content;

import java.util.Optional;

/**
 * <h1>烤炉配方</h1>
 * <p>输入 / 输出可为物品堆栈、内容物或菜标识（culinary）。</p>
 *
 * <h2>culinary 适配</h2>
 * <ul>
 *     <li><b>输入</b>为菜标识：匹配对象是<b>菜肴容器</b>（{@link ServingVessel}）——
 *         世界中的容器（如盘子方块实体）直接匹配，物品容器（{@link ItemStackVessel}）
 *         由物品入口包装后走同一套语义；容器内菜肴身份与标识一致即匹配；</li>
 *     <li><b>输出</b>为菜标识：烤制完成生成 {@link BakingStep} 应用到容器内菜肴
 *         （时长来自配方，名称与口数由 {@code BakingStep} 从摆盘步骤推导）。</li>
 * </ul>
 *
 * <h2>权威认证</h2>
 * <p>设备（如烤架）面对容器内菜肴时，先经 {@link #getFirstDishMatch} 查询认证配方
 * （决定"能否烤、烤多久"）；认证通过、进度满后由 {@link #applyCulinaryOutput}
 * 产生并应用烘烤步骤——烘烤步骤只能由配方产生。</p>
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
        // 菜输入：把槽位物品当作菜肴容器匹配（与盘子方块实体同一套语义）
        return ItemStackVessel.of(stack).map(this::matchesDish).orElse(false);
    }

    /**
     * 菜肴输入是否匹配当前容器内的菜：仅当输入为菜标识（CulinaryComp）且与容器菜肴的
     * 当前标识一致时匹配。面向世界中的菜肴容器（如盘子方块实体）与物品容器通用。
     */
    public boolean matchesDish(ServingVessel vessel) {
        if (!(input instanceof Component.CulinaryComp comp)) {
            return false;
        }
        Culinary dish = vessel.getCulinary();
        return dish != null && comp.dishId().equals(dish.getIdentifier());
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
        if (output instanceof Component.CulinaryComp) {
            // 就地加工：对容器内菜肴应用烘烤步骤（时长来自配方）
            ItemStackVessel.of(stack).ifPresent(this::applyCulinaryOutput);
            return stack;
        }
        return ItemStack.EMPTY;
    }

    /**
     * 把菜肴输出（烘烤步骤）应用到容器：构造时长来自配方的 {@link BakingStep} 并交由
     * 容器句柄执行；容器不认可（无菜、摆盘流程活动、菜肴已食用）时返回 {@code false}。
     */
    public boolean applyCulinaryOutput(ServingVessel vessel) {
        if (!(output instanceof Component.CulinaryComp)) {
            return false;
        }
        return vessel.getCulinaryHandle().applyStep(new BakingStep(bakingTime));
    }

    /**
     * 查找第一个认证当前容器内菜肴的炉子配方（菜肴输入的配方）。
     *
     * <p>这是"容器内菜能否被烤、烤多久"的权威查询：设备先经本方法认证，
     * 认证通过后才可应用 {@link #applyCulinaryOutput} 产生烘烤步骤。</p>
     *
     * @return 匹配的配方；没有任何炉子配方认证这道菜时为空
     */
    public static Optional<StoveRecipe> getFirstDishMatch(ServingVessel vessel, World world) {
        return world.getRecipeManager().listAllOfType(ModRecipeTypes.STOVE).stream()
                .filter(recipe -> recipe.matchesDish(vessel))
                .findFirst();
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
