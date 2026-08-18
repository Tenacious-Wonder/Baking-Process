package org.bakingprocess.registry;

import com.mojang.serialization.Codec;
import net.fabricmc.fabric.api.event.registry.FabricRegistryBuilder;
import net.fabricmc.fabric.api.event.registry.RegistryAttribute;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.util.Identifier;
import org.bakingprocess.BakingProcess;
import org.bakingprocess.culinary.step.BakingStep;
import org.bakingprocess.culinary.step.PlatingStep;
import org.bakingprocess.culinary.step.ProcessingStep;
import org.bakingprocess.culinary.step.ProcessingType;

/**
 * 加工步骤类型注册表。
 *
 * @see ProcessingType
 */
public class ModProcessingTypes {
    /** 加工步骤类型的注册表。 */
    public static final Registry<ProcessingType<?>> PROCESSING_TYPES = ofRegistry();

    public static final ProcessingType<PlatingStep> PLATING = register("plating", PlatingStep.CODEC);
    public static final ProcessingType<BakingStep> BAKING = register("baking", BakingStep.CODEC);

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

    private static <T> Registry<T> ofRegistry() {
        RegistryKey<Registry<T>> key = RegistryKey.ofRegistry(new Identifier(BakingProcess.MOD_ID, "processing_type"));
        return FabricRegistryBuilder.createSimple(key)
                .attribute(RegistryAttribute.SYNCED)
                .buildAndRegister();
    }

    public static void registerAll() {}
}
