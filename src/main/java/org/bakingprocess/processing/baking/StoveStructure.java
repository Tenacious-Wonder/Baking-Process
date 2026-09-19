package org.bakingprocess.processing.baking;

import net.minecraft.block.BlockState;
import net.minecraft.block.pattern.BlockPattern;
import net.minecraft.block.pattern.CachedBlockPosition;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;
import org.bakingprocess.BakingProcess;
import org.bakingprocess.block.CombustionFirewoodBlock;
import org.bakingprocess.block.FirewoodBlock;
import org.bakingprocess.block.HeatResistantSlateBlock;
import org.bakingprocess.block.entity.CombustionFirewoodBlockEntity;
import org.bakingprocess.block.entity.HeatResistantSlateBlockEntity;
import org.bakingprocess.util.BakingProcessUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.twcore.api.blockvolume.BlockRange;
import org.twcore.api.blockvolume.BlockVolume;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * <h1>炉子结构</h1>
 * <p>
 * 耐热石板方块体<b>是否构成炉子</b>的判定结果与运行状态：它回答"由耐热石板组成的方块体
 * 是不是一座炉子、属于哪种炉型（1x1/1x2/2x2/2x3）、绑定了哪些柴火堆"。判定由方块实体按
 * 固定间隔驱动（见 {@link HeatResistantSlateBlockEntity#tick}），本类只关心结构本身，
 * 不涉及物品烘烤。
 * </p>
 *
 * <h2>系统构成与生命周期</h2>
 * <ul>
 *   <li><b>判定输入</b>：{@link BlockVolume}（耐热石板构成的方块体，来自方块体系统）与
 *       {@link HeatResistantSlateBlock} 上定义的炉型图案（{@code stove1x1 ~ stove2x3}）。</li>
 *   <li><b>判定流程</b>：{@link #check} 依次校验方块体完整性 → 由尺寸推导炉型 → 在主方块
 *       周围搜索匹配图案 → 成功后记录图案结果与朝向，并绑定图案中的柴火位置。</li>
 *   <li><b>主从共享</b>：同一方块体中只有主方块执行完整判定，从方块通过 {@link #copyFrom}
 *       同步主方块的结构状态，避免重复计算。</li>
 *   <li><b>柴火解析</b>：绑定的位置（{@link #firewoodPositions}）持久化到 NBT；方块实体每
 *       tick 调用 {@link #updateFirewood} 把位置解析为柴火堆方块实体引用，供烘烤读取热量。</li>
 * </ul>
 */
public class StoveStructure {
    protected static final String VALID_KEY = "IsStoveValid";
    protected static final String TYPE_KEY = "StoveStructureType";
    protected static final String FIREWOOD_POSITIONS_KEY = "FirewoodPositions";
    protected static final String RESULT_DIRECTION_KEY = "resultDirection";
    protected static final Logger LOGGER = BakingProcess.LOGGER;

    // ==================== 判定结果 ====================

    /** 匹配到的炉型图案结果（含柴火位置布局），结构无效时为 {@code null}。 */
    @Nullable
    private BlockPattern.Result patternResult;
    /** 图案朝向，决定台面物品的渲染方向。 */
    @Nullable
    private Direction resultDirection;
    /** 炉型索引（1~4），-1 表示当前不构成任何炉型。 */
    private int patternType = -1;
    /** 是否构成有效炉子结构。 */
    private boolean valid = false;

    // ==================== 柴火绑定 ====================

    /** 图案中柴火位置的世界坐标集合（持久化）。 */
    private final Set<BlockPos> firewoodPositions = new HashSet<>();
    /** 由 {@link #firewoodPositions} 解析出的柴火堆方块实体引用（运行时缓存）。 */
    private final Set<CombustionFirewoodBlockEntity> firewoodEntities = new HashSet<>();

    // ==================== 结构判定 ====================

    /**
     * 重新判定炉子结构并更新全部状态。只应由方块体的主方块调用；从方块使用 {@link #copyFrom}
     * 同步主方块结果。
     *
     * @param world  服务端世界
     * @param volume 所属方块体
     * @return 是否构成有效炉子结构
     */
    public boolean check(World world, BlockVolume volume) {
        reset();

        if (world == null || world.isClient || volume == null) {
            return false;
        }

        // 方块体范围内所有方块必须完整
        if (!volume.checkIntegrity(world)) {
            return false;
        }

        // 由方块体尺寸推导炉型；非炉子尺寸返回 -1
        int type = getPatternTypeFromVolume(volume);
        if (type == -1) {
            return false;
        }

        BlockPattern pattern = HeatResistantSlateBlock.getStovePattern(type);
        if (pattern == null) {
            return false;
        }

        // 在主方块周围搜索匹配的炉型图案
        BlockPattern.Result result = searchAround(world, volume.masterPos(), type, pattern);
        if (result == null) {
            return false;
        }

        this.patternResult = result;
        this.patternType = type;
        this.resultDirection = result.getForwards();
        this.valid = true;

        bindFirewoodFromStructure(result, type);
        return true;
    }

    /**
     * 从主方块的结构复制判定结果与柴火绑定（从方块共享主方块判定，避免重复计算）。
     */
    public void copyFrom(StoveStructure master) {
        this.patternResult = master.patternResult;
        this.resultDirection = master.resultDirection;
        this.patternType = master.patternType;
        this.valid = master.valid;
        this.firewoodPositions.clear();
        this.firewoodPositions.addAll(master.firewoodPositions);
        this.firewoodEntities.clear();
        this.firewoodEntities.addAll(master.firewoodEntities);
    }

    /**
     * 把绑定的柴火位置解析为方块实体引用。被移除（燃尽破坏）的柴火堆会在下次检查时重新获取。
     */
    public void updateFirewood(World world) {
        if (world.isClient) {
            return;
        }
        if (firewoodPositions.isEmpty()) {
            firewoodEntities.clear();
            return;
        }
        if (firewoodEntities.isEmpty() || firewoodEntities.stream().anyMatch(BlockEntity::isRemoved)) {
            firewoodEntities.clear();
            for (BlockPos pos : firewoodPositions) {
                if (world.getBlockEntity(pos) instanceof CombustionFirewoodBlockEntity combustionBE) {
                    firewoodEntities.add(combustionBE);
                }
            }
        }
    }

    /**
     * 清除全部判定结果与柴火绑定。
     */
    private void reset() {
        this.patternResult = null;
        this.patternType = -1;
        this.resultDirection = null;
        this.valid = false;
        clearFirewoodBinding();
    }

    /**
     * 由方块体尺寸推导炉型索引：水平方向且高度为 1 的 1x1/1x2/2x2/2x3 布局。
     *
     * @return 炉型索引（1~4）；不构成任何炉型时返回 -1
     */
    private int getPatternTypeFromVolume(BlockVolume volume) {
        BlockRange range = volume.range();
        if (range.height() != 1) {
            return -1;
        }
        int width = range.width();
        int depth = range.depth();
        if (width == 1 && depth == 1) {
            return 1; // 1x1
        }
        if ((width == 2 && depth == 1) || (width == 1 && depth == 2)) {
            return 2; // 1x2 / 2x1
        }
        if (width == 2 && depth == 2) {
            return 3; // 2x2
        }
        if ((width == 3 && depth == 2) || (width == 2 && depth == 3)) {
            return 4; // 2x3 / 3x2
        }
        return -1;
    }

    /**
     * 扩大搜索范围以提高匹配成功率：以主方块为中心，向四方向逐步扩大偏移。
     */
    private BlockPattern.Result searchAround(World world, BlockPos searchPos, int patternType, BlockPattern pattern) {
        for (int i = 0; i < patternType + 2; i++) {
            List<BlockPos> params = Arrays.asList(
                    searchPos.offset(Direction.EAST, i),
                    searchPos.offset(Direction.WEST, i),
                    searchPos.offset(Direction.NORTH, i),
                    searchPos.offset(Direction.SOUTH, i)
            );
            for (BlockPos pos : params) {
                BlockPattern.Result result = pattern.searchAround(world, pos);
                if (result != null) {
                    return result;
                }
            }
        }
        return null;
    }

    /**
     * 记录图案中所有柴火位置（空气或柴火堆方块）并清空实体缓存，实体引用在下次
     * {@link #updateFirewood} 时重新解析。
     */
    private void bindFirewoodFromStructure(BlockPattern.Result result, int patternType) {
        Set<BlockPos> newFirewoodPositions = BakingProcessUtils.findTargetPositionsFromPattern(result, StoveStructure::isFirewoodPositionPredicate);
        if (!newFirewoodPositions.isEmpty()) {
            this.firewoodPositions.clear();
            this.firewoodPositions.addAll(newFirewoodPositions);
            this.firewoodEntities.clear();
        } else {
            clearFirewoodBinding();
            LOGGER.warn("Failed to find firewood positions in pattern type: {}", patternType);
        }
    }

    /**
     * 检查位置是否匹配柴火位置的谓词：空气或柴火堆方块。
     */
    private static boolean isFirewoodPositionPredicate(@NotNull CachedBlockPosition cachedPos) {
        BlockState state = cachedPos.getBlockState();
        return state.isAir() ||
                state.getBlock() instanceof FirewoodBlock ||
                state.getBlock() instanceof CombustionFirewoodBlock;
    }

    private void clearFirewoodBinding() {
        this.firewoodPositions.clear();
        this.firewoodEntities.clear();
    }

    // ==================== 查询 ====================

    /**
     * 当前匹配到的炉型图案结果。
     */
    @Nullable
    public BlockPattern.Result getPatternResult() {
        return patternResult;
    }

    /**
     * 当前炉型索引（1~4）；结构无效时为 -1。
     */
    public int getPatternType() {
        return patternType;
    }

    /**
     * 是否构成有效炉子结构。
     */
    public boolean isValid() {
        return valid;
    }

    /**
     * 图案朝向，决定台面物品的渲染方向。
     */
    @Nullable
    public Direction getResultDirection() {
        return resultDirection;
    }

    /**
     * 绑定的柴火位置集合（不可修改视图）。
     */
    public Set<BlockPos> getFirewoodPositions() {
        return Collections.unmodifiableSet(firewoodPositions);
    }

    /**
     * 绑定的柴火堆方块实体集合（不可修改视图）。
     */
    public Set<CombustionFirewoodBlockEntity> getFirewoodEntities() {
        return Collections.unmodifiableSet(firewoodEntities);
    }

    /**
     * 当前正在燃烧的柴火堆数量。
     */
    public int getActiveFirewoodCount() {
        return (int) firewoodEntities.stream()
                .filter(Objects::nonNull)
                .filter(CombustionFirewoodBlockEntity::isCombusting)
                .count();
    }

    // ==================== 序列化 ====================

    /**
     * 写入结构状态（沿用方块实体顶层的 NBT 键，与旧存档兼容）。
     */
    public void writeNbt(NbtCompound nbt) {
        nbt.putBoolean(VALID_KEY, valid);
        nbt.putInt(TYPE_KEY, patternType);

        if (!firewoodPositions.isEmpty()) {
            NbtList firewoodList = new NbtList();
            for (BlockPos pos : firewoodPositions) {
                firewoodList.add(BakingProcessUtils.serializeBlockPos(pos));
            }
            nbt.put(FIREWOOD_POSITIONS_KEY, firewoodList);
        }

        if (resultDirection != null) {
            nbt.putString(RESULT_DIRECTION_KEY, resultDirection.asString());
        }
    }

    /**
     * 读取结构状态。
     */
    public void readNbt(NbtCompound nbt) {
        this.valid = nbt.getBoolean(VALID_KEY);
        this.patternType = nbt.getInt(TYPE_KEY);

        firewoodPositions.clear();
        if (nbt.contains(FIREWOOD_POSITIONS_KEY)) {
            NbtList firewoodList = nbt.getList(FIREWOOD_POSITIONS_KEY, 10);
            for (int i = 0; i < firewoodList.size(); i++) {
                BlockPos pos = BakingProcessUtils.deserializeBlockPos(firewoodList.getCompound(i));
                if (pos != null) {
                    firewoodPositions.add(pos);
                }
            }
        }

        if (nbt.contains(RESULT_DIRECTION_KEY)) {
            resultDirection = Direction.byName(nbt.getString(RESULT_DIRECTION_KEY));
        }
    }
}
