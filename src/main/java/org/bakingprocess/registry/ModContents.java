package org.bakingprocess.registry;

import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;
import org.bakingprocess.BakingProcess;
import org.bakingprocess.content.ShapedDoughContent;
import org.twcore.content.Content;
import org.twcore.registry.TWRegistries;

public class ModContents {
    public static final String SHAPED_DOUGH = "shaped_dough";

    // 定型面团
    public static final Content TOAST_EMBRYO = registerContent("toast_embryo", new ShapedDoughContent(
            SHAPED_DOUGH, createModId("toast_dough"), createModId("toast_embryo_mold")));

    public static final Content TOAST = registerContent("toast", new ShapedDoughContent(
            SHAPED_DOUGH, createModId("toast"), createModId("toast_embryo_mold")));

    public static final Content CAKE_EMBRYO = registerContent("cake_embryo", new ShapedDoughContent(
            SHAPED_DOUGH, createModId("cake_dough"), createModId("cake_embryo_mold")));

    public static final Content BAKED_CAKE_EMBRYO = registerContent("baked_cake_embryo", new ShapedDoughContent(
            SHAPED_DOUGH, createModId("baked_cake_embryo"), createModId("cake_embryo_mold")));

    private static Content registerContent(String name, Content content){
        return Registry.register(TWRegistries.CONTENT, new Identifier(BakingProcess.MOD_ID, name), content);
    }

    private static Identifier createModId(String path) {
        return new Identifier(BakingProcess.MOD_ID, path);
    }

    public static void registerAll() {}
}
