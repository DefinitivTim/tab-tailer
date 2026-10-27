package io.github.definitivtim.tabtailor.core;

import io.github.definitivtim.tabtailor.TabTailor;
import io.github.definitivtim.tabtailor.config.ConfigManager;
import io.github.definitivtim.tabtailor.config.TabDefinition;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.registries.RegisterEvent;

/**
 * Registers the tabs marked with "new_tab": true. Creative tabs are not synced with servers,
 * so registering them only on the client is safe. Registries freeze after startup, so new tabs need a restart.
 */
public final class NewTabRegistrar {
    private NewTabRegistrar() {
    }

    public static void onRegister(RegisterEvent event) {
        event.register(Registries.CREATIVE_MODE_TAB, helper -> {
            for (TabDefinition def : ConfigManager.get().tabs()) {
                if (!def.newTab()) {
                    continue;
                }
                Component title = def.title() != null ? def.title() : Component.literal(def.id().getPath());
                CreativeModeTab tab = CreativeModeTab.builder()
                        .title(title)
                        // The real icon comes from TabOverrides, this is only the fallback.
                        .icon(() -> new ItemStack(Items.CHEST))
                        .displayItems((params, output) -> {
                            // Filled by TabContentProcessor after all tabs are built.
                        })
                        .build();
                helper.register(def.id(), tab);
                TabTailor.LOGGER.info("[Tab Tailor] Registered new creative tab {}", def.id());
            }
        });
    }
}
