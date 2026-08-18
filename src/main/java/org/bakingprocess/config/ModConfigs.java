package org.bakingprocess.config;

import org.twcore.api.config.ConfigType;
import org.twcore.api.config.TwConfig;

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
