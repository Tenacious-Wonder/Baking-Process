package org.bakingprocess.registry;

import com.mojang.serialization.Codec;
import net.fabricmc.fabric.api.event.registry.FabricRegistryBuilder;
import net.fabricmc.fabric.api.event.registry.RegistryAttribute;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.util.Identifier;
import org.bakingprocess.BakingProcess;
import org.bakingprocess.food.culinary.step.ProcessingStep;
import org.bakingprocess.food.culinary.step.ProcessingType;

/**
 * 加工步骤类型注册表。
 *
 * <p>持有所有 {@link ProcessingType} 的原版注册表（键名 {@code baking_process:processing_type}，
 * 会同步到客户端）。具体加工步骤类型在 {@link #registerAll()} 中登记；
 * Culinary 序列化步骤时通过该注册表在“步骤类型 id”与“步骤 Codec”之间互相转换。</p>
 *
 * @see ProcessingType
 */
public class ModProcessingTypes {
    /** 加工步骤类型的注册表。 */
    public static final Registry<ProcessingType<?>> PROCESSING_TYPES = of("processing_type");

    /**
     * 注册一种加工步骤类型。
     *
     * @param id    类型 id（不含命名空间）
     * @param codec 该步骤类型的序列化 Codec
     * @return 注册好的类型实例，供具体步骤的 {@code getType()} 返回
     */
    public static <S extends ProcessingStep> ProcessingType<S> register(String id, Codec<S> codec) {
        ProcessingType<S> type = () -> codec;
        Registry.register(PROCESSING_TYPES, new Identifier(BakingProcess.MOD_ID, id), type);
        return type;
    }

    private static <T> Registry<T> of(String id) {
        RegistryKey<Registry<T>> key = RegistryKey.ofRegistry(new Identifier(BakingProcess.MOD_ID, id));
        return FabricRegistryBuilder.createSimple(key)
                .attribute(RegistryAttribute.SYNCED)
                .buildAndRegister();
    }

    /** 注册所有加工步骤类型（当前暂无具体类型，后续在此登记）。 */
    public static void registerAll() {
        // TODO: 注册具体加工步骤类型，如切块、烤制等。
    }
}
