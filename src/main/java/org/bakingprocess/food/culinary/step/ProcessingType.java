package org.bakingprocess.food.culinary.step;

import com.mojang.serialization.Codec;

/**
 * 加工步骤类型注册项。
 *
 * <p>每种具体加工步骤（如切块、烤制）都对应一个
 * 注册进 {@code org.bakingprocess.registry.ModProcessingTypes} 的 ProcessingType，
 * 负责提供该步骤类型的序列化 Codec。Culinary 读写 NBT 时通过步骤的
 * {@link ProcessingStep#getType()} 找到本类型，完成“类型 id ↔ 步骤数据”的调度序列化。</p>
 *
 * @param <T> 该类型对应的具体步骤类
 */
@FunctionalInterface
public interface ProcessingType<T extends ProcessingStep> {

    /** 该步骤类型的序列化 Codec。 */
    Codec<T> codec();
}
