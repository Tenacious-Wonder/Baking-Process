package org.bakingprocess.food.culinary;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtOps;
import net.minecraft.util.Identifier;
import org.bakingprocess.food.culinary.carrier.ServingVessel;
import org.bakingprocess.food.culinary.step.ProcessingStep;
import org.bakingprocess.food.culinary.step.ProcessingType;
import org.bakingprocess.registry.ModProcessingTypes;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * <h1>一道菜肴（Culinary）的数据容器。</h1>
 * <p>
 * Culinary 是整个烹饪系统的核心数据对象：它承载一道菜从原料到成品的完整加工历史，
 * 是加工、转移、展示与存档共用的<b>唯一真实对象</b>。
 * </p>
 *
 * <h2>对象身份与生命周期</h2>
 * <ul>
 *     <li>一道菜从被制作出来起就是同一个对象：加工方（砧板、烤箱等）通过
 *         {@link #addStep(ProcessingStep)} 对<b>同一个对象</b>原地追加加工步骤；</li>
 *     <li>载体（盘子、碗等 {@code ServingVessel}）持有该对象直到被转移或丢弃，
 *         转移时由目标容器接住同一对象，原容器通过 {@code clearCulinary()} 抛弃引用；</li>
 *     <li>状态可随时通过 {@link #writeNbt} / {@link #readNbt} 在 NBT 中完整保存与恢复
 *         （加工步骤列表 + 锁定标记）。</li>
 * </ul>
 *
 * <h2>锁定与展示</h2>
 * <ul>
 *     <li>{@link #lock()} 表示这道菜已经定型（例如已盛好上桌），定型后不允许再追加
 *         任何加工步骤，且该状态不可逆；</li>
 *     <li>需要把菜肴展示给 GUI 时，必须使用 {@link #asReadOnly()} 的只读快照
 *         （{@link CulinaryView}），<b>不要将真实菜肴对象交给渲染代码</b>。</li>
 * </ul>
 *
 * <h2>步骤类型体系</h2>
 * <p>
 * 仿照原版 Structure / StructureType：{@link ProcessingStep} 是步骤抽象基类，
 * 每种具体步骤通过 {@link ProcessingStep#getType()} 找到自己的 {@link ProcessingType}，
 * 由该类型提供序列化 Codec。所有 ProcessingType 注册于
 * {@link org.bakingprocess.registry.ModProcessingTypes}，Culinary 序列化步骤时
 * 通过注册表在“类型 id”和“步骤 Codec”之间互相转换。
 * </p>
 *
 * @see CulinaryView
 * @see ProcessingStep
 * @see ProcessingType
 * @see ServingVessel
 * @see org.bakingprocess.registry.ModProcessingTypes
 */
public final class Culinary {
    /** 加工步骤列表。 */
    private static final String KEY_STEPS = "Steps";
    /** 锁定标记。 */
    private static final String KEY_LOCKED = "Locked";
    /** 步骤条目中的类型 id。 */
    private static final String KEY_TYPE = "Type";
    /** 步骤条目中的步骤数据。 */
    private static final String KEY_DATA = "Data";

    private final List<ProcessingStep> steps;
    private boolean locked;

    private Culinary(List<ProcessingStep> steps, boolean locked) {
        this.steps = new ArrayList<>(steps);
        this.locked = locked;
    }

    /**
     * 创建一道全新的空菜肴：没有任何加工步骤，未锁定。
     */
    public static Culinary create() {
        return new Culinary(new ArrayList<>(), false);
    }

    // ==================== 加工与定型 ====================
    /**
     * 追加一次加工步骤，表示这道菜经历了该加工。
     *
     * @throws IllegalStateException 菜肴已锁定（定型后不允许再加工）
     */
    public Culinary addStep(ProcessingStep step) {
        if (locked) {
            throw new IllegalStateException("Culinary is locked");
        }
        steps.add(step);
        return this;
    }

    /**
     * 将菜肴定型，之后不能再追加任何加工步骤。该操作幂等且不可逆。
     */
    public Culinary lock() {
        locked = true;
        return this;
    }

    /**
     * 当前菜肴是否已定型（锁定）。
     */
    public boolean isLocked() {
        return locked;
    }

    // ==================== 克隆 ====================
    /**
     * 完整克隆一份菜肴：新对象与原件互不影响，锁定状态一并复制。
     */
    public Culinary copy() {
        return new Culinary(steps, locked);
    }

    // ==================== 只读快照 ====================
    /**
     * 生成一份用于展示的只读快照（{@link CulinaryView}）。
     *
     * <p>快照在生成时拷贝当前状态，之后对真实菜肴的修改不会反映到快照上；
     * 快照不参与任何实际游戏逻辑，仅供 GUI 渲染等展示用途。</p>
     */
    public CulinaryView asReadOnly() {
        return new CulinaryView(steps);
    }

    // ==================== 查询 ====================
    /**
     * 当前加工步骤列表的不可修改视图。
     */
    public List<ProcessingStep> getSteps() {
        return Collections.unmodifiableList(steps);
    }

    /**
     * 最新一步加工；尚未加工过则返回 {@code null}。
     */
    @Nullable
    public ProcessingStep getLatestStep() {
        if (steps.isEmpty()) {
            return null;
        }
        return steps.get(steps.size() - 1);
    }

    // ==================== 当前状态（预留板块） ====================
    // 此处放置“此刻这道菜是什么状态”的方法，例如当前显示名称、
    // 此刻应当如何被食用等。这些方法的行为由最新追加的加工步骤
    // （getLatestStep()）决定，每追加一步加工，返回结果实时变化。
    // TODO: 后续按具体步骤类型补充实现。

    // ==================== NBT 序列化 ====================
    /**
     * 将当前状态完整写入 NBT：加工步骤列表（按各自类型 id + Codec 序列化）与锁定标记。
     *
     * @return 写入后的同一份 NBT
     * @throws IllegalStateException 某一步骤的类型未注册，或步骤序列化失败
     */
    public NbtCompound writeNbt(NbtCompound nbt) {
        nbt.putBoolean(KEY_LOCKED, locked);
        NbtList stepList = new NbtList();

        for (ProcessingStep step : steps) {
            ProcessingType<?> type = step.getType();
            Identifier typeId = ModProcessingTypes.PROCESSING_TYPES.getId(type);
            if (typeId == null) {
                throw new IllegalStateException("Unregistered step type: " + type);
            }

            NbtCompound entry = new NbtCompound();
            entry.putString(KEY_TYPE, typeId.toString());

            NbtElement data = unwrap(encodeStep(type, step), "Failed to encode step " + step);
            entry.put(KEY_DATA, data);
            stepList.add(entry);
        }

        nbt.put(KEY_STEPS, stepList);
        return nbt;
    }

    /**
     * 用 NBT 中的状态完整覆盖当前对象（包括锁定标记）。
     *
     * <p>这是反序列化路径：即使当前对象已锁定也可以调用，覆盖后以 NBT 中的状态为准。</p>
     *
     * @return this
     */
    public Culinary readNbt(NbtCompound nbt) {
        steps.clear();
        locked = nbt.getBoolean(KEY_LOCKED);

        NbtList stepList = nbt.getList(KEY_STEPS, NbtElement.COMPOUND_TYPE);
        for (NbtElement element : stepList) {
            NbtCompound entry = (NbtCompound) element;
            Identifier typeId = Identifier.tryParse(entry.getString(KEY_TYPE));
            if (typeId == null) {
                throw new IllegalStateException("Invalid step type id: " + entry.getString(KEY_TYPE));
            }

            ProcessingType<?> type = ModProcessingTypes.PROCESSING_TYPES.get(typeId);
            if (type == null) {
                throw new IllegalStateException("Unknown step type: " + typeId);
            }

            steps.add(decodeStep(type, entry.getCompound(KEY_DATA)));
        }

        return this;
    }

    @SuppressWarnings("unchecked")
    private static DataResult<NbtElement> encodeStep(ProcessingType<?> type, ProcessingStep step) {
        return ((Codec<ProcessingStep>) type.codec()).encodeStart(NbtOps.INSTANCE, step);
    }

    private static <S extends ProcessingStep> S decodeStep(ProcessingType<S> type, NbtCompound data) {
        return unwrap(type.codec().parse(NbtOps.INSTANCE, data), "Failed to decode step");
    }

    private static <T> T unwrap(DataResult<T> result, String description) {
        return result.getOrThrow(false, error -> {
            throw new IllegalStateException(description + ": " + error);
        });
    }
}
