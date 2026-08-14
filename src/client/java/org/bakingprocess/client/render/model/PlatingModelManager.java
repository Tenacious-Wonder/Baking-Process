package org.bakingprocess.client.render.model;

import com.google.common.collect.HashBasedTable;
import com.google.common.collect.Table;
import net.minecraft.item.Item;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import org.bakingprocess.BakingProcess;
import org.jetbrains.annotations.Nullable;
import org.twcore.api.process.PlayerAction;
import org.twcore.content.Content;

import java.util.*;

/**
 * 基于 PlayerAction 编码的摆盘模型管理器
 *
 * <p><strong>设计理念：</strong></p>
 * <ul>
 *   <li><strong>编码职责分离</strong>：每个 PlayerAction 负责生成自己的编码，管理器不参与冲突解决</li>
 *   <li><strong>简洁高效</strong>：只做编码序列到模型标识符的映射，不处理空模型</li>
 *   <li><strong>单一缓存</strong>：使用序列缓存加速模型查找</li>
 *   <li><strong>路径规范</strong>：统一使用 "process/plating/" 作为路径前缀</li>
 * </ul>
 *
 * <p><strong>编码规范：</strong></p>
 * <ol>
 *   <li>每个 PlayerAction 必须实现 getCode() 方法</li>
 *   <li>编码长度不超过 8 个字符</li>
 *   <li>编码应稳定：相同的操作总是返回相同的编码</li>
 *   <li>编码应包含足够的信息以区分不同操作类型和参数</li>
 * </ol>
 *
 * <p><strong>模型路径生成规则：</strong></p>
 * <pre>
 * assets/baking_process/models/process/plating/[容器编码]/[编码分组]/[最后4个编码]
 *
 * 示例：assets/baking_process/models/process/plating/ir_pla/ApBcDe_Fg/ApBcDe_Fg
 * 其中：ir_pla 是铁盘容器编码，ApBcDe_Fg 是编码分组
 * </pre>
 */
public class PlatingModelManager {
    private static final PlatingModelManager INSTANCE = new PlatingModelManager();

    /**
     * 操作序列缓存：SequenceKey → Identifier
     *
     * <p>缓存完整的操作序列到模型标识符的映射，避免重复计算。</p>
     */
    private final Map<SequenceKey, Identifier> sequenceCache = new HashMap<>();

    /**
     * 配方模型缓存：Table<Item, String, Identifier>
     *
     * <p>缓存已知配方到菜肴模型的映射，用于快速识别完整配方。</p>
     */
    private final Table<Item, String, Identifier> recipeModelCache = HashBasedTable.create();

    // 路径生成配置
    private static final int GROUP_SIZE = 4;

    private PlatingModelManager() {}

    /**
     * 获取 PlatingModelManager 单例实例
     */
    public static PlatingModelManager getInstance() {
        return INSTANCE;
    }

    // ==================== 核心公共接口 ====================

    /**
     * 根据容器类型和操作序列获取对应的模型标识符
     *
     * <p><strong>查找顺序：</strong></p>
     * <ol>
     *   <li>检查缓存中的操作序列</li>
     *   <li>检查配方模型映射</li>
     *   <li>生成动态模型路径</li>
     * </ol>
     *
     * @param container 容器物品类型（如铁盘、木盘）
     * @param actions 已执行的操作序列，按执行顺序排列
     * @return 对应的模型标识符，如果无法获取则返回 null
     * @throws IllegalArgumentException 如果容器为 null
     */
    @Nullable
    public Identifier getModelForActions(Item container, List<PlayerAction> actions) {
        // 参数验证
        Objects.requireNonNull(container, "Containers cannot be null");

        // 空序列处理：直接返回 null
        if (actions == null || actions.isEmpty()) {
            return null;
        }

        // 生成编码序列和缓存键
        List<String> codeSequence = generateCodeSequence(actions);
        SequenceKey key = new SequenceKey(container, actions);

        // 尝试匹配完整配方（如果有完全匹配的配方，使用菜肴模型）
        Identifier recipeModel = tryMatchRecipeModel(container, codeSequence);
        if (recipeModel != null) {
            sequenceCache.put(key, recipeModel);
            return recipeModel;
        }

        // 检查操作序列缓存
        Identifier cachedModel = sequenceCache.get(key);
        if (cachedModel != null) {
            return cachedModel;
        }

        // 生成步骤模型路径
        Identifier modelPath = generateModelPath(container, codeSequence);
        sequenceCache.put(key, modelPath);

        return modelPath;
    }

    /**
     * 注册配方模型映射
     *
     * <p>用于将特定的操作序列直接映射到菜肴模型。当操作序列完全匹配时，
     * 将使用菜肴模型而不是逐步构建的步骤模型。</p>
     *
     * @param container 容器物品类型
     * @param recipeActions 配方的完整操作序列
     * @param dish 配方对应的菜肴内容
     */
    public void registerRecipeModel(Item container, List<PlayerAction> recipeActions, Content dish) {
        // 生成配方操作的编码序列哈希（作为唯一标识）
        String recipeHash = generateRecipeHash(recipeActions);
        Identifier dishId = ModModelId.createDishesModelId(container, dish);

        // 注册到配方模型缓存
        recipeModelCache.put(container, recipeHash, dishId);
    }

    /**
     * 检查操作序列是否对应一个菜肴
     *
     * <p>用于判断是否应该为这个序列生成步骤模型。</p>
     *
     * @param container 容器物品类型
     * @param actions 操作序列
     * @return 如果序列对应一个菜肴则返回 true
     */
    public boolean isSequenceForDish(Item container, List<PlayerAction> actions) {
        if (actions == null || actions.isEmpty()) {
            return false;
        }

        List<String> codeSequence = generateCodeSequence(actions);
        String hash = generateSequenceHash(codeSequence);
        return recipeModelCache.contains(container, hash);
    }

    /**
     * 为特定操作序列的所有前缀生成模型标识符列表，但跳过对应菜肴的序列
     *
     * <p>这个方法用于在游戏启动时预加载所有可能用到的模型。
     * 它会为操作序列的每个前缀生成对应的模型路径，但如果某个前缀对应菜肴，
     * 则跳过该前缀的步骤模型生成。</p>
     *
     * <p>示例：对于序列 [A, B, C]，其中 [A, B, C] 对应菜肴，会生成：
     * 1. 前缀 [A] 的模型
     * 2. 前缀 [A, B] 的模型
     * 3. 前缀 [A, B, C] 跳过（因为对应菜肴）</p>
     *
     * @param container 容器物品类型
     * @param actionSequence 完整的操作序列
     * @return 所有前缀对应的模型标识符列表（跳过菜肴对应的序列）
     */
    public List<Identifier> generateAllPrefixModels(Item container, List<PlayerAction> actionSequence) {
        List<Identifier> models = new ArrayList<>();

        // 为序列的每个前缀生成模型，但如果前缀对应菜肴则跳过
        for (int i = 1; i <= actionSequence.size(); i++) {
            List<PlayerAction> prefix = actionSequence.subList(0, i);

            // 检查这个前缀是否对应一个菜肴
            if (isSequenceForDish(container, prefix)) {
                // 如果对应菜肴，跳过步骤模型生成
                continue;
            }

            Identifier modelId = getModelForActions(container, prefix);
            if (modelId != null) {
                models.add(modelId);
            }
        }

        return models;
    }

    /**
     * 清空所有缓存
     */
    public void clearAllCaches() {
        sequenceCache.clear();
        recipeModelCache.clear();
    }

    // ==================== 内部核心方法 ====================

    /**
     * 生成操作序列的编码序列
     */
    private List<String> generateCodeSequence(List<PlayerAction> actions) {
        List<String> codes = new ArrayList<>(actions.size());

        for (PlayerAction action : actions) {
            String code = action.getCode();
            codes.add(code);
        }

        return codes;
    }

    /**
     * 尝试匹配配方模型
     */
    @Nullable
    private Identifier tryMatchRecipeModel(Item container, List<String> codeSequence) {
        // 生成当前序列的哈希
        String currentHash = generateSequenceHash(codeSequence);
        return recipeModelCache.get(container, currentHash);
    }

    /**
     * 生成模型路径标识符
     */
    private Identifier generateModelPath(Item container, List<String> codeSequence) {
        // 获取容器编码
        String containerCode = getContainerCode(container);

        // 构建基础路径
        StringBuilder pathBuilder = new StringBuilder("process/plating/");
        pathBuilder.append(containerCode).append("/");

        // 处理编码分组
        int groupCount = (codeSequence.size() + GROUP_SIZE - 1) / GROUP_SIZE; // 向上取整

        for (int groupIndex = 0; groupIndex < groupCount; groupIndex++) {
            int start = groupIndex * GROUP_SIZE;
            int end = Math.min(start + GROUP_SIZE, codeSequence.size());

            // 构建本组编码字符串
            StringBuilder groupBuilder = new StringBuilder();
            for (int i = start; i < end; i++) {
                if (i > start) {
                    groupBuilder.append("_");
                }
                groupBuilder.append(codeSequence.get(i));
            }

            String groupCode = groupBuilder.toString();

            if (groupIndex < groupCount - 1) {
                // 中间组：作为目录
                pathBuilder.append(groupCode).append("/");
            } else {
                // 最后一组：作为文件名（不带扩展名）
                pathBuilder.append(groupCode);
            }
        }

        // 生成完整的模型标识符
        String path = pathBuilder.toString();
        return new Identifier(BakingProcess.MOD_ID, path);
    }

    /**
     * 获取容器编码
     */
    private String getContainerCode(Item container) {
        Identifier containerId = Registries.ITEM.getId(container);

        StringBuilder containerCode = new StringBuilder();

        // 命名空间前2位
        String namespace = containerId.getNamespace();
        if (namespace.length() >= 2) {
            containerCode.append(namespace, 0, 2);
        } else {
            containerCode.append(namespace);
        }

        containerCode.append("_");

        // 物品路径前3位
        String path = containerId.getPath();
        if (path.length() >= 3) {
            containerCode.append(path, 0, 3);
        } else {
            containerCode.append(path);
        }

        return containerCode.toString();
    }

    /**
     * 生成操作序列的哈希值
     */
    private String generateRecipeHash(List<PlayerAction> actions) {
        List<String> codes = generateCodeSequence(actions);
        return generateSequenceHash(codes);
    }

    /**
     * 生成编码序列的哈希值
     */
    private String generateSequenceHash(List<String> codeSequence) {
        // 将编码序列连接成一个字符串
        StringBuilder builder = new StringBuilder();
        for (String code : codeSequence) {
            builder.append(code).append("|");
        }

        // 计算哈希值并转换为8位十六进制
        int hash = builder.toString().hashCode();
        return String.format("%08x", Math.abs(hash));
    }

    /**
     * 操作序列缓存键
     */
    private static class SequenceKey {
        private final Item container;
        private final List<PlayerAction> actions;
        private final int hashCode;

        SequenceKey(Item container, List<PlayerAction> actions) {
            this.container = container;
            this.actions = new ArrayList<>(actions); // 防御性复制
            this.hashCode = Objects.hash(container, actions);
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) return true;
            if (obj == null || getClass() != obj.getClass()) return false;

            SequenceKey other = (SequenceKey) obj;

            // 快速比较：先比较哈希值
            if (this.hashCode != other.hashCode) {
                return false;
            }

            // 详细比较
            if (!Objects.equals(container, other.container)) {
                return false;
            }

            if (actions.size() != other.actions.size()) {
                return false;
            }

            for (int i = 0; i < actions.size(); i++) {
                if (!actions.get(i).equals(other.actions.get(i))) {
                    return false;
                }
            }

            return true;
        }

        @Override
        public int hashCode() {
            return hashCode;
        }

        @Override
        public String toString() {
            return String.format("SequenceKey{container=%s, actions=%d}",
                    Registries.ITEM.getId(container), actions.size());
        }
    }
}