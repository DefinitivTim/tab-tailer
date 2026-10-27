package io.github.definitivtim.tabtailor.client;

import io.github.definitivtim.tabtailor.config.ConfigManager;
import io.github.definitivtim.tabtailor.config.TabDefinition;
import io.github.definitivtim.tabtailor.config.TabLook;
import io.github.definitivtim.tabtailor.config.TabTailorConfig;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.CreativeModeTab;

import javax.annotation.Nullable;

/**
 * Window look per tab. By default every normal tab gets the plain vanilla window, so mods can no longer give
 * their tab a different background or colors. "mod" keeps what the mod set.
 */
public final class TabLooks {
    private static final ResourceLocation VANILLA_BACKGROUND = CreativeModeTab.createTextureLocation("items");
    private static final ResourceLocation VANILLA_SCROLLER = ResourceLocation.withDefaultNamespace("container/creative_inventory/scroller");

    private TabLooks() {
    }

    private static @Nullable TabLook look(CreativeModeTab tab) {
        if (tab.getType() != CreativeModeTab.Type.CATEGORY) {
            return null; // search, hotbar and inventory keep their own look
        }
        TabTailorConfig config = ConfigManager.get();
        ResourceLocation id = BuiltInRegistries.CREATIVE_MODE_TAB.getKey(tab);
        TabDefinition def = id == null ? null : config.tab(id);
        TabLook look = def != null ? def.tabLook().inherit(config.tabLook()) : config.tabLook();
        return look.inherit(TabLook.DEFAULT);
    }

    public static @Nullable ResourceLocation background(CreativeModeTab tab) {
        TabLook look = look(tab);
        if (look == null || look.background() == null || look.background().equalsIgnoreCase("mod")) {
            return null;
        }
        return texture(look.background());
    }

    /** Texture for a "background" value ("vanilla", a PNG in config/tabtailor/images/ or a texture ID). */
    public static ResourceLocation texture(String value) {
        if (value.equalsIgnoreCase("vanilla") || value.equalsIgnoreCase("mod")) {
            return VANILLA_BACKGROUND;
        }
        if (!value.contains(":") || value.toLowerCase().endsWith(".png") && !value.contains("textures/")) {
            HeaderImages.Image image = HeaderImages.get(value);
            return image != null ? image.texture() : VANILLA_BACKGROUND;
        }
        ResourceLocation id = ResourceLocation.tryParse(value);
        return id != null ? id : VANILLA_BACKGROUND;
    }

    public static @Nullable Integer labelColor(CreativeModeTab tab) {
        TabLook look = look(tab);
        if (look == null) {
            return null;
        }
        if (look.labelColor() != null) {
            return look.labelColor();
        }
        return Boolean.TRUE.equals(look.keepModColors()) ? null : TabLook.VANILLA_LABEL;
    }

    public static @Nullable Integer slotHighlight(CreativeModeTab tab) {
        TabLook look = look(tab);
        if (look == null) {
            return null;
        }
        if (look.slotHighlight() != null) {
            return look.slotHighlight();
        }
        return Boolean.TRUE.equals(look.keepModColors()) ? null : TabLook.VANILLA_SLOT_HIGHLIGHT;
    }

    public static @Nullable ResourceLocation scroller(CreativeModeTab tab) {
        TabLook look = look(tab);
        if (look == null || look.scroller() == null || look.scroller().equalsIgnoreCase("mod")) {
            return null;
        }
        if (look.scroller().equalsIgnoreCase("vanilla")) {
            return VANILLA_SCROLLER;
        }
        ResourceLocation id = ResourceLocation.tryParse(look.scroller());
        return id != null ? id : VANILLA_SCROLLER;
    }
}
