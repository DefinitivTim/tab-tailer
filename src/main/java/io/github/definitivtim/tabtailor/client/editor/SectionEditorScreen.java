package io.github.definitivtim.tabtailor.client.editor;

import io.github.definitivtim.tabtailor.core.Selector;
import io.github.definitivtim.tabtailor.core.TabContentProcessor;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Mth;
import net.minecraft.Util;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.lwjgl.glfw.GLFW;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Stage 2 of the editor: the sections of one tab. Sections on the left, the entries of the selected section in the
 * middle, an item picker and a live preview on the right.
 */
public final class SectionEditorScreen extends Screen {
    private static final int MARGIN = 8;
    private static final int TOP = 26;
    private static final int CELL = 18;
    private static final int C_PANEL = 0xB0101010;
    private static final int C_PANEL_EDGE = 0xFF3A3A3A;
    private static final int C_TEXT = 0xFFFFFFFF;
    private static final int C_DIM = 0xFF9A9A9A;
    private static final int C_FAINT = 0xFF5E5E5E;
    private static final int C_HEADER = 0xC0303030;
    private static final ResourceLocation BUTTON = ResourceLocation.withDefaultNamespace("widget/button");
    private static final ResourceLocation BUTTON_HIGHLIGHTED = ResourceLocation.withDefaultNamespace("widget/button_highlighted");

    private final Screen parent;
    private final CreativeModeTab tab;
    private final Component tabTitle;
    private final SectionEditorModel model;

    private DragList sectionList;
    private DragList entryList;
    private boolean showExclude;
    private boolean showPreview;

    // picker
    private static List<ItemStack> allItems;
    private static Map<Item, String> searchText;
    private List<ItemStack> filtered = List.of();
    private double pickerScroll;
    private double previewScroll;

    // preview
    private @Nullable TabContentProcessor.Preview preview;
    private int revision;
    private int previewRevision = -1;
    private long previewTime;
    private final Set<Item> inTab = java.util.Collections.newSetFromMap(new IdentityHashMap<>());
    private final Set<Item> inSection = java.util.Collections.newSetFromMap(new IdentityHashMap<>());
    private final Map<String, ItemStack> entryIcons = new HashMap<>();

    private Component status = Component.empty();
    private int statusColor = C_DIM;
    private boolean syncing;

    // widgets
    private EditBox titleBox;
    private EditBox iconBox;
    private EditBox addBox;
    private EditBox searchBox;
    private Button itemsButton;
    private Button excludeButton;
    private Button pickerButton;
    private Button previewButton;
    private Button addSectionButton;
    private Button removeSectionButton;
    private Button convertButton;
    private Button addEntryButton;
    private Button saveButton;
    private Button styleButton;

    // layout
    private int ax0;
    private int ax1;
    private int bx0;
    private int bx1;
    private int cx0;
    private int cx1;
    private int bottom;
    private int gridY0;

    public SectionEditorScreen(Screen parent, CreativeModeTab tab, ResourceLocation id, Component tabTitle) {
        super(Component.translatable("tabtailor.sections.title", tabTitle));
        this.parent = parent;
        this.tab = tab;
        this.tabTitle = tabTitle;
        this.model = new SectionEditorModel(id);
        this.sectionList = new DragList(20, () -> model.sections.size(), this::moveSection, this::renderSectionRow);
        this.entryList = new DragList(18, () -> currentEntries().size(), this::moveEntry, this::renderEntryRow);
        sectionList.selected = model.sections.isEmpty() ? -1 : 0;
        sectionList.onChange = this::sectionSelected;
        entryList.onChange = () -> { };
        if (model.loadError != null) {
            setStatus(Component.translatable("tabtailor.sections.error.load", model.loadError), 0xFFFF5555);
        }
    }

    // ------------------------------------------------------------------ model helpers

    private @Nullable SectionEditorModel.Section section() {
        int i = sectionList.selected;
        return i >= 0 && i < model.sections.size() ? model.sections.get(i) : null;
    }

    private List<String> currentEntries() {
        SectionEditorModel.Section s = section();
        if (s == null) {
            return List.of();
        }
        return showExclude ? s.exclude : s.items;
    }

    private void moveSection(int from, int to) {
        SectionEditorModel.Section s = model.sections.remove(from);
        model.sections.add(to, s);
        changed();
    }

    private void moveEntry(int from, int to) {
        List<String> list = currentEntries();
        String e = list.remove(from);
        list.add(to, e);
        changed();
    }

    private void changed() {
        revision++;
        status = Component.empty();
        updateWidgets();
    }

    private void sectionSelected() {
        entryList.selected = currentEntries().isEmpty() ? -1 : 0;
        entryList.scroll = 0;
        syncFields();
    }

    // ------------------------------------------------------------------ layout

    @Override
    protected void init() {
        bottom = height - 46;
        int aw = Mth.clamp(width * 22 / 100, 120, 170);
        int bw = Mth.clamp(width * 28 / 100, 150, 230);
        ax0 = MARGIN;
        ax1 = ax0 + aw;
        bx0 = ax1 + MARGIN;
        bx1 = bx0 + bw;
        cx0 = bx1 + MARGIN;
        cx1 = width - MARGIN;

        // sections column
        sectionList.setBounds(ax0, TOP, ax1, bottom - 24);
        int half = (aw - 4) / 2;
        addSectionButton = addRenderableWidget(Button.builder(Component.translatable("tabtailor.sections.add_section"), b -> addSection())
                .bounds(ax0, bottom - 20, half, 20).build());
        removeSectionButton = addRenderableWidget(Button.builder(Component.translatable("tabtailor.sections.remove_section"), b -> removeSection())
                .bounds(ax0 + half + 4, bottom - 20, aw - half - 4, 20).build());
        convertButton = addRenderableWidget(Button.builder(Component.translatable("tabtailor.sections.convert"), b -> convert())
                .bounds(ax0 + 4, TOP + 40, aw - 8, 20)
                .tooltip(Tooltip.create(Component.translatable("tabtailor.sections.convert.tooltip"))).build());

        // entries column
        int y = TOP + 10;
        titleBox = new EditBox(font, bx0 + 4, y, bw - 8, 18, Component.translatable("tabtailor.sections.field.title"));
        titleBox.setMaxLength(100);
        titleBox.setResponder(v -> {
            SectionEditorModel.Section s = section();
            if (!syncing && s != null) {
                s.title = v;
                s.rawTitle = null;
                changed();
            }
        });
        addRenderableWidget(titleBox);
        y += 32;
        iconBox = new EditBox(font, bx0 + 4, y, bw - 30, 18, Component.translatable("tabtailor.sections.field.icon"));
        iconBox.setMaxLength(200);
        iconBox.setResponder(v -> {
            iconBox.setTextColor(TabEditorModel.iconValid(v) ? 0xE0E0E0 : 0xFF5555);
            SectionEditorModel.Section s = section();
            if (!syncing && s != null) {
                s.icon = v;
                changed();
            }
        });
        addRenderableWidget(iconBox);
        y += 24;
        int bhalf = (bw - 12) / 2;
        itemsButton = addRenderableWidget(Button.builder(Component.empty(), b -> setExcludeMode(false))
                .bounds(bx0 + 4, y, bhalf, 20).build());
        excludeButton = addRenderableWidget(Button.builder(Component.empty(), b -> setExcludeMode(true))
                .bounds(bx0 + 8 + bhalf, y, bw - 12 - bhalf, 20)
                .tooltip(Tooltip.create(Component.translatable("tabtailor.sections.exclude.tooltip"))).build());
        y += 24;
        styleButton = addRenderableWidget(Button.builder(Component.translatable("tabtailor.sections.style"), b -> openStyle())
                .bounds(bx0 + 4, y, bw - 8, 20)
                .tooltip(Tooltip.create(Component.translatable("tabtailor.sections.style.tooltip"))).build());
        y += 24;
        entryList.setBounds(bx0, y, bx1, bottom - 24);
        addBox = new EditBox(font, bx0 + 4, bottom - 20, bw - 32, 18, Component.translatable("tabtailor.sections.field.add"));
        addBox.setMaxLength(200);
        addBox.setHint(Component.literal("@mod, #tag, id").withStyle(ChatFormatting.DARK_GRAY));
        addRenderableWidget(addBox);
        addEntryButton = addRenderableWidget(Button.builder(Component.literal("+"), b -> addTyped())
                .bounds(bx1 - 24, bottom - 21, 20, 20)
                .tooltip(Tooltip.create(Component.translatable("tabtailor.sections.add_entry.tooltip"))).build());

        // picker / preview column
        int chalf = (cx1 - cx0 - 12) / 2;
        pickerButton = addRenderableWidget(Button.builder(Component.translatable("tabtailor.sections.picker"), b -> setPreviewMode(false))
                .bounds(cx0 + 4, TOP, chalf, 20).build());
        previewButton = addRenderableWidget(Button.builder(Component.translatable("tabtailor.sections.preview"), b -> setPreviewMode(true))
                .bounds(cx0 + 8 + chalf, TOP, cx1 - cx0 - 12 - chalf, 20).build());
        searchBox = new EditBox(font, cx0 + 4, TOP + 24, cx1 - cx0 - 8, 18, Component.translatable("tabtailor.sections.search"));
        searchBox.setMaxLength(100);
        searchBox.setHint(Component.translatable("tabtailor.sections.search.hint").withStyle(ChatFormatting.DARK_GRAY));
        searchBox.setResponder(v -> {
            pickerScroll = 0;
            refilter();
        });
        addRenderableWidget(searchBox);
        gridY0 = TOP + 46;

        // bottom
        int w = Math.min(120, (width - 4 * MARGIN) / 3);
        int x = width / 2 - (w * 3 + 2 * MARGIN) / 2;
        saveButton = addRenderableWidget(Button.builder(Component.translatable("tabtailor.editor.save"), b -> save())
                .bounds(x, height - 28, w, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("tabtailor.sections.open_file"), b -> openFile())
                .bounds(x + w + MARGIN, height - 28, w, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("tabtailor.sections.back"), b -> onClose())
                .bounds(x + 2 * (w + MARGIN), height - 28, w, 20).build());

        buildItemIndex();
        refilter();
        syncFields();
    }

    private void syncFields() {
        SectionEditorModel.Section s = section();
        syncing = true;
        titleBox.setValue(s == null ? "" : s.title);
        titleBox.setHint(Component.translatable("tabtailor.sections.no_title").withStyle(ChatFormatting.DARK_GRAY));
        iconBox.setValue(s == null ? "" : s.icon);
        iconBox.setHint(Component.translatable("tabtailor.sections.icon_hint").withStyle(ChatFormatting.DARK_GRAY));
        syncing = false;
        updateWidgets();
    }

    private void updateWidgets() {
        SectionEditorModel.Section s = section();
        boolean plain = model.plainTab;
        boolean has = s != null;
        convertButton.visible = plain;
        addSectionButton.active = !plain;
        removeSectionButton.active = !plain && has;
        titleBox.visible = has;
        iconBox.visible = has;
        itemsButton.visible = has;
        excludeButton.visible = has;
        addBox.visible = has;
        styleButton.visible = has;
        addEntryButton.visible = has;
        if (has) {
            itemsButton.setMessage(Component.translatable("tabtailor.sections.items", s.items.size())
                    .withStyle(showExclude ? ChatFormatting.GRAY : ChatFormatting.WHITE));
            excludeButton.setMessage(Component.translatable("tabtailor.sections.exclude", s.exclude.size())
                    .withStyle(showExclude ? ChatFormatting.WHITE : ChatFormatting.GRAY));
        }
        pickerButton.setMessage(Component.translatable("tabtailor.sections.picker").withStyle(showPreview ? ChatFormatting.GRAY : ChatFormatting.WHITE));
        previewButton.setMessage(Component.translatable("tabtailor.sections.preview").withStyle(showPreview ? ChatFormatting.WHITE : ChatFormatting.GRAY));
        searchBox.visible = !showPreview;
        saveButton.active = model.dirty();
    }

    // ------------------------------------------------------------------ actions

    private void setExcludeMode(boolean exclude) {
        showExclude = exclude;
        entryList.selected = currentEntries().isEmpty() ? -1 : 0;
        entryList.scroll = 0;
        updateWidgets();
    }

    private void setPreviewMode(boolean on) {
        showPreview = on;
        updateWidgets();
    }

    private void addSection() {
        int at = sectionList.selected < 0 ? model.sections.size() : sectionList.selected + 1;
        model.sections.add(at, SectionEditorModel.Section.create(Component.translatable("tabtailor.sections.new_name").getString()));
        sectionList.select(at);
        showExclude = false;
        changed();
        sectionSelected();
        setFocused(titleBox);
        titleBox.setFocused(true);
        titleBox.moveCursorToEnd(false);
        titleBox.setHighlightPos(0);
    }

    private void removeSection() {
        SectionEditorModel.Section s = section();
        if (s == null) {
            return;
        }
        Runnable remove = () -> {
            model.sections.remove(sectionList.selected);
            sectionList.select(Math.min(sectionList.selected, model.sections.size() - 1));
            changed();
            sectionSelected();
        };
        if (s.items.isEmpty() && s.exclude.isEmpty()) {
            remove.run();
            return;
        }
        minecraft.setScreen(new ConfirmScreen(yes -> {
            minecraft.setScreen(this);
            if (yes) {
                remove.run();
            }
        }, Component.translatable("tabtailor.sections.remove_confirm.title", s.title.isBlank() ? "?" : s.title),
                Component.translatable("tabtailor.sections.remove_confirm.message", s.items.size())));
    }

    private void openStyle() {
        SectionEditorModel.Section s = section();
        if (s == null) {
            return;
        }
        Component name = Component.literal(s.title.isBlank() ? Component.translatable("tabtailor.sections.no_title").getString() : s.title);
        minecraft.setScreen(new StyleEditorScreen(this, StyleEditorScreen.Scope.SECTION, model.tabId, name, s.extra,
                this::changed, iconStack(s, sectionList.selected)));
    }

    private void convert() {
        model.convertToSections(tabTitle.getString());
        sectionList.select(0);
        changed();
        sectionSelected();
    }

    private void addEntry(String entry) {
        SectionEditorModel.Section s = section();
        if (s == null) {
            return;
        }
        String trimmed = entry.trim();
        if (trimmed.isEmpty()) {
            return;
        }
        String error = SectionEditorModel.validate(trimmed);
        if (error != null) {
            setStatus(Component.literal(error), 0xFFFF5555);
            return;
        }
        List<String> list = currentEntries();
        if (list.contains(trimmed)) {
            entryList.select(list.indexOf(trimmed));
            setStatus(Component.translatable("tabtailor.sections.status.exists", trimmed), C_DIM);
            return;
        }
        list.add(trimmed);
        entryList.select(list.size() - 1);
        changed();
        setStatus(Component.translatable(showExclude ? "tabtailor.sections.status.excluded" : "tabtailor.sections.status.added", trimmed), 0xFF55FF55);
    }

    private void addTyped() {
        addEntry(addBox.getValue());
        if (status.getString().isEmpty() || statusColor != 0xFFFF5555) {
            addBox.setValue("");
        }
    }

    private void removeEntry(int index) {
        List<String> list = currentEntries();
        if (index < 0 || index >= list.size()) {
            return;
        }
        list.remove(index);
        entryList.select(Math.min(index, list.size() - 1));
        changed();
    }

    private void save() {
        SectionEditorModel.SaveResult result = model.save();
        if (!result.ok()) {
            String[] parts = result.message().split("\\|", 2);
            setStatus(Component.translatable(parts[0], parts.length > 1 ? parts[1] : ""), 0xFFFF5555);
            return;
        }
        sectionList.select(Math.max(0, sectionList.selected));
        revision++;
        syncFields();
        setStatus(result.problems() == 0 ? Component.translatable("tabtailor.editor.status.saved")
                : Component.translatable("tabtailor.editor.status.saved_problems", result.problems()),
                result.problems() == 0 ? 0xFF55FF55 : 0xFFFFAA00);
    }

    private void openFile() {
        try {
            Util.getPlatform().openFile(TabEditorModel.tabFile(model.tabId, io.github.definitivtim.tabtailor.config.ConfigManager.get(), true).toFile());
        } catch (Exception e) {
            setStatus(Component.translatable("tabtailor.editor.error.save", String.valueOf(e.getMessage())), 0xFFFF5555);
        }
    }

    private void setStatus(Component text, int color) {
        status = text;
        statusColor = color;
    }

    @Override
    public void onClose() {
        if (!model.dirty()) {
            minecraft.setScreen(parent);
            return;
        }
        minecraft.setScreen(new ConfirmScreen(yes -> minecraft.setScreen(yes ? parent : this),
                Component.translatable("tabtailor.editor.discard.title"), Component.translatable("tabtailor.editor.discard.message")));
    }

    // ------------------------------------------------------------------ item index and icons

    private static void buildItemIndex() {
        if (allItems != null) {
            return;
        }
        List<ItemStack> list = new ArrayList<>();
        Map<Item, String> text = new IdentityHashMap<>();
        for (Item item : BuiltInRegistries.ITEM) {
            if (item == Items.AIR) {
                continue;
            }
            ItemStack stack = new ItemStack(item);
            list.add(stack);
            String name;
            try {
                name = stack.getHoverName().getString();
            } catch (Throwable t) {
                name = "";
            }
            text.put(item, (name + " " + BuiltInRegistries.ITEM.getKey(item)).toLowerCase(Locale.ROOT));
        }
        allItems = list;
        searchText = text;
    }

    private void refilter() {
        String q = searchBox == null ? "" : searchBox.getValue().trim().toLowerCase(Locale.ROOT);
        if (q.isEmpty()) {
            filtered = allItems;
            return;
        }
        List<ItemStack> out = new ArrayList<>();
        if (q.startsWith("@")) {
            String ns = q.substring(1);
            for (ItemStack stack : allItems) {
                if (BuiltInRegistries.ITEM.getKey(stack.getItem()).getNamespace().startsWith(ns)) {
                    out.add(stack);
                }
            }
        } else if (q.startsWith("#")) {
            String tag = q.substring(1);
            for (ItemStack stack : allItems) {
                if (stack.getItem().builtInRegistryHolder().tags().anyMatch(t -> t.location().toString().contains(tag))) {
                    out.add(stack);
                }
            }
        } else {
            for (ItemStack stack : allItems) {
                if (searchText.getOrDefault(stack.getItem(), "").contains(q)) {
                    out.add(stack);
                }
            }
        }
        filtered = out;
    }

    private ItemStack entryIcon(String entry) {
        return entryIcons.computeIfAbsent(entry, this::computeEntryIcon);
    }

    private ItemStack computeEntryIcon(String entry) {
        try {
            Selector sel = Selector.parse(entry);
            if (sel instanceof Selector.Not) {
                return new ItemStack(Items.BARRIER);
            }
            if (sel instanceof Selector.Exact exact) {
                Item item = BuiltInRegistries.ITEM.getOptional(exact.id()).orElse(Items.AIR);
                return item == Items.AIR ? new ItemStack(Items.BARRIER) : new ItemStack(item);
            }
            if (sel instanceof Selector.Tab t) {
                CreativeModeTab other = BuiltInRegistries.CREATIVE_MODE_TAB.get(t.tabId());
                return other == null ? new ItemStack(Items.BARRIER) : other.getIconItem();
            }
            if (sel instanceof Selector.Rest) {
                return tab.getIconItem();
            }
            if (sel instanceof Selector.Tag tag) {
                for (Holder<Item> holder : BuiltInRegistries.ITEM.getTagOrEmpty(tag.tag())) {
                    return new ItemStack(holder.value());
                }
                return new ItemStack(Items.BARRIER);
            }
            // @mod and wildcards: first matching item
            for (ItemStack stack : allItems) {
                if (sel.matches(stack, null, null)) {
                    return stack;
                }
            }
        } catch (Throwable ignored) {
            // fall through
        }
        return new ItemStack(Items.BARRIER);
    }

    // ------------------------------------------------------------------ preview

    private void updatePreview() {
        if (previewRevision == revision && preview != null) {
            return;
        }
        long now = Util.getMillis();
        if (preview != null && now - previewTime < 200) {
            return;
        }
        previewTime = now;
        previewRevision = revision;
        try {
            preview = model.preview();
        } catch (Throwable t) {
            preview = null;
        }
        inTab.clear();
        inSection.clear();
        if (preview != null) {
            for (int b = 0; b < preview.blocks().size(); b++) {
                for (ItemStack stack : preview.blocks().get(b).items()) {
                    inTab.add(stack.getItem());
                    if (b == sectionList.selected) {
                        inSection.add(stack.getItem());
                    }
                }
            }
        }
    }

    private int sectionCount(int index) {
        if (preview == null || model.plainTab || index >= preview.blocks().size()) {
            return -1;
        }
        return preview.blocks().get(index).items().size();
    }

    // ------------------------------------------------------------------ input

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (super.mouseClicked(mx, my, button)) {
            return true;
        }
        if (button == 0 && !model.plainTab && sectionList.press(mx, my) >= 0) {
            unfocusFields();
            return true;
        }
        if (button == 0 && entryList.inside(mx, my)) {
            int index = entryList.rowAt(my);
            if (index >= 0 && mx >= entryList.contentRight() - 14) {
                removeEntry(index);
                return true;
            }
            if (entryList.press(mx, my) >= 0) {
                unfocusFields();
                return true;
            }
        }
        if (!showPreview && section() != null) {
            ItemStack stack = pickerAt(mx, my);
            if (stack != null) {
                ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
                if (button == 0) {
                    addEntry(id.toString());
                    return true;
                }
                if (button == 1) {
                    addEntry("@" + id.getNamespace());
                    return true;
                }
            }
        }
        return false;
    }

    private void unfocusFields() {
        setFocused(null);
        titleBox.setFocused(false);
        iconBox.setFocused(false);
        addBox.setFocused(false);
        searchBox.setFocused(false);
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (button == 0 && (sectionList.drag(my) || entryList.drag(my))) {
            return true;
        }
        return super.mouseDragged(mx, my, button, dx, dy);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        boolean a = sectionList.release();
        boolean b = entryList.release();
        if (a || b) {
            return true;
        }
        return super.mouseReleased(mx, my, button);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double sx, double sy) {
        if (sectionList.scroll(mx, my, sy) || entryList.scroll(mx, my, sy)) {
            return true;
        }
        if (mx >= cx0 && mx < cx1 && my >= gridY0 && my < bottom) {
            if (showPreview) {
                previewScroll = Math.max(0, previewScroll - sy * CELL * 2);
            } else {
                pickerScroll = Math.max(0, pickerScroll - sy * CELL * 2);
            }
            return true;
        }
        return super.mouseScrolled(mx, my, sx, sy);
    }

    @Override
    public boolean keyPressed(int key, int scan, int modifiers) {
        if (addBox.isFocused() && (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER)) {
            addTyped();
            return true;
        }
        boolean typing = titleBox.isFocused() || iconBox.isFocused() || addBox.isFocused() || searchBox.isFocused();
        if (!typing && key == GLFW.GLFW_KEY_DELETE && entryList.selected >= 0) {
            removeEntry(entryList.selected);
            return true;
        }
        if (key == GLFW.GLFW_KEY_S && (modifiers & GLFW.GLFW_MOD_CONTROL) != 0) {
            if (model.dirty()) {
                save();
            }
            return true;
        }
        return super.keyPressed(key, scan, modifiers);
    }

    // ------------------------------------------------------------------ rendering

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.renderBackground(g, mouseX, mouseY, partialTick);
        panel(g, ax0 - 2, TOP - 2, ax1 + 2, bottom + 2);
        panel(g, bx0 - 2, TOP - 2, bx1 + 2, bottom + 2);
        panel(g, cx0 - 2, TOP - 2, cx1 + 2, bottom + 2);
    }

    private static void panel(GuiGraphics g, int x0, int y0, int x1, int y1) {
        g.fill(x0, y0, x1, y1, C_PANEL);
        g.renderOutline(x0, y0, x1 - x0, y1 - y0, C_PANEL_EDGE);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        updatePreview();
        super.render(g, mouseX, mouseY, partialTick);
        g.drawCenteredString(font, title, width / 2, 9, C_TEXT);
        g.drawString(font, Component.translatable("tabtailor.sections.column.sections"), ax0, TOP - 12, C_DIM, false);
        g.drawString(font, Component.translatable("tabtailor.sections.column.entries"), bx0, TOP - 12, C_DIM, false);

        if (model.plainTab) {
            List<net.minecraft.util.FormattedCharSequence> lines = font.split(Component.translatable("tabtailor.sections.plain"), ax1 - ax0 - 8);
            int y = TOP + 4;
            for (var line : lines) {
                g.drawString(font, line, ax0 + 4, y, C_DIM, false);
                y += 10;
            }
        } else {
            sectionList.render(g, mouseX, mouseY);
        }

        SectionEditorModel.Section s = section();
        if (s != null) {
            g.drawString(font, Component.translatable("tabtailor.sections.field.title"), bx0 + 4, titleBox.getY() - 10, C_DIM, false);
            g.drawString(font, Component.translatable("tabtailor.sections.field.icon"), bx0 + 4, iconBox.getY() - 10, C_DIM, false);
            int ix = iconBox.getX() + iconBox.getWidth() + 4;
            g.fill(ix, iconBox.getY(), ix + 18, iconBox.getY() + 18, 0xFF8B8B8B);
            ItemStack icon = iconStack(s, sectionList.selected);
            g.renderItem(icon, ix + 1, iconBox.getY() + 1);
            entryList.render(g, mouseX, mouseY);
            if (currentEntries().isEmpty()) {
                List<net.minecraft.util.FormattedCharSequence> lines = font.split(
                        Component.translatable(showExclude ? "tabtailor.sections.empty_exclude" : "tabtailor.sections.empty_items"), bx1 - bx0 - 12);
                int y = entryList.y0 + 6;
                for (var line : lines) {
                    g.drawString(font, line, bx0 + 6, y, C_FAINT, false);
                    y += 10;
                }
            }
        }

        if (showPreview) {
            renderPreview(g, mouseX, mouseY);
        } else {
            renderPicker(g, mouseX, mouseY);
        }

        if (!status.getString().isEmpty()) {
            g.drawCenteredString(font, status, width / 2, height - 40, statusColor);
        }
    }

    private ItemStack iconStack(SectionEditorModel.Section s, int index) {
        Item item = TabEditorModel.iconItem(s.icon);
        if (item != null) {
            return new ItemStack(item);
        }
        if (preview != null && index >= 0 && index < preview.blocks().size() && !preview.blocks().get(index).items().isEmpty()) {
            return preview.blocks().get(index).items().get(0);
        }
        return s.items.isEmpty() ? ItemStack.EMPTY : entryIcon(s.items.get(0));
    }

    private void renderSectionRow(GuiGraphics g, int index, int x0, int y, int x1, int h, boolean selected, boolean hovered, int mx, int my) {
        SectionEditorModel.Section s = model.sections.get(index);
        DragList.handle(g, x0 + 4, y, h);
        g.renderItem(iconStack(s, index), x0 + 14, y + 2);
        int count = sectionCount(index);
        String countText = count < 0 ? "" : String.valueOf(count);
        int countW = font.width(countText);
        String name = s.title.isBlank() ? Component.translatable("tabtailor.sections.no_title").getString() : s.title;
        int color = s.title.isBlank() ? C_DIM : C_TEXT;
        g.drawString(font, font.plainSubstrByWidth(name, x1 - x0 - 38 - countW - 4), x0 + 34, y + 6, color, false);
        g.drawString(font, countText, x1 - 4 - countW, y + 6, count == 0 ? 0xFFFF6B6B : C_FAINT, false);
    }

    private void renderEntryRow(GuiGraphics g, int index, int x0, int y, int x1, int h, boolean selected, boolean hovered, int mx, int my) {
        String entry = currentEntries().get(index);
        DragList.handle(g, x0 + 4, y, h);
        g.renderItem(entryIcon(entry), x0 + 14, y + 1);
        boolean valid = SectionEditorModel.validate(entry) == null;
        g.drawString(font, font.plainSubstrByWidth(entry, x1 - x0 - 34 - 16), x0 + 34, y + 5, valid ? entryColor(entry) : 0xFFFF5555, false);
        // remove button
        int bx = x1 - 14;
        boolean over = hovered && mx >= bx;
        g.blitSprite(over ? BUTTON_HIGHLIGHTED : BUTTON, bx, y + 2, 12, 14);
        g.drawCenteredString(font, "x", bx + 6, y + 5, over ? 0xFFFF6B6B : C_TEXT);
    }

    private static int entryColor(String entry) {
        if (entry.startsWith("@")) {
            return 0xFF8FD3FF;
        }
        if (entry.startsWith("#")) {
            return 0xFFB8F28A;
        }
        if (entry.startsWith("tab:") || entry.equals("*")) {
            return 0xFFFFD27F;
        }
        if (entry.startsWith("!")) {
            return 0xFFFF9F9F;
        }
        return C_TEXT;
    }

    private int columns() {
        return Math.max(1, (cx1 - cx0 - 14) / CELL);
    }

    private @Nullable ItemStack pickerAt(double mx, double my) {
        if (showPreview || mx < cx0 + 4 || my < gridY0 || my >= bottom - 2) {
            return null;
        }
        int col = (int) ((mx - cx0 - 4) / CELL);
        if (col >= columns()) {
            return null;
        }
        int row = (int) ((my - gridY0 + pickerScroll) / CELL);
        int index = row * columns() + col;
        return index >= 0 && index < filtered.size() ? filtered.get(index) : null;
    }

    private void renderPicker(GuiGraphics g, int mouseX, int mouseY) {
        int cols = columns();
        int rows = (filtered.size() + cols - 1) / cols;
        int viewH = bottom - 2 - gridY0;
        double max = Math.max(0, rows * CELL - viewH);
        pickerScroll = Mth.clamp(pickerScroll, 0, max);
        g.enableScissor(cx0, gridY0, cx1, bottom - 2);
        int firstRow = (int) (pickerScroll / CELL);
        int lastRow = Math.min(rows - 1, firstRow + viewH / CELL + 1);
        ItemStack hovered = null;
        for (int r = firstRow; r <= lastRow; r++) {
            for (int c = 0; c < cols; c++) {
                int i = r * cols + c;
                if (i >= filtered.size()) {
                    break;
                }
                ItemStack stack = filtered.get(i);
                int x = cx0 + 4 + c * CELL;
                int y = gridY0 + r * CELL - (int) pickerScroll;
                boolean over = mouseX >= x && mouseX < x + CELL && mouseY >= y && mouseY < y + CELL && mouseY >= gridY0 && mouseY < bottom - 2;
                if (inSection.contains(stack.getItem())) {
                    g.fill(x, y, x + CELL, y + CELL, 0x5533AA33);
                } else if (inTab.contains(stack.getItem())) {
                    g.fill(x, y, x + CELL, y + CELL, 0x33FFFFFF);
                }
                if (over) {
                    g.fill(x, y, x + CELL, y + CELL, 0x80FFFFFF);
                    hovered = stack;
                }
                g.renderItem(stack, x + 1, y + 1);
            }
        }
        g.disableScissor();
        if (max > 0) {
            int track = viewH;
            int bar = Math.max(12, (int) (track * (double) track / (track + max)));
            int barY = gridY0 + (int) ((track - bar) * (pickerScroll / max));
            g.fill(cx1 - 5, gridY0, cx1 - 1, bottom - 2, 0x40000000);
            g.fill(cx1 - 5, barY, cx1 - 1, barY + bar, 0xFF8B8B8B);
        }
        if (filtered.isEmpty()) {
            g.drawString(font, Component.translatable("tabtailor.sections.nothing_found"), cx0 + 6, gridY0 + 4, C_FAINT, false);
        }
        if (hovered != null) {
            ResourceLocation id = BuiltInRegistries.ITEM.getKey(hovered.getItem());
            List<Component> lines = new ArrayList<>();
            lines.add(hovered.getHoverName());
            lines.add(Component.literal(id.toString()).withStyle(ChatFormatting.DARK_GRAY));
            if (section() != null) {
                lines.add(Component.translatable(showExclude ? "tabtailor.sections.picker.left_exclude" : "tabtailor.sections.picker.left").withStyle(ChatFormatting.GRAY));
                lines.add(Component.translatable("tabtailor.sections.picker.right", "@" + id.getNamespace()).withStyle(ChatFormatting.GRAY));
            }
            if (inSection.contains(hovered.getItem())) {
                lines.add(Component.translatable("tabtailor.sections.picker.in_section").withStyle(ChatFormatting.GREEN));
            } else if (inTab.contains(hovered.getItem())) {
                lines.add(Component.translatable("tabtailor.sections.picker.in_tab").withStyle(ChatFormatting.YELLOW));
            }
            g.renderComponentTooltip(font, lines, mouseX, mouseY);
        }
    }

    private void renderPreview(GuiGraphics g, int mouseX, int mouseY) {
        int top = TOP + 24;
        if (preview == null) {
            List<net.minecraft.util.FormattedCharSequence> lines = font.split(Component.translatable("tabtailor.sections.preview.none"), cx1 - cx0 - 12);
            int y = top + 4;
            for (var line : lines) {
                g.drawString(font, line, cx0 + 6, y, C_FAINT, false);
                y += 10;
            }
            return;
        }
        int cols = columns();
        int problemsH = Math.min(3, preview.problems().size()) * 10;
        int viewBottom = bottom - 2 - problemsH;
        // total height
        int total = 0;
        for (TabContentProcessor.PreviewBlock block : preview.blocks()) {
            if (block.items().isEmpty()) {
                continue;
            }
            total += (block.title() != null ? 14 : 0) + ((block.items().size() + cols - 1) / cols) * CELL + 4;
        }
        double max = Math.max(0, total - (viewBottom - top));
        previewScroll = Mth.clamp(previewScroll, 0, max);
        g.enableScissor(cx0, top, cx1, viewBottom);
        int y = top - (int) previewScroll;
        ItemStack hovered = null;
        int selectedBlock = sectionList.selected;
        for (int b = 0; b < preview.blocks().size(); b++) {
            TabContentProcessor.PreviewBlock block = preview.blocks().get(b);
            if (block.items().isEmpty()) {
                continue;
            }
            if (block.title() != null) {
                g.fill(cx0 + 4, y, cx0 + 4 + cols * CELL, y + 12, b == selectedBlock ? 0xC0505050 : C_HEADER);
                if (b == selectedBlock) {
                    g.fill(cx0 + 4, y + 11, cx0 + 4 + cols * CELL, y + 12, DragList.C_ACCENT);
                }
                g.drawString(font, block.title(), cx0 + 7, y + 2, C_TEXT, false);
                y += 14;
            }
            for (int i = 0; i < block.items().size(); i++) {
                int x = cx0 + 4 + (i % cols) * CELL;
                int iy = y + (i / cols) * CELL;
                if (iy + CELL < top || iy > viewBottom) {
                    continue;
                }
                ItemStack stack = block.items().get(i);
                boolean over = mouseX >= x && mouseX < x + CELL && mouseY >= iy && mouseY < iy + CELL && mouseY >= top && mouseY < viewBottom;
                if (over) {
                    g.fill(x, iy, x + CELL, iy + CELL, 0x80FFFFFF);
                    hovered = stack;
                }
                g.renderItem(stack, x + 1, iy + 1);
            }
            y += ((block.items().size() + cols - 1) / cols) * CELL + 4;
        }
        g.disableScissor();
        if (total == 0) {
            g.drawString(font, Component.translatable("tabtailor.sections.preview.empty"), cx0 + 6, top + 4, C_FAINT, false);
        }
        int py = viewBottom + 1;
        for (int i = 0; i < Math.min(3, preview.problems().size()); i++) {
            g.drawString(font, font.plainSubstrByWidth(preview.problems().get(i), cx1 - cx0 - 10), cx0 + 5, py, 0xFFFF6B6B, false);
            py += 10;
        }
        if (hovered != null) {
            g.renderComponentTooltip(font, List.of(hovered.getHoverName(),
                    Component.literal(BuiltInRegistries.ITEM.getKey(hovered.getItem()).toString()).withStyle(ChatFormatting.DARK_GRAY)), mouseX, mouseY);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return true;
    }
}
