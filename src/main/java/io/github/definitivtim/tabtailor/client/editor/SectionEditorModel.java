package io.github.definitivtim.tabtailor.client.editor;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import io.github.definitivtim.tabtailor.TabTailor;
import io.github.definitivtim.tabtailor.client.TabTailorCommands;
import io.github.definitivtim.tabtailor.config.ConfigManager;
import io.github.definitivtim.tabtailor.config.TabDefinition;
import io.github.definitivtim.tabtailor.config.TabTailorConfig;
import io.github.definitivtim.tabtailor.core.Selector;
import io.github.definitivtim.tabtailor.core.TabContentProcessor;
import net.minecraft.resources.ResourceLocation;

import javax.annotation.Nullable;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * State of the section editor (stage 2) for one tab file: sections with title, icon, items and exclude.
 * Other settings of a section (colors, image, ...) are kept as they are.
 */
final class SectionEditorModel {
    private static final Gson GSON = new Gson();
    private static final Set<String> EDITED_KEYS = Set.of("title", "icon", "items", "exclude");

    static final class Section {
        /** Original title value when it is not a plain string (e.g. {"translate": ...}), kept until the title is edited. */
        @Nullable JsonElement rawTitle;
        String title;
        String icon;
        final List<String> items;
        final List<String> exclude;
        /** All other keys of the section, written back unchanged. */
        final JsonObject extra;

        Section(@Nullable JsonElement rawTitle, String title, String icon, List<String> items, List<String> exclude, JsonObject extra) {
            this.rawTitle = rawTitle;
            this.title = title;
            this.icon = icon;
            this.items = items;
            this.exclude = exclude;
            this.extra = extra;
        }

        static Section create(String title) {
            return new Section(null, title, "", new ArrayList<>(), new ArrayList<>(), new JsonObject());
        }

        Section copy() {
            return new Section(rawTitle, title, icon, new ArrayList<>(items), new ArrayList<>(exclude), extra.deepCopy());
        }

        String signature() {
            return title + "|" + icon + "|" + items + "|" + exclude + "|" + rawTitle + "|" + extra;
        }
    }

    record SaveResult(boolean ok, int problems, String message) {
    }

    final ResourceLocation tabId;
    final List<Section> sections = new ArrayList<>();
    /** True if the file had no sections: the tab kept its normal contents (with "add"/"remove"). */
    boolean plainTab;
    private Path file;
    private JsonObject root = new JsonObject();
    private String savedSignature = "";
    @Nullable String loadError;

    SectionEditorModel(ResourceLocation tabId) {
        this.tabId = tabId;
        load();
    }

    void load() {
        sections.clear();
        loadError = null;
        try {
            file = TabEditorModel.tabFile(tabId, ConfigManager.get(), false);
            if (Files.isRegularFile(file)) {
                root = ConfigManager.readJson(file);
            } else {
                root = new JsonObject();
                root.addProperty("tab", tabId.toString());
            }
        } catch (Exception e) {
            loadError = e.getMessage() == null ? e.toString() : e.getMessage();
            root = new JsonObject();
            root.addProperty("tab", tabId.toString());
        }
        if (root.has("items") && root.get("items").isJsonArray()) {
            // shorthand: one untitled section
            sections.add(new Section(null, "", "", strings(root.get("items")), new ArrayList<>(), new JsonObject()));
        }
        if (root.has("sections") && root.get("sections").isJsonArray()) {
            for (JsonElement el : root.getAsJsonArray("sections")) {
                if (el == null || !el.isJsonObject()) {
                    continue;
                }
                JsonObject o = el.getAsJsonObject();
                JsonElement t = o.get("title");
                String title = "";
                JsonElement rawTitle = null;
                if (t != null && t.isJsonPrimitive()) {
                    title = t.getAsString();
                } else if (t != null && !t.isJsonNull()) {
                    rawTitle = t;
                    title = describeTitle(t);
                }
                String icon = o.has("icon") && o.get("icon").isJsonPrimitive() ? o.get("icon").getAsString() : "";
                JsonObject extra = new JsonObject();
                for (Map.Entry<String, JsonElement> entry : o.entrySet()) {
                    if (!EDITED_KEYS.contains(entry.getKey())) {
                        extra.add(entry.getKey(), entry.getValue());
                    }
                }
                sections.add(new Section(rawTitle, title, icon, strings(o.get("items")), strings(o.get("exclude")), extra));
            }
        }
        plainTab = sections.isEmpty();
        savedSignature = signature();
    }

    private static String describeTitle(JsonElement t) {
        if (t.isJsonObject() && t.getAsJsonObject().has("translate")) {
            return net.minecraft.network.chat.Component.translatable(t.getAsJsonObject().get("translate").getAsString()).getString();
        }
        return t.toString();
    }

    private static List<String> strings(@Nullable JsonElement el) {
        List<String> out = new ArrayList<>();
        if (el != null && el.isJsonArray()) {
            for (JsonElement e : el.getAsJsonArray()) {
                if (e != null && e.isJsonPrimitive()) {
                    out.add(e.getAsString());
                }
            }
        } else if (el != null && el.isJsonPrimitive()) {
            out.add(el.getAsString());
        }
        return out;
    }

    private String signature() {
        StringBuilder sb = new StringBuilder(String.valueOf(plainTab));
        for (Section s : sections) {
            sb.append('\n').append(s.signature());
        }
        return sb.toString();
    }

    boolean dirty() {
        return !signature().equals(savedSignature);
    }

    /** Turns a tab without sections into one with a first section that keeps everything the tab had. */
    void convertToSections(String firstTitle) {
        if (!plainTab) {
            return;
        }
        Section all = Section.create(firstTitle);
        all.items.add("*");
        for (JsonElement e : root.has("add") ? root.getAsJsonArray("add") : new JsonArray()) {
            if (e.isJsonPrimitive()) {
                all.items.add(e.getAsString());
            }
        }
        sections.add(all);
        plainTab = false;
    }

    /** null if the entry can be used, else why not. */
    static @Nullable String validate(String entry) {
        try {
            Selector.parse(entry);
            return null;
        } catch (IllegalArgumentException e) {
            return e.getMessage();
        }
    }

    // ------------------------------------------------------------------ preview

    /** The tab file as it would be written, as JSON object (for the preview). */
    JsonObject toJson() {
        JsonObject out = root.deepCopy();
        out.remove("items");
        if (!plainTab) {
            out.remove("add");
            JsonArray array = new JsonArray();
            for (Section s : sections) {
                array.add(sectionJson(s));
            }
            out.add("sections", array);
        } else {
            out.remove("sections");
        }
        return out;
    }

    private static JsonObject sectionJson(Section s) {
        JsonObject o = new JsonObject();
        if (s.rawTitle != null) {
            o.add("title", s.rawTitle);
        } else if (!s.title.isBlank()) {
            o.addProperty("title", s.title);
        }
        if (!s.icon.isBlank()) {
            o.addProperty("icon", s.icon.trim());
        }
        JsonArray items = new JsonArray();
        s.items.forEach(items::add);
        o.add("items", items);
        if (!s.exclude.isEmpty()) {
            JsonArray ex = new JsonArray();
            s.exclude.forEach(ex::add);
            o.add("exclude", ex);
        }
        for (Map.Entry<String, JsonElement> entry : s.extra.entrySet()) {
            o.add(entry.getKey(), entry.getValue());
        }
        return o;
    }

    @Nullable TabContentProcessor.Preview preview() {
        List<String> problems = new ArrayList<>();
        TabDefinition def = ConfigManager.parseTabForPreview(toJson(), problems);
        if (def == null) {
            return null;
        }
        TabContentProcessor.Preview preview = TabContentProcessor.preview(def);
        if (preview == null) {
            return null;
        }
        List<String> all = new ArrayList<>(problems);
        all.addAll(preview.problems());
        return new TabContentProcessor.Preview(preview.blocks(), all);
    }

    // ------------------------------------------------------------------ saving

    SaveResult save() {
        for (Section s : sections) {
            if (!TabEditorModel.iconValid(s.icon)) {
                return new SaveResult(false, 0, "tabtailor.editor.error.icon|" + s.icon.trim());
            }
            for (String entry : s.items) {
                if (validate(entry) != null) {
                    return new SaveResult(false, 0, "tabtailor.sections.error.entry|" + entry);
                }
            }
        }
        try {
            Path target = TabEditorModel.tabFile(tabId, ConfigManager.get(), true);
            String text = Files.readString(target, StandardCharsets.UTF_8);
            Path rootDir = ConfigManager.root();
            Path backupDir = rootDir.resolve("_backup").resolve(LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss")));
            Path backup = backupDir.resolve(rootDir.relativize(target).toString());
            if (Files.notExists(backup)) {
                Files.createDirectories(backup.getParent());
                Files.copy(target, backup);
            }
            if (!plainTab) {
                text = JsonText.remove(text, "items");
                text = JsonText.remove(text, "add");
                text = JsonText.set(text, "sections", sectionsText());
            }
            Files.writeString(target, text, StandardCharsets.UTF_8);
            file = target;
        } catch (Exception e) {
            TabTailor.LOGGER.error("[Tab Tailor] Section editor could not save", e);
            return new SaveResult(false, 0, "tabtailor.editor.error.save|" + e.getMessage());
        }
        TabTailorConfig reloaded = ConfigManager.reload();
        TabTailorCommands.rebuildTabsNow();
        Set<String> problems = new LinkedHashSet<>(reloaded.problems());
        problems.addAll(TabContentProcessor.lastProblems());
        for (String p : problems) {
            TabTailor.LOGGER.warn("[Tab Tailor] {}", p);
        }
        load();
        return new SaveResult(true, problems.size(), "");
    }

    /** The "sections" array in the same layout as the hand-written files. */
    private String sectionsText() {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < sections.size(); i++) {
            Section s = sections.get(i);
            sb.append(i == 0 ? "\n" : ",\n").append("    {\n");
            List<String> lines = new ArrayList<>();
            if (s.rawTitle != null) {
                lines.add("\"title\": " + GSON.toJson(s.rawTitle));
            } else if (!s.title.isBlank()) {
                lines.add("\"title\": " + JsonText.quote(s.title));
            }
            if (!s.icon.isBlank()) {
                lines.add("\"icon\": " + JsonText.quote(s.icon.trim()));
            }
            lines.add("\"items\": " + array(s.items));
            if (!s.exclude.isEmpty()) {
                lines.add("\"exclude\": " + array(s.exclude));
            }
            for (Map.Entry<String, JsonElement> entry : s.extra.entrySet()) {
                lines.add(JsonText.quote(entry.getKey()) + ": " + GSON.toJson(entry.getValue()));
            }
            for (int l = 0; l < lines.size(); l++) {
                sb.append("      ").append(lines.get(l)).append(l < lines.size() - 1 ? ",\n" : "\n");
            }
            sb.append("    }");
        }
        return sb.append(sections.isEmpty() ? "]" : "\n  ]").toString();
    }

    private static String array(List<String> values) {
        if (values.isEmpty()) {
            return "[ ]";
        }
        StringBuilder sb = new StringBuilder("[\n");
        for (int i = 0; i < values.size(); i++) {
            sb.append("        ").append(JsonText.quote(values.get(i))).append(i < values.size() - 1 ? ",\n" : "\n");
        }
        return sb.append("      ]").toString();
    }


}
