package io.github.definitivtim.tabtailor.client;

import com.mojang.blaze3d.platform.NativeImage;
import io.github.definitivtim.tabtailor.TabTailor;
import io.github.definitivtim.tabtailor.config.ConfigManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;

import javax.annotation.Nullable;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Pictures behind section headers ("image" in a section or section_style).
 *
 * <ul>
 *   <li>{@code "image": "holz.png"} loads config/tabtailor/images/holz.png, no resource pack needed</li>
 *   <li>{@code "image": "farbound:textures/gui/holz.png"} uses a texture from a resource pack or mod</li>
 * </ul>
 * The picture is stretched to the header row (162 x 18 GUI pixels), so 162 x 18 or a multiple like 324 x 36 looks best.
 */
public final class HeaderImages {
    /**
     * @param metaFrametime    "frametime" from a .png.mcmeta next to the image, 0 if none
     * @param metaFrameHeight  "height" from that .mcmeta, 0 if none
     */
    public record Image(ResourceLocation texture, int width, int height, int metaFrametime, int metaFrameHeight) {
        /** Frames stacked vertically: explicit count, else .mcmeta height, else as many 9:1 frames as fit. */
        public int frames(@Nullable Integer configured) {
            if (configured != null && configured > 0) {
                return Math.max(1, Math.min(configured, height));
            }
            if (metaFrameHeight > 0) {
                return Math.max(1, height / metaFrameHeight);
            }
            int frameHeight = Math.max(1, Math.round(width / 9.0F));
            int frames = height / frameHeight;
            // only treat it as animation when the height is an exact multiple of a 9:1 frame
            return frames >= 2 && height % frameHeight == 0 ? frames : 1;
        }
    }

    private static final Map<String, Optional<Image>> CACHE = new HashMap<>();
    private static int cachedGeneration = -1;
    private static int counter;

    private HeaderImages() {
    }

    /** Must be called on the render thread. Returns null (and logs once) if the image cannot be loaded. */
    public static @Nullable Image get(String name) {
        int generation = ConfigManager.get().generation();
        if (generation != cachedGeneration) {
            clear();
            cachedGeneration = generation;
        }
        return CACHE.computeIfAbsent(name, HeaderImages::load).orElse(null);
    }

    private static Optional<Image> load(String name) {
        Minecraft mc = Minecraft.getInstance();
        try {
            Path file = ConfigManager.root().resolve("images").resolve(name).normalize();
            if (file.startsWith(ConfigManager.root().resolve("images")) && Files.isRegularFile(file)) {
                int[] meta = readMeta(file.resolveSibling(file.getFileName() + ".mcmeta"));
                try (InputStream in = Files.newInputStream(file)) {
                    NativeImage image = NativeImage.read(in);
                    int w = image.getWidth();
                    int h = image.getHeight();
                    ResourceLocation id = ResourceLocation.fromNamespaceAndPath(TabTailor.MOD_ID,
                            "dynamic/header_" + (counter++) + "_" + name.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_.-]", "_"));
                    mc.getTextureManager().register(id, new DynamicTexture(image));
                    return Optional.of(new Image(id, w, h, meta[0], meta[1]));
                }
            }
            ResourceLocation id = ResourceLocation.tryParse(name);
            if (id != null && name.contains(":")) {
                Optional<Resource> resource = mc.getResourceManager().getResource(id);
                if (resource.isPresent()) {
                    int[] meta = {0, 0};
                    Optional<Resource> metaResource = mc.getResourceManager().getResource(id.withPath(id.getPath() + ".mcmeta"));
                    if (metaResource.isPresent()) {
                        try (InputStream in = metaResource.get().open()) {
                            meta = parseMeta(new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8));
                        }
                    }
                    try (InputStream in = resource.get().open(); NativeImage image = NativeImage.read(in)) {
                        return Optional.of(new Image(id, image.getWidth(), image.getHeight(), meta[0], meta[1]));
                    }
                }
            }
            TabTailor.LOGGER.warn("[Tab Tailor] Header image \"{}\" not found (put it in config/tabtailor/images/ or use a texture ID)", name);
        } catch (Exception e) {
            TabTailor.LOGGER.warn("[Tab Tailor] Could not load header image \"{}\": {}", name, e.toString());
        }
        return Optional.empty();
    }

    private static int[] readMeta(Path file) {
        try {
            if (Files.isRegularFile(file)) {
                return parseMeta(Files.readString(file));
            }
        } catch (Exception e) {
            TabTailor.LOGGER.warn("[Tab Tailor] Could not read {}: {}", file.getFileName(), e.toString());
        }
        return new int[]{0, 0};
    }

    /** Reads {"animation": {"frametime": 2, "height": 36}} like Minecraft's texture .mcmeta files. */
    private static int[] parseMeta(String json) {
        com.google.gson.stream.JsonReader reader = new com.google.gson.stream.JsonReader(new java.io.StringReader(json));
        reader.setLenient(true);
        com.google.gson.JsonObject root = com.google.gson.JsonParser.parseReader(reader).getAsJsonObject();
        if (!root.has("animation") || !root.get("animation").isJsonObject()) {
            return new int[]{0, 0};
        }
        com.google.gson.JsonObject animation = root.getAsJsonObject("animation");
        int frametime = animation.has("frametime") ? animation.get("frametime").getAsInt() : 0;
        int height = animation.has("height") ? animation.get("height").getAsInt() : 0;
        return new int[]{Math.max(0, frametime), Math.max(0, height)};
    }

    private static void clear() {
        Minecraft mc = Minecraft.getInstance();
        for (Optional<Image> image : CACHE.values()) {
            image.ifPresent(i -> {
                if (i.texture().getNamespace().equals(TabTailor.MOD_ID)) {
                    mc.getTextureManager().release(i.texture());
                }
            });
        }
        CACHE.clear();
    }
}
