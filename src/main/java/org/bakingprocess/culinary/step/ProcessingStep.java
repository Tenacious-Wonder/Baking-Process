package org.bakingprocess.culinary.step;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.world.World;
import org.bakingprocess.culinary.CulinaryView;

/**
 * 加工步骤抽象基类。
 *
 * <p>代表一道菜经历过的某一次加工（切块、腌制、烤制……）之后的状态快照。
 * 具体步骤类型继承本类，定义各自特有的属性字段，并通过 {@link #getType()} 声明
 * 自己属于哪种 {@link ProcessingType}，由该类型提供步骤的序列化 Codec。</p>
 *
 * <p>一道菜的<b>当前表现</b>（显示名、可食性、总口数、吃的行为）由最新一步决定，
 * 因此这些查询与行为方法都在本基类声明，由具体步骤实现。</p>
 *
 * @see ProcessingType
 */
public abstract class ProcessingStep {

    /** 经历这一步加工后，这道菜是否已经可以食用。 */
    public abstract boolean isEdible();

    /** 这一步加工在 GUI / 提示中的显示名称。 */
    public abstract Text getDisplayName();

    /** 本步骤所属的加工类型，用于序列化调度与类型识别。 */
    public abstract ProcessingType<?> getType();

    /** 本步骤对应的菜总共可以食用的口数；不可食用则返回 0。 */
    public abstract int getTotalEats();

    /**
     * 执行"吃下这一口"的世界副作用（饱食度、饱和度、状态效果、音效等）。
     *
     * <p>口数的校验与推进由 {@code Culinary} 统一调度，本方法只负责"这一口具体吃什么"。</p>
     *
     * @param player      吃的玩家
     * @param world       当前世界
     * @param currentBite 本次是第几口（从 1 起）；步骤可据此按比例分配食物值，
     *                    最后一口（currentBite == getTotalEats()）可补足余量
     */
    public abstract void eat(PlayerEntity player, World world, int currentBite);

    /**
     * 当本步骤被加入一道菜时调用，可读取既有历史（快照）来决定自身表现。
     *
     * <p>步骤链只追加，因此新步骤加入时可以看到之前的所有加工历史；
     * 具体步骤可在此时消化历史、推导并固化自己的表现（例如 {@code BakingStep}
     * 根据摆盘步骤推导烤熟后的食物属性）。</p>
     *
     * @param history 加入前这道菜的既有历史（只读快照），不含本步骤
     */
    public void onAdded(CulinaryView history) {

    }
}
