package org.bakingprocess;

import net.fabricmc.api.ModInitializer;
import org.bakingprocess.config.ModConfigs;
import org.bakingprocess.registry.*;
import org.bakingprocess.integration.dfood.DFoodInit;
import org.bakingprocess.util.BakingProcessUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.twcore.api.TwModManager;
import org.twcore.api.blockvolume.BlockVolumeRegistry;
import org.twcore.api.config.TwConfig;
import org.twcore.api.event.TwCoreRegisterEvent;
import org.twcore.api.sound.Item2BlockSounds;
import org.twcore.container.AbstractMappedContainer;
import org.twcore.process.playeraction.impl.AddItemPlayerAction;
import org.twcore.registry.ContainerTypes;
import org.twcore.registry.Contents;

public class BakingProcess implements ModInitializer {
    public static final String MOD_ID = "baking_process";
    public static final Logger LOGGER = LoggerFactory.getLogger("TW's Baking Process");

    @Override
    public void onInitialize() {
        DFoodInit.init();
        RegistryInit.init();
        TwCoreRegisterEvent.TW_CORE_REGISTRAR.register(BakingProcess::registerCore);

        LOGGER.info("TW's Baking Process is initializing!");
    }

    public static void registerCore() {
        TwModManager.IMPL.register(BakingProcess.MOD_ID, 2);
        ModConfigs.registerAll(TwConfig.forMod(BakingProcess.MOD_ID));

        AddItemPlayerAction.REMAPPING.put(ModItems.SALMON_CUBES, "msa");
        AddItemPlayerAction.REMAPPING.put(ModItems.SALT_CUBES, "sac");
        AddItemPlayerAction.REMAPPING.put(ModItems.CARROT_HEAD, "cad");

        ((AbstractMappedContainer) ContainerTypes.POTION).registerContentMapping(Contents.MILK, ModItems.MILK_POTION);

        Item2BlockSounds.registerParser(BakingProcessUtils::getSoundGroupFromItem);

        BlockVolumeRegistry.register(ModBlocks.HEAT_RESISTANT_SLATE);
    }
}
