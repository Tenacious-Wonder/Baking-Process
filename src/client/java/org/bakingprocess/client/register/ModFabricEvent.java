package org.bakingprocess.client.register;

import net.fabricmc.fabric.api.client.rendering.v1.TooltipComponentCallback;
import org.bakingprocess.client.render.gui.tooltip.FlourSackTooltipComponent;
import org.bakingprocess.item.FlourSackItem;

public class ModFabricEvent {
    public static void registerFabricEvents() {
        TooltipComponentCallback.EVENT.register(data -> {
            if (data instanceof FlourSackItem.FlourSackTooltipData flourSackData) {
                return new FlourSackTooltipComponent(flourSackData);
            }
            return null;
        });
    }
}
