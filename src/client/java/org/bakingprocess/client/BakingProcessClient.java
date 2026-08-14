package org.bakingprocess.client;

import net.fabricmc.api.ClientModInitializer;
import org.bakingprocess.client.register.ModFabricEvent;
import org.bakingprocess.client.register.RenderRegistry;
import org.bakingprocess.client.render.model.ModModelRules;
import org.twcore.api.event.TwCoreClientRegisterEvent;

public class BakingProcessClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        RenderRegistry.registryRender();
        ModFabricEvent.registerFabricEvents();
        TwCoreClientRegisterEvent.TW_CORE_CLIENT_REGISTRAR.register(BakingProcessClient::registerCore);
    }

    public static void registerCore() {
        ModModelRules.register();
    }
}
