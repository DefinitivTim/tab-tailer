package io.github.definitivtim.tabtailor.compat.emi;

import dev.emi.emi.api.EmiEntrypoint;
import dev.emi.emi.api.EmiPlugin;
import dev.emi.emi.api.EmiRegistry;
import dev.emi.emi.api.widget.Bounds;
import io.github.definitivtim.tabtailor.client.SectionScreenHelper;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;

/** Keeps EMI's side panels from drawing under the section tabs. Only loaded when EMI is installed. */
@EmiEntrypoint
public final class TabTailorEmiPlugin implements EmiPlugin {
    @Override
    public void register(EmiRegistry registry) {
        registry.addExclusionArea(CreativeModeInventoryScreen.class, (screen, consumer) -> {
            int[] area = SectionScreenHelper.lastSideTabArea();
            if (area != null) {
                consumer.accept(new Bounds(area[0], area[1], area[2], area[3]));
            }
        });
    }
}
