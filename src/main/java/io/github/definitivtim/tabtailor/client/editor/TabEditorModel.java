package io.github.definitivtim.tabtailor.client.editor;

import io.github.definitivtim.tabtailor.TabTailor;
import io.github.definitivtim.tabtailor.client.TabTailorCommands;
import io.github.definitivtim.tabtailor.config.ConfigManager;
import io.github.definitivtim.tabtailor.config.TabDefinition;
import io.github.definitivtim.tabtailor.config.TabTailorConfig;
import io.github.definitivtim.tabtailor.core.TabContentProcessor;
import io.github.definitivtim.tabtailor.core.TabOrdering;
import io.github.definitivtim.tabtailor.mixin.CreativeModeTabAccessor;
import io.github.definitivtim.tabtailor.mixin.CreativeModeTabRegistryAccessor;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import javax.annotation.Nullable;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * State of the in-game editor (stage 1: order, visibility, title, icon) and writing it back into the config files.
 * Files are changed in place, comments and all other settings in them stay untouched.
 */
final class TabEditorModel {
    static final class Row {
        final CreativeModeTab tab;
        final ResourceLocation id;
        final Component modTitle;
        final boolean originalHidden;
        final String originalTitle;
        final String originalIcon;
        boolean hidden;
        String title;
        String icon;

        Row(CreativeModeTab tab, ResourceLocation id, Component modTitle, boolean hidden, String title, String icon) {
            this.tab = tab;
            this.id = id;
            this.modTitle = modTitle;
            this.originalHidden = hidden;
            this.hidden = hidden;
            this.originalTitle = title;
            this.title = title;
            this.originalIcon = icon;
            this.icon = icon;
        }

        boolean fileChanged() {
            return !title.equals(originalTitle) || !icon.trim().equals(originalIcon);
        }

        /** Title shown in the editor: the edited one, or the mod's own. */
        Component displayTitle() {
            return title.isBlank() ? modTitle : Component.literal(title);
        }

        /** Icon shown in the editor: the typed item, or what the tab shows now. */
        ItemStack displayIcon() {
            Item item = iconItem(icon);
            if (item != null) {
                return new ItemStack(item);
            }
            try {
                return tab.getIconItem();
            } catch (Throwable t) {
                return ItemStack.EMPTY;
            }
        }
    }

    /** Result of {@link #save()}. */
    record SaveResult(boolean ok, int problems, String message) {
    }

    final List<Row> rows = new ArrayList<>();
    private List<ResourceLocation> originalOrder = List.of();

    TabEditorModel() {
        load();
    }

    void load() {
        rows.clear();
        TabTailorConfig config = ConfigManager.get();
        List<CreativeModeTab> all = new ArrayList<>();
        Set<CreativeModeTab> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        for (CreativeModeTab tab : CreativeModeTabRegistryAccessor.tabtailor$getSortedTabs()) {
            if (tab.getType() == CreativeModeTab.Type.CATEGORY && seen.add(tab)) {
                all.add(tab);
            }
        }
        for (CreativeModeTab tab : BuiltInRegistries.CREATIVE_MODE_TAB) {
            if (tab.getType() == CreativeModeTab.Type.CATEGORY && seen.add(tab)) {
                all.add(tab);
            }
        }
        List<ResourceLocation> order = new ArrayList<>();
        for (CreativeModeTab tab : TabOrdering.order(all, config)) {
            ResourceLocation id = BuiltInRegistries.CREATIVE_MODE_TAB.getKey(tab);
            if (id == null) {
                continue;
            }
            TabDefinition def = config.tab(id);
            String title = def != null && def.title() != null ? def.title().getString() : "";
            String icon = def != null && def.icon() != null ? def.icon().toString() : "";
            Component modTitle;
            try {
                modTitle = ((CreativeModeTabAccessor) tab).tabtailor$getRawDisplayName();
            } catch (Throwable t) {
                modTitle = Component.literal(id.toString());
            }
            rows.add(new Row(tab, id, modTitle, TabOrdering.inHiddenList(tab, config), title, icon));
            order.add(id);
        }
        originalOrder = List.copyOf(order);
    }

    boolean dirty() {
        if (orderChanged()) {
            return true;
        }
        for (Row row : rows) {
            if (row.hidden != row.originalHidden || row.fileChanged()) {
                return true;
            }
        }
        return false;
    }

    private boolean orderChanged() {
        if (rows.size() != originalOrder.size()) {
            return true;
        }
        for (int i = 0; i < rows.size(); i++) {
            if (!rows.get(i).id.equals(originalOrder.get(i))) {
                return true;
            }
        }
        return false;
    }

    void move(int from, int to) {
        if (from == to || from < 0 || to < 0 || from >= rows.size() || to >= rows.size()) {
            return;
        }
        Row row = rows.remove(from);
        rows.add(to, row);
    }

    static @Nullable Item iconItem(String text) {
        String trimmed = text.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        ResourceLocation id = ResourceLocation.tryParse(trimmed.contains(":") ? trimmed : "minecraft:" + trimmed);
        if (id == null) {
            return null;
        }
        return BuiltInRegistries.ITEM.getOptional(id).filter(item -> item != Items.AIR).orElse(null);
    }

    /** null if fine, else a short reason why the icon text is not valid. */
    static boolean iconValid(String text) {
        return text.isBlank() || iconItem(text) != null;
    }

    // ------------------------------------------------------------------ saving

    SaveResult save() {
        for (Row row : rows) {
            if (!iconValid(row.icon)) {
                return new SaveResult(false, 0, "tabtailor.editor.error.icon|" + row.icon.trim());
            }
        }
        Path root = ConfigManager.root();
        Path backup = root.resolve("_backup").resolve(LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss")));
        TabTailorConfig config = ConfigManager.get();
        int written = 0;
        try {
            // main file: order and hidden tabs
            boolean orderChanged = orderChanged();
            boolean hiddenChanged = false;
            for (Row row : rows) {
                hiddenChanged |= row.hidden != row.originalHidden;
            }
            if (orderChanged || hiddenChanged) {
                Path main = root.resolve("tabtailor.json");
                String text = Files.isRegularFile(main) ? Files.readString(main, StandardCharsets.UTF_8) : "{\n}\n";
                backup(root, main, backup);
                if (orderChanged) {
                    List<String> order = new ArrayList<>();
                    for (Row row : rows) {
                        order.add(row.id.toString());
                    }
                    order.add("*"); // tabs of mods added later go to the end
                    text = JsonText.set(text, "tab_order", JsonText.stringArray(order));
                }
                if (hiddenChanged) {
                    text = JsonText.set(text, "hidden_tabs", JsonText.stringArray(hiddenList(config)));
                }
                Files.createDirectories(main.getParent());
                Files.writeString(main, text, StandardCharsets.UTF_8);
                written++;
            }

            // tab files: title and icon
            for (Row row : rows) {
                if (!row.fileChanged()) {
                    continue;
                }
                Path file = tabFile(row.id, config, true);
                String text = Files.readString(file, StandardCharsets.UTF_8);
                backup(root, file, backup);
                if (!row.title.equals(row.originalTitle)) {
                    text = row.title.isBlank() ? JsonText.remove(text, "title") : JsonText.set(text, "title", JsonText.quote(row.title));
                }
                String icon = row.icon.trim();
                if (!icon.equals(row.originalIcon)) {
                    if (icon.isEmpty()) {
                        text = JsonText.remove(text, "icon");
                    } else {
                        Item item = iconItem(icon);
                        text = JsonText.set(text, "icon", JsonText.quote(BuiltInRegistries.ITEM.getKey(item).toString()));
                    }
                }
                Files.writeString(file, text, StandardCharsets.UTF_8);
                written++;
            }
        } catch (Exception e) {
            TabTailor.LOGGER.error("[Tab Tailor] Editor could not save", e);
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
        TabTailor.LOGGER.info("[Tab Tailor] Editor saved {} file(s), backup in {}", written, backup);
        return new SaveResult(true, problems.size(), "");
    }

    /**
     * New "hidden_tabs": entries of the old list that still match only hidden tabs (so "@copycats" survives), entries
     * matching no installed tab (mods that may come back), plus the IDs of tabs hidden in the editor.
     */
    private List<String> hiddenList(TabTailorConfig config) {
        List<String> out = new ArrayList<>();
        Set<CreativeModeTab> covered = Collections.newSetFromMap(new IdentityHashMap<>());
        for (String pattern : config.hiddenTabs()) {
            boolean any = false;
            boolean allStillHidden = true;
            List<CreativeModeTab> matched = new ArrayList<>();
            for (Row row : rows) {
                if (TabOrdering.matches(pattern, row.tab)) {
                    any = true;
                    matched.add(row.tab);
                    allStillHidden &= row.hidden;
                }
            }
            if (!any || allStillHidden) {
                out.add(pattern);
                covered.addAll(matched);
            }
        }
        for (Row row : rows) {
            if (row.hidden && !covered.contains(row.tab)) {
                out.add(row.id.toString());
            }
        }
        return out;
    }

    /** The file of a tab; with create = true a minimal one is written if the tab has none yet. */
    static Path tabFile(ResourceLocation id, TabTailorConfig config, boolean create) throws IOException {
        TabDefinition def = config.tab(id);
        if (def != null) {
            return ConfigManager.root().resolve(def.sourceFile());
        }
        Path dir = ConfigManager.tabsDir();
        String base = (id.getNamespace() + "__" + id.getPath()).replaceAll("[^a-z0-9_.-]", "_").toLowerCase(Locale.ROOT);
        Path file = dir.resolve(base + ".json");
        int n = 2;
        while (Files.exists(file)) {
            file = dir.resolve(base + "_" + n++ + ".json");
        }
        if (create) {
            Files.createDirectories(dir);
            Files.writeString(file, "// Created by the Tab Tailor editor.\n{\n  \"tab\": " + JsonText.quote(id.toString()) + "\n}\n",
                    StandardCharsets.UTF_8);
        }
        return file;
    }

    private static void backup(Path root, Path file, Path backupDir) throws IOException {
        if (!Files.isRegularFile(file)) {
            return;
        }
        Path target = backupDir.resolve(root.relativize(file).toString());
        if (Files.exists(target)) {
            return;
        }
        Files.createDirectories(target.getParent());
        Files.copy(file, target);
    }
}
