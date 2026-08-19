package org.bakingprocess.culinary;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtOps;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.world.World;
import org.bakingprocess.culinary.carrier.ServingVessel;
import org.bakingprocess.culinary.step.ProcessingStep;
import org.bakingprocess.culinary.step.ProcessingType;
import org.bakingprocess.registry.ModProcessingTypes;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * <h1>一道菜肴（Culinary）</h1>
 * <p>从原料到成品的加工历史与动态状态，随容器流转、可被完整拷贝的<b>值对象</b>。</p>
 *
 * <h2>二元数据模型</h2>
 * <ul>
 *     <li><b>步骤链</b>（{@code steps}）：只追加的加工历史，最新一步（{@link #getLatestStep()}）
 *         派生这道菜"是什么"——显示名、可食性、总口数、吃的行为；</li>
 *     <li><b>动态状态</b>（{@link CulinaryState}）：跟随这道菜的可读写事实（如已吃口数），
 *         决定"现在吃到哪了"。</li>
 * </ul>
 *
 * <h2>归属与写权限</h2>
 * <ul>
 *     <li>权威归属是持有它的容器（{@link ServingVessel}），对菜的实际读写一律经容器进行；</li>
 *     <li>加工（{@link #addStep}）不可回退；被吃过至少一口（{@link #isConsumed()}）后不允许再加工；</li>
 *     <li>交给别处用 {@link #copy()} 深拷贝，展示用 {@link #asReadOnly()} 快照（{@link CulinaryView}）。</li>
 * </ul>
 *
 * @see ServingVessel
 * @see CulinaryState
 * @see CulinaryView
 * @see ProcessingStep
 * @see ProcessingType
 */
public final class Culinary {
    /** 加工步骤列表。 */
    private static final String KEY_STEPS = "Steps";
    /** 动态状态子标签。 */
    private static final String KEY_STATE = "State";
    /** 步骤条目中的类型 id。 */
    private static final String KEY_TYPE = "Type";
    /** 步骤条目中的步骤数据。 */
    private static final String KEY_DATA = "Data";

    private final List<ProcessingStep> steps;
    private final CulinaryState state;

    private Culinary(List<ProcessingStep> steps, CulinaryState state) {
        this.steps = new ArrayList<>(steps);
        this.state = state;
    }

    /**
     * 创建一道全新的空菜肴：没有任何加工步骤，动态状态为默认。
     */
    public static Culinary create() {
        return new Culinary(new ArrayList<>(), new CulinaryState());
    }

    // ==================== 加工 ====================
    /**
     * 追加一次加工步骤，表示这道菜经历了该加工。
     *
     * @throws IllegalStateException 菜肴已被食用过（不允许再加工）
     */
    public Culinary addStep(ProcessingStep step) {
        if (state.isConsumed()) {
            throw new IllegalStateException("Culinary has already been consumed");
        }
        // 让新步骤在落链前消化既有历史（只读快照），决定自己让菜呈现的状态
        step.onAdded(asReadOnly());
        steps.add(step);
        return this;
    }

    // ==================== 食用 ====================
    /**
     * 吃下这道菜的一口。
     *
     * <p>这是吃的统一入口：先校验可食性与剩余口数，再把"这一口具体吃什么"
     * 委托给最新一步（{@link ProcessingStep#eat}），随后推进口数；当吃完最后一口时，
     * 若提供了容器 {@code vessel}，则调用其 {@link ServingVessel#clearCulinary()} 让
     * 持有者自动丢弃这道菜。</p>
     *
     * <p>注意：本方法会修改动态状态（已吃口数），因此应由<b>持有这道菜的容器</b>
     * 对其内部真实对象调用；对 {@link #copy()} 得到的拷贝调用只会改副本，不影响任何容器。</p>
     *
     * @param player 吃的玩家
     * @param world  当前世界
     * @param vessel 持有这道菜的容器；吃完最后一口时会被清空。可为 {@code null}，
     *               表示无需自动丢弃（例如由物品直接食用）
     * @return 是否成功吃下一口；不可食、无剩余口数时为 {@code false}
     */
    public boolean eat(PlayerEntity player, World world, @Nullable ServingVessel vessel) {
        ProcessingStep latest = getLatestStep();
        if (latest == null || !latest.isEdible()) {
            return false;
        }

        int totalEats = latest.getTotalEats();
        if (totalEats <= 0) {
            return false;
        }

        int eaten = state.getEatenCount();
        if (eaten >= totalEats) {
            return false;
        }

        // 本次是第几口（从 1 起），交由最新一步决定这一口的具体吃法
        int currentBite = eaten + 1;
        latest.eat(player, world, currentBite);
        state.incrementEatenCount();

        if (state.getEatenCount() >= totalEats && vessel != null) {
            vessel.clearCulinary();
        }
        return true;
    }

    // ==================== 克隆 ====================
    /**
     * 完整克隆一份菜肴：新对象与原件互不影响，动态状态一并复制。
     *
     * <p>通过 NBT 往返实现<b>深拷贝</b>：每个加工步骤都经其 {@link ProcessingType} 的
     * Codec 重新序列化并反序列化，返回全新的步骤对象，与原件不共享任何可变引用。
     * 这是 Culinary 作为值对象（随容器流转、可被安全拷贝）的语义前提。</p>
     */
    public Culinary copy() {
        NbtCompound snapshot = new NbtCompound();
        writeNbt(snapshot);
        return create().readNbt(snapshot);
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

    /**
     * 这道菜当前的显示名称：由最新一步派生；尚未加工过（空盘）时
     * 返回 {@code culinary.empty} 翻译键对应的文本。
     */
    public Text getDisplayName() {
        ProcessingStep latest = getLatestStep();
        return latest != null ? latest.getDisplayName() : Text.translatable("culinary.empty");
    }

    // ==================== 当前状态 ====================
    /**
     * 已吃口数。
     */
    public int getEatenCount() {
        return state.getEatenCount();
    }

    /**
     * 是否已被吃过至少一口（即"已食用"，此后不允许再加工）。
     */
    public boolean isConsumed() {
        return state.isConsumed();
    }

    /**
     * 这道菜当前的标识（身份），由最新一步决定；尚未加工过则返回 {@code null}。
     */
    @Nullable
    public Identifier getIdentifier() {
        ProcessingStep latest = getLatestStep();
        return latest == null ? null : latest.getIdentifier();
    }

    /**
     * 这道菜总共可以食用的口数，由最新一步决定；尚无步骤或不可食时为 0。
     */
    public int getTotalEats() {
        ProcessingStep latest = getLatestStep();
        return latest == null ? 0 : latest.getTotalEats();
    }

    /**
     * 剩余可食用口数。
     */
    public int getRemainingEats() {
        return Math.max(0, getTotalEats() - state.getEatenCount());
    }

    /**
     * 当前是否可以食用，由最新一步决定。
     */
    public boolean isEdible() {
        ProcessingStep latest = getLatestStep();
        return latest != null && latest.isEdible();
    }

    // ==================== NBT 序列化 ====================
    /**
     * 将当前状态完整写入 NBT：加工步骤列表（按各自类型 id + Codec 序列化）与动态状态。
     *
     * @return 写入后的同一份 NBT
     * @throws IllegalStateException 某一步骤的类型未注册，或步骤序列化失败
     */
    public NbtCompound writeNbt(NbtCompound nbt) {
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

        NbtCompound stateNbt = new NbtCompound();
        state.writeNbt(stateNbt);
        nbt.put(KEY_STATE, stateNbt);

        return nbt;
    }

    /**
     * 用 NBT 中的状态完整覆盖当前对象（包括动态状态）。
     *
     * @return this
     */
    public Culinary readNbt(NbtCompound nbt) {
        steps.clear();

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

        if (nbt.contains(KEY_STATE, NbtElement.COMPOUND_TYPE)) {
            state.readNbt(nbt.getCompound(KEY_STATE));
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
