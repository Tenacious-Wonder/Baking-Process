package org.bakingprocess.registry;

import net.minecraft.item.Items;
import net.minecraft.registry.Registry;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Identifier;
import org.bakingprocess.BakingProcess;
import org.bakingprocess.container.BreadBoatContainer;
import org.bakingprocess.container.MoldContainer;
import org.twcore.container.BowlContainer;
import org.twcore.container.BucketContainer;
import org.twcore.container.ContainerType;
import org.twcore.container.PotionContainer;
import org.twcore.registry.TWRegistries;

public class ModContainers {
    // 碗
    public static final ContainerType BOWL = registerContainerType("bowl", new BowlContainer(
            new ContainerType.ContainerSettings(Items.BOWL)
                    .setUseSound(ModSounds.SOUP_FILL)));
    // 硬面包船
    public static final ContainerType HARD_BREAD_BOAT = registerContainerType("hard_bread_boat", new BreadBoatContainer(
            new ContainerType.ContainerSettings(ModItems.HARD_BREAD_BOAT)
                    .setUseSound(ModSounds.SOUP_FILL)
    ));
    // 瓶子
    public static final ContainerType POTION = registerContainerType("potion", new PotionContainer(
            new ContainerType.ContainerSettings(Items.GLASS_BOTTLE)
    ));
    // 桶
    public static final ContainerType BUCKET = registerContainerType("bucket", new BucketContainer(
            new ContainerType.ContainerSettings(Items.BUCKET)
                    .setBaseCapacity(3)
                    .setUseSound(SoundEvents.ITEM_BUCKET_EMPTY)
    ));
    // 模具
    public static final ContainerType TOAST_EMBRYO_MOLD = registerContainerType("toast_embryo_mold", new MoldContainer(
            new ContainerType.ContainerSettings(ModItems.TOAST_EMBRYO_MOLD)
                    .setUseSound(SoundEvents.ENTITY_SLIME_DEATH_SMALL)
    ));
    public static final ContainerType CAKE_EMBRYO_MOLD = registerContainerType("cake_embryo_mold", new MoldContainer(
            new ContainerType.ContainerSettings(ModItems.CAKE_EMBRYO_MOLD)
                    .setUseSound(SoundEvents.ENTITY_SLIME_DEATH_SMALL)
    ));

    private static ContainerType registerContainerType(String name, ContainerType containerType){
        return Registry.register(TWRegistries.CONTAINER_TYPE, new Identifier(BakingProcess.MOD_ID, name), containerType);
    }

    private static Identifier createModId(String path) {
        return new Identifier(BakingProcess.MOD_ID, path);
    }

    public static void registerAll() {}
}