package org.bakingprocess.config;

import org.twcore.api.config.ConfigType;
import org.twcore.api.config.TwConfig;

/**
 * 配置注册入口：静态 {@link ConfigType} 字段 + {@link #registerAll} 唤醒。
 *
 * <p>须在 {@code TwModManager.register} 之后调用（已接在 {@code BakingProcess.registerCore}）。</p>
 */
public final class ModConfigs {
    public static final ConfigType<IngredientTableData> FOOD_INGREDIENTS = ConfigType.of(
            "food_ingredients",
            IngredientTableData.CODEC,
            IngredientTableData::withInfluencers,
            null
    );

    public static void registerAll(TwConfig config) {
        config.registerConfig(FOOD_INGREDIENTS);
    }
}
