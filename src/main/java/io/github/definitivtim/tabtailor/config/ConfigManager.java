package io.github.definitivtim.tabtailor.config;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import com.google.gson.stream.JsonReader;
import io.github.definitivtim.tabtailor.TabTailor;
import io.github.definitivtim.tabtailor.core.Selector;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.fml.loading.FMLPaths;
import javax.annotation.Nullable;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Loads config/tabtailor/tabtailor.json and every file in config/tabtailor/tabs/.
 * The files are JSON with comments allowed (// and /* *&#47;), so they can be documented inline.
 */
public final class ConfigManager {
    private static final Set<String> MAIN_KEYS = Set.of("tab_order", "hidden_tabs", "show_hidden_tabs", "hidden_items", "section_style", "section_tabs", "sidebar", "tab_style");
    private static final Set<String> TAB_KEYS = Set.of("tab", "new_tab", "title", "icon", "exclusive", "items", "sidebar", "tab_style",
            "sections", "add", "remove", "section_style");
    private static final Set<String> SECTION_KEYS = Set.of("title", "icon", "items", "exclude", "background", "image", "image_frames", "image_frametime", "image_hover_only",
            "color", "line", "sprite", "shadow");
    private static final Set<String> STYLE_KEYS = Set.of("background", "color", "line", "sprite", "shadow", "image",
            "image_frames", "image_frametime", "image_hover_only");
    private static final Set<String> SIDEBAR_KEYS = Set.of("background", "slot", "background_active", "border_active",
            "button", "button_active", "arrow", "arrow_active");

    private static volatile TabTailorConfig current = TabTailorConfig.EMPTY;
    private static int generation = 0;

    private ConfigManager() {
    }

    public static TabTailorConfig get() {
        return current;
    }

    public static Path root() {
        return FMLPaths.CONFIGDIR.get().resolve(TabTailor.MOD_ID);
    }

    public static Path tabsDir() {
        return root().resolve("tabs");
    }

    public static Path exportDir() {
        return root().resolve("export");
    }

    /** Reads all files again. Never throws: problems end up in {@link TabTailorConfig#problems()}. */
    public static synchronized TabTailorConfig reload() {
        List<String> problems = new ArrayList<>();
        try {
            writeDefaultsIfMissing();
        } catch (IOException e) {
            problems.add("Could not create default config files: " + e.getMessage());
        }

        List<String> tabOrder = new ArrayList<>();
        List<String> hiddenTabs = new ArrayList<>();
        List<Selector> hiddenItems = new ArrayList<>();
        SectionStyle globalStyle = SectionStyle.DEFAULT;
        boolean sectionTabs = true;
        boolean showHiddenTabs = false;
        SidebarStyle sidebarStyle = SidebarStyle.DEFAULT;
        TabLook tabLook = TabLook.DEFAULT;

        Path mainFile = root().resolve("tabtailor.json");
        if (Files.isRegularFile(mainFile)) {
            String file = "tabtailor.json";
            try {
                JsonObject main = readObject(mainFile);
                warnUnknownKeys(main, MAIN_KEYS, file, "", problems);
                tabOrder.addAll(strings(main, "tab_order", file, problems));
                hiddenTabs.addAll(strings(main, "hidden_tabs", file, problems));
                hiddenItems.addAll(selectors(strings(main, "hidden_items", file, problems), file, problems));
                sectionTabs = bool(main, "section_tabs", true);
                showHiddenTabs = bool(main, "show_hidden_tabs", false);
                if (main.has("tab_style")) {
                    tabLook = tabLook(main.get("tab_style"), file, problems).inherit(TabLook.DEFAULT);
                }
                if (main.has("sidebar")) {
                    sidebarStyle = sidebar(main.get("sidebar"), file, problems).inherit(SidebarStyle.DEFAULT);
                }
                if (main.has("section_style")) {
                    globalStyle = style(main.get("section_style"), file, "section_style", problems).inherit(SectionStyle.DEFAULT);
                }
            } catch (Exception e) {
                problems.add(file + ": " + describe(e));
            }
        }

        List<TabDefinition> tabs = new ArrayList<>();
        Map<ResourceLocation, TabDefinition> byId = new LinkedHashMap<>();
        for (Path path : tabFiles(problems)) {
            String file = "tabs/" + path.getFileName();
            try {
                TabDefinition def = parseTab(readObject(path), file, problems);
                if (def == null) {
                    continue;
                }
                TabDefinition existing = byId.get(def.id());
                if (existing != null) {
                    problems.add(file + ": tab " + def.id() + " is already configured in " + existing.sourceFile() + ", this file is ignored");
                    continue;
                }
                byId.put(def.id(), def);
                tabs.add(def);
            } catch (Exception e) {
                problems.add(file + ": " + describe(e));
            }
        }

        TabTailorConfig config = new TabTailorConfig(List.copyOf(tabOrder), List.copyOf(hiddenTabs), showHiddenTabs, List.copyOf(hiddenItems),
                globalStyle, sectionTabs, sidebarStyle, tabLook, List.copyOf(tabs), Collections.unmodifiableMap(byId), List.copyOf(problems), ++generation);
        current = config;
        for (String problem : problems) {
            TabTailor.LOGGER.warn("[Tab Tailor] {}", problem);
        }
        TabTailor.LOGGER.info("[Tab Tailor] Loaded {} tab file(s), {} problem(s)", tabs.size(), problems.size());
        return config;
    }

    private static List<Path> tabFiles(List<String> problems) {
        Path dir = tabsDir();
        if (!Files.isDirectory(dir)) {
            return List.of();
        }
        try (Stream<Path> stream = Files.list(dir)) {
            return stream
                    .filter(Files::isRegularFile)
                    .filter(p -> {
                        String name = p.getFileName().toString().toLowerCase(Locale.ROOT);
                        return (name.endsWith(".json") || name.endsWith(".json5")) && !name.startsWith("_");
                    })
                    .sorted()
                    .toList();
        } catch (IOException e) {
            problems.add("Could not list " + dir + ": " + e.getMessage());
            return List.of();
        }
    }

    // ------------------------------------------------------------------ tab files

    private static @Nullable TabDefinition parseTab(JsonObject json, String file, List<String> problems) {
        warnUnknownKeys(json, TAB_KEYS, file, "", problems);
        if (!json.has("tab")) {
            problems.add(file + ": missing \"tab\" (the ID of the tab this file is about)");
            return null;
        }
        boolean newTab = bool(json, "new_tab", false);
        String rawId = json.get("tab").getAsString().trim();
        ResourceLocation id = rawId.contains(":") ? ResourceLocation.tryParse(rawId)
                : ResourceLocation.tryParse((newTab ? TabTailor.MOD_ID : "minecraft") + ":" + rawId);
        if (id == null) {
            problems.add(file + ": \"" + rawId + "\" is not a valid tab ID (only a-z, 0-9, _ - . / allowed)");
            return null;
        }

        Component title = json.has("title") ? component(json.get("title")) : null;
        ResourceLocation icon = json.has("icon") ? itemId(json.get("icon").getAsString(), file, "icon", problems) : null;
        boolean exclusive = bool(json, "exclusive", false);
        SectionStyle tabStyle = json.has("section_style") ? style(json.get("section_style"), file, "section_style", problems) : SectionStyle.EMPTY;

        List<SectionDefinition> sections = null;
        if (json.has("items")) {
            sections = new ArrayList<>();
            sections.add(new SectionDefinition(null, null, tabStyle,
                    selectors(strings(json, "items", file, problems), file, problems), List.of()));
        }
        if (json.has("sections")) {
            if (sections == null) {
                sections = new ArrayList<>();
            }
            JsonElement el = json.get("sections");
            if (!el.isJsonArray()) {
                problems.add(file + ": \"sections\" must be a list [ {...}, {...} ]");
            } else {
                int index = 0;
                for (JsonElement sectionEl : el.getAsJsonArray()) {
                    index++;
                    if (sectionEl == null || sectionEl.isJsonNull()) {
                        continue;
                    }
                    if (!sectionEl.isJsonObject()) {
                        problems.add(file + ": section #" + index + " must be an object { \"title\": ..., \"items\": [...] }");
                        continue;
                    }
                    sections.add(parseSection(sectionEl.getAsJsonObject(), file, "section #" + index, tabStyle, problems));
                }
            }
        }

        List<Selector> add = selectors(strings(json, "add", file, problems), file, problems);
        List<Selector> remove = selectors(strings(json, "remove", file, problems), file, problems);
        if (sections != null && !add.isEmpty()) {
            problems.add(file + ": \"add\" is ignored because the file defines \"items\" or \"sections\" (list the items there instead)");
        }
        if (newTab && sections == null && add.isEmpty()) {
            problems.add(file + ": new tab " + id + " has no \"items\" or \"sections\", it will stay empty and hidden");
        }
        SidebarStyle sidebarStyle = json.has("sidebar") ? sidebar(json.get("sidebar"), file, problems) : SidebarStyle.EMPTY;
        TabLook tabLook = json.has("tab_style") ? tabLook(json.get("tab_style"), file, problems) : TabLook.EMPTY;
        return new TabDefinition(file, id, newTab, title, icon, exclusive,
                sections == null ? null : List.copyOf(sections), add, remove, tabStyle, sidebarStyle, tabLook);
    }

    private static SectionDefinition parseSection(JsonObject json, String file, String where, SectionStyle tabStyle, List<String> problems) {
        warnUnknownKeys(json, SECTION_KEYS, file, where + ": ", problems);
        Component title = json.has("title") ? component(json.get("title")) : null;
        ResourceLocation icon = json.has("icon") ? itemId(json.get("icon").getAsString(), file, where + " icon", problems) : null;
        SectionStyle style = style(json, file, where, problems).inherit(tabStyle);
        List<Selector> items = selectors(strings(json, "items", file, problems), file, problems);
        List<Selector> exclude = selectors(strings(json, "exclude", file, problems), file, problems);
        if (items.isEmpty()) {
            problems.add(file + ": " + where + (title != null ? " (\"" + title.getString() + "\")" : "") + " has no items");
        }
        return new SectionDefinition(title, icon, style, items, exclude);
    }

    // ------------------------------------------------------------------ value helpers

    private static final Set<String> TAB_LOOK_KEYS = Set.of("background", "label_color", "slot_highlight", "scroller", "keep_mod_colors");

    private static TabLook tabLook(JsonElement el, String file, List<String> problems) {
        if (!el.isJsonObject()) {
            problems.add(file + ": \"tab_style\" must be an object like { \"background\": \"vanilla\" }");
            return TabLook.EMPTY;
        }
        JsonObject o = el.getAsJsonObject();
        warnUnknownKeys(o, TAB_LOOK_KEYS, file, "tab_style: ", problems);
        String where = "tab_style";
        return new TabLook(
                o.has("background") ? o.get("background").getAsString().trim() : null,
                color(o, "label_color", file, where, problems),
                color(o, "slot_highlight", file, where, problems),
                o.has("scroller") ? o.get("scroller").getAsString().trim() : null,
                o.has("keep_mod_colors") ? o.get("keep_mod_colors").getAsBoolean() : null);
    }

    private static SidebarStyle sidebar(JsonElement el, String file, List<String> problems) {
        if (!el.isJsonObject()) {
            problems.add(file + ": \"sidebar\" must be an object like { \"background\": \"#C6C6C6\" }");
            return SidebarStyle.EMPTY;
        }
        JsonObject o = el.getAsJsonObject();
        warnUnknownKeys(o, SIDEBAR_KEYS, file, "sidebar: ", problems);
        String where = "sidebar";
        return new SidebarStyle(
                color(o, "background", file, where, problems),
                color(o, "slot", file, where, problems),
                color(o, "background_active", file, where, problems),
                color(o, "border_active", file, where, problems),
                color(o, "button", file, where, problems),
                color(o, "button_active", file, where, problems),
                color(o, "arrow", file, where, problems),
                color(o, "arrow_active", file, where, problems));
    }

    private static SectionStyle style(JsonElement el, String file, String where, List<String> problems) {
        if (!el.isJsonObject()) {
            problems.add(file + ": " + where + " must be an object like { \"background\": \"#AA000000\" }");
            return SectionStyle.EMPTY;
        }
        JsonObject o = el.getAsJsonObject();
        if (where.equals("section_style")) {
            warnUnknownKeys(o, STYLE_KEYS, file, "section_style: ", problems);
        }
        ResourceLocation sprite = null;
        if (o.has("sprite")) {
            sprite = ResourceLocation.tryParse(o.get("sprite").getAsString());
            if (sprite == null) {
                problems.add(file + ": " + where + ": invalid sprite " + o.get("sprite"));
            }
        }
        return new SectionStyle(
                color(o, "background", file, where, problems),
                color(o, "color", file, where, problems),
                color(o, "line", file, where, problems),
                sprite,
                o.has("shadow") ? o.get("shadow").getAsBoolean() : null,
                o.has("image") ? o.get("image").getAsString().trim() : null,
                positiveInt(o, "image_frames", file, where, problems),
                positiveInt(o, "image_frametime", file, where, problems),
                o.has("image_hover_only") ? o.get("image_hover_only").getAsBoolean() : null);
    }

    private static @Nullable Integer positiveInt(JsonObject o, String key, String file, String where, List<String> problems) {
        if (!o.has(key)) {
            return null;
        }
        try {
            int value = o.get(key).getAsInt();
            if (value > 0) {
                return value;
            }
        } catch (RuntimeException ignored) {
            // reported below
        }
        problems.add(file + ": " + where + ": \"" + key + "\" must be a whole number above 0, got " + o.get(key));
        return null;
    }

    private static @Nullable Integer color(JsonObject o, String key, String file, String where, List<String> problems) {
        if (!o.has(key)) {
            return null;
        }
        JsonElement el = o.get(key);
        try {
            if (el.isJsonPrimitive() && el.getAsJsonPrimitive().isNumber()) {
                return el.getAsInt();
            }
            String s = el.getAsString().trim();
            if (s.startsWith("#")) {
                s = s.substring(1);
            } else if (s.startsWith("0x") || s.startsWith("0X")) {
                s = s.substring(2);
            }
            if (s.length() == 6) {
                return 0xFF000000 | Integer.parseUnsignedInt(s, 16);
            }
            if (s.length() == 8) {
                return (int) Long.parseLong(s, 16);
            }
        } catch (RuntimeException ignored) {
            // fall through to the problem report
        }
        problems.add(file + ": " + where + ": \"" + key + "\" must be a color like \"#RRGGBB\" or \"#AARRGGBB\", got " + el);
        return null;
    }

    private static Component component(JsonElement el) {
        if (el.isJsonObject()) {
            JsonObject o = el.getAsJsonObject();
            if (o.has("translate")) {
                String fallback = o.has("fallback") ? o.get("fallback").getAsString() : null;
                return fallback != null
                        ? Component.translatableWithFallback(o.get("translate").getAsString(), fallback)
                        : Component.translatable(o.get("translate").getAsString());
            }
            if (o.has("text")) {
                return Component.literal(o.get("text").getAsString());
            }
        }
        return Component.literal(el.getAsString());
    }

    private static @Nullable ResourceLocation itemId(String raw, String file, String where, List<String> problems) {
        ResourceLocation id = ResourceLocation.tryParse(raw.trim());
        if (id == null) {
            problems.add(file + ": " + where + ": \"" + raw + "\" is not a valid item ID");
        }
        return id;
    }

    private static boolean bool(JsonObject o, String key, boolean fallback) {
        return o.has(key) && !o.get(key).isJsonNull() ? o.get(key).getAsBoolean() : fallback;
    }

    /** Accepts a single string or a list of strings. Empty entries and nulls (trailing commas) are skipped. */
    private static List<String> strings(JsonObject o, String key, String file, List<String> problems) {
        if (!o.has(key) || o.get(key).isJsonNull()) {
            return List.of();
        }
        JsonElement el = o.get(key);
        List<String> out = new ArrayList<>();
        if (el.isJsonPrimitive()) {
            out.add(el.getAsString());
        } else if (el.isJsonArray()) {
            JsonArray array = el.getAsJsonArray();
            for (JsonElement entry : array) {
                if (entry == null || entry.isJsonNull()) {
                    continue;
                }
                if (entry instanceof JsonPrimitive p && p.isString()) {
                    if (!p.getAsString().isBlank()) {
                        out.add(p.getAsString().trim());
                    }
                } else {
                    problems.add(file + ": entries of \"" + key + "\" must be text in quotes, got " + entry);
                }
            }
        } else {
            problems.add(file + ": \"" + key + "\" must be a list like [\"minecraft:stone\", \"@create\"]");
        }
        return out;
    }

    private static List<Selector> selectors(List<String> raw, String file, List<String> problems) {
        List<Selector> out = new ArrayList<>(raw.size());
        for (String s : raw) {
            try {
                out.add(Selector.parse(s));
            } catch (IllegalArgumentException e) {
                problems.add(file + ": " + e.getMessage());
            }
        }
        return List.copyOf(out);
    }

    private static void warnUnknownKeys(JsonObject o, Set<String> known, String file, String prefix, List<String> problems) {
        for (String key : o.keySet()) {
            if (!known.contains(key)) {
                problems.add(file + ": " + prefix + "unknown setting \"" + key + "\" (known: " + String.join(", ", known.stream().sorted().toList()) + ")");
            }
        }
    }

    /** Style parsers for the in-game style editor. */
    public static SectionStyle parseSectionStyle(JsonObject o, List<String> problems) {
        return style(o, "editor", "style", problems);
    }

    public static SidebarStyle parseSidebar(JsonObject o, List<String> problems) {
        return sidebar(o, "editor", problems);
    }

    public static TabLook parseTabLook(JsonObject o, List<String> problems) {
        return tabLook(o, "editor", problems);
    }

    /** Lenient read of one config file (comments allowed), for the in-game editor. */
    public static JsonObject readJson(Path path) throws IOException {
        return readObject(path);
    }

    /** Parses a tab file's content without loading it, for the editor preview. */
    public static @Nullable TabDefinition parseTabForPreview(JsonObject json, List<String> problems) {
        return parseTab(json, "editor", problems);
    }

    private static JsonObject readObject(Path path) throws IOException {
        try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            JsonReader json = new JsonReader(reader);
            json.setLenient(true);
            JsonElement el = JsonParser.parseReader(json);
            if (!el.isJsonObject()) {
                throw new JsonParseException("the file must contain one object { ... }");
            }
            return el.getAsJsonObject();
        }
    }

    private static String describe(Exception e) {
        String msg = e.getMessage();
        if (e instanceof JsonParseException && e.getCause() != null && e.getCause().getMessage() != null) {
            msg = e.getCause().getMessage();
        }
        return msg == null ? e.getClass().getSimpleName() : msg;
    }

    // ------------------------------------------------------------------ defaults

    private static void writeDefaultsIfMissing() throws IOException {
        Path root = root();
        Files.createDirectories(tabsDir());
        Files.createDirectories(root.resolve("images"));
        Path main = root.resolve("tabtailor.json");
        if (!Files.exists(main)) {
            Files.writeString(main, DefaultFiles.MAIN, StandardCharsets.UTF_8);
        }
        Path example = tabsDir().resolve("_example.json");
        if (!Files.exists(example)) {
            Files.writeString(example, DefaultFiles.EXAMPLE_TAB, StandardCharsets.UTF_8);
        }
        Path readme = root.resolve("README.txt");
        if (!Files.exists(readme)) {
            Files.writeString(readme, DefaultFiles.README, StandardCharsets.UTF_8);
        }
    }
}
