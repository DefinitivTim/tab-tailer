package io.github.definitivtim.tabtailor;

import com.mojang.logging.LogUtils;
import io.github.definitivtim.tabtailor.client.TabTailorCommands;
import io.github.definitivtim.tabtailor.config.ConfigManager;
import io.github.definitivtim.tabtailor.core.NewTabRegistrar;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import org.slf4j.Logger;

/**
 * Tab Tailor: rearrange, rename, hide and refill creative mode tabs and split them into sections.
 * Purely client side: servers do not need the mod.
 */
@Mod(value = TabTailor.MOD_ID, dist = Dist.CLIENT)
public final class TabTailor {
    public static final String MOD_ID = "tabtailor";
    public static final Logger LOGGER = LogUtils.getLogger();

    public TabTailor(IEventBus modBus, ModContainer container) {
        ConfigManager.reload();
        modBus.addListener(NewTabRegistrar::onRegister);
        NeoForge.EVENT_BUS.addListener(TabTailorCommands::register);
        // "Config" button in the mod list opens the editor
        container.registerExtensionPoint(net.neoforged.neoforge.client.gui.IConfigScreenFactory.class,
                (mod, parent) -> new io.github.definitivtim.tabtailor.client.editor.TabEditorScreen(parent));
    }
}
