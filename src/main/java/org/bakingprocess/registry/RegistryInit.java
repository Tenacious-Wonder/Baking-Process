package org.bakingprocess.registry;

public class RegistryInit {
    public static void init() {
        ModBlocks.registerAll();
        ModItems.registerAll();
        ModContents.registerAll();
        ModContainers.registerAll();
        ModBlockEntityTypes.registerAll();
        ModEntityTypes.registerAll();
        ModRecipeTypes.registerAll();
        ModItemGroups.registerAll();
        ModSounds.registerAll();
        ModProcessingTypes.registerAll();
        ModBiomeFeatures.registerAll();
    }
}
