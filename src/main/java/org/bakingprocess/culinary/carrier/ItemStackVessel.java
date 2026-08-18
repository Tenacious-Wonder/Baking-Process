package org.bakingprocess.culinary.carrier;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.util.Identifier;
import net.minecraft.world.World;
import org.bakingprocess.BakingProcess;
import org.bakingprocess.culinary.Culinary;
import org.bakingprocess.culinary.CulinaryHandle;
import org.bakingprocess.culinary.step.ProcessingStep;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;

/**
 * <h1>物品堆栈容器</h1>
 * <p>把携带菜数据的物品堆栈包装为 {@link ServingVessel} 视图，让无法实现接口的
 * {@link ItemStack} 也能参与容器语义（如烤炉对盘子物品的加工）。</p>
 *
 * <h2>识别与数据布局</h2>
 * <ul>
 *     <li>堆栈 NBT 需含统一复合对象（键 {@link #VESSEL_NBT_KEY}），其内固定字段
 *         {@link #VESSEL_NAME_KEY}（{@code name}）对应 {@link #getContainerId()}；
 *         复合对象中其余数据由物品类自行定义；</li>
 *     <li>菜数据存于独立键 {@link ServingVessel#CULINARY_NBT_KEY}，
 *         与方块实体视图读写同一份数据。</li>
 * </ul>
 *
 * <h2>映射语义</h2>
 * <ul>
 *     <li>每次操作都从当前堆栈 NBT 重新读取、操作后写回，不缓存中间数据：
 *         即使堆栈实例被替换，用当前堆栈重建视图即可，数据不丢。</li>
 * </ul>
 */
public final class ItemStackVessel implements ServingVessel {
    /** 堆栈 NBT 中容器身份复合对象的键（mod 命名空间，避免与其他数据冲突）。 */
    public static final String VESSEL_NBT_KEY = BakingProcess.MOD_ID + ":vessel";
    /** 容器身份复合对象中标识字段（{@code name}），对应 {@link #getContainerId()}。 */
    public static final String VESSEL_NAME_KEY = "name";

    private final ItemStack stack;

    /** 对当前堆栈菜数据的操作句柄（每次操作映射到 NBT）。 */
    private final CulinaryHandle handle = new CulinaryHandle() {
        @Override
        public int getEatenCount() {
            Culinary dish = readCulinary();
            return dish != null ? dish.getEatenCount() : 0;
        }

        @Override
        public boolean isConsumed() {
            Culinary dish = readCulinary();
            return dish != null && dish.isConsumed();
        }

        @Override
        public int getRemainingEats() {
            Culinary dish = readCulinary();
            return dish != null ? dish.getRemainingEats() : 0;
        }

        @Override
        public boolean isEdible() {
            Culinary dish = readCulinary();
            return dish != null && dish.isEdible();
        }

        @Override
        public int getTotalEats() {
            Culinary dish = readCulinary();
            return dish != null ? dish.getTotalEats() : 0;
        }

        @Override
        public boolean applyStep(ProcessingStep step) {
            Culinary dish = readCulinary();
            if (dish == null) {
                return false;
            }
            dish.addStep(step);
            writeCulinary(dish);
            return true;
        }

        @Override
        public boolean eat(PlayerEntity player, World world) {
            Culinary dish = readCulinary();
            if (dish == null) {
                return false;
            }
            boolean eaten = dish.eat(player, world, ItemStackVessel.this);
            // 吃完时 clearCulinary 已移除菜数据；未吃完则写回剩余进度
            if (eaten && dish.getRemainingEats() > 0) {
                writeCulinary(dish);
            }
            return eaten;
        }
    };

    private ItemStackVessel(ItemStack stack) {
        this.stack = stack;
    }

    /**
     * 若堆栈是合理容器（NBT 含 vessel 复合对象），返回其容器视图；否则为空。
     */
    public static Optional<ItemStackVessel> of(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return Optional.empty();
        }
        NbtCompound root = stack.getNbt();
        if (root == null || !root.contains(VESSEL_NBT_KEY, NbtElement.COMPOUND_TYPE)) {
            return Optional.empty();
        }
        return Optional.of(new ItemStackVessel(stack));
    }

    // ==================== ServingVessel 实现 ====================

    @Override
    public Identifier getContainerId() {
        return Identifier.tryParse(stack.getNbt().getCompound(VESSEL_NBT_KEY).getString(VESSEL_NAME_KEY));
    }

    @Override
    @Nullable
    public Culinary getCulinary() {
        return readCulinary();
    }

    @Override
    public boolean tryAddCulinary(Culinary culinary) {
        writeCulinary(culinary);
        return true;
    }

    @Override
    public void clearCulinary() {
        NbtCompound root = stack.getNbt();
        if (root != null) {
            root.remove(CULINARY_NBT_KEY);
        }
    }

    @Override
    public CulinaryHandle getCulinaryHandle() {
        return handle;
    }

    // ==================== NBT 读写 ====================

    /** 从堆栈 NBT 读取菜数据；无菜时返回 {@code null}。 */
    @Nullable
    private Culinary readCulinary() {
        NbtCompound root = stack.getNbt();
        if (root == null || !root.contains(CULINARY_NBT_KEY, NbtElement.COMPOUND_TYPE)) {
            return null;
        }
        return Culinary.create().readNbt(root.getCompound(CULINARY_NBT_KEY));
    }

    /** 把菜数据写入堆栈 NBT。 */
    private void writeCulinary(Culinary dish) {
        NbtCompound root = stack.getOrCreateNbt();
        NbtCompound culinaryNbt = new NbtCompound();
        dish.writeNbt(culinaryNbt);
        root.put(CULINARY_NBT_KEY, culinaryNbt);
    }
}
