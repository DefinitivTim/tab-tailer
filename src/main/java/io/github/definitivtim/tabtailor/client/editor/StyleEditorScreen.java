package io.github.definitivtim.tabtailor.client.editor;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import io.github.definitivtim.tabtailor.TabTailor;
import io.github.definitivtim.tabtailor.client.SectionScreenHelper;
import io.github.definitivtim.tabtailor.client.TabLooks;
import io.github.definitivtim.tabtailor.client.TabTailorCommands;
import io.github.definitivtim.tabtailor.config.ConfigManager;
import io.github.definitivtim.tabtailor.config.SectionStyle;
import io.github.definitivtim.tabtailor.config.SidebarStyle;
import io.github.definitivtim.tabtailor.config.TabDefinition;
import io.github.definitivtim.tabtailor.config.TabLook;
import io.github.definitivtim.tabtailor.config.TabTailorConfig;
import io.github.definitivtim.tabtailor.core.SectionLayout;
import io.github.definitivtim.tabtailor.core.TabContentProcessor;
import net.minecraft.ChatFormatting;
import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.lwjgl.glfw.GLFW;

import javax.annotation.Nullable;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Stage 3 of the editor: colors and images of section headers, the side bar and the creative window, for all tabs
 * (tabtailor.json), one tab (its file) or one section (inside the section editor). Unset values are inherited.
 */
public final class StyleEditorScreen extends Screen {
    public enum Scope { GLOBAL, TAB, SECTION }

    enum Group { HEADER, SIDEBAR, WINDOW }

    enum Type { COLOR, BOOL, INT, HEADER_IMAGE, WINDOW_IMAGE, SCROLLER }

    record Prop(Group group, String key, Type type) {
        String label() {
            return "tabtailor.style.key." + key;
        }
    }

    private static final List<Prop> PROPS = List.of(
            new Prop(Group.HEADER, "background", Type.COLOR),
            new Prop(Group.HEADER, "color", Type.COLOR),
            new Prop(Group.HEADER, "line", Type.COLOR),
            new Prop(Group.HEADER, "shadow", Type.BOOL),
            new Prop(Group.HEADER, "image", Type.HEADER_IMAGE),
            new Prop(Group.HEADER, "image_frames", Type.INT),
            new Prop(Group.HEADER, "image_frametime", Type.INT),
            new Prop(Group.HEADER, "image_hover_only", Type.BOOL),
            new Prop(Group.SIDEBAR, "background", Type.COLOR),
            new Prop(Group.SIDEBAR, "slot", Type.COLOR),
            new Prop(Group.SIDEBAR, "background_active", Type.COLOR),
            new Prop(Group.SIDEBAR, "border_active", Type.COLOR),
            new Prop(Group.SIDEBAR, "button", Type.COLOR),
            new Prop(Group.SIDEBAR, "button_active", Type.COLOR),
            new Prop(Group.SIDEBAR, "arrow", Type.COLOR),
            new Prop(Group.SIDEBAR, "arrow_active", Type.COLOR),
            new Prop(Group.WINDOW, "background", Type.WINDOW_IMAGE),
            new Prop(Group.WINDOW, "label_color", Type.COLOR),
            new Prop(Group.WINDOW, "slot_highlight", Type.COLOR),
            new Prop(Group.WINDOW, "scroller", Type.SCROLLER),
            new Prop(Group.WINDOW, "keep_mod_colors", Type.BOOL));

    private static final Map<Group, String> FILE_KEYS = Map.of(Group.HEADER, "section_style", Group.SIDEBAR, "sidebar", Group.WINDOW, "tab_style");
    private static final int MARGIN = 8;
    private static final int TOP = 26;
    private static final int C_PANEL = 0xB0101010;
    private static final int C_PANEL_EDGE = 0xFF3A3A3A;
    private static final int C_TEXT = 0xFFFFFFFF;
    private static final int C_DIM = 0xFF9A9A9A;
    private static final int C_FAINT = 0xFF5E5E5E;
    private static final ResourceLocation VANILLA_SCROLLER = ResourceLocation.withDefaultNamespace("container/creative_inventory/scroller");

    private final Screen parent;
    private final Scope scope;
    private final @Nullable ResourceLocation tabId;
    private final Component targetName;
    private final @Nullable JsonObject sectionTarget;
    private final @Nullable Runnable onApply;
    private final ItemStack sampleIcon;

    private final Map<Group, JsonObject> objects = new EnumMap<>(Group.class);
    private final Map<Group, String> saved = new EnumMap<>(Group.class);
    private final List<Prop> props = new ArrayList<>();
    private final List<Object> rows = new ArrayList<>(); // Group headers and Props
    private DragList list;
    private Prop selected;

    private ColorPicker picker;
    private EditBox valueBox;
    private Button inheritButton;
    private Button cycleButton;
    private Button folderButton;
    private DragList choiceList;
    private final List<String> choices = new ArrayList<>();
    private boolean syncing;

    private Component status = Component.empty();
    private int statusColor = C_DIM;

    private int lx1;
    private int rx0;
    private int rx1;
    private int bottom;
    private int previewY;

    public StyleEditorScreen(Screen parent, Scope scope, @Nullable ResourceLocation tabId, Component targetName,
                             @Nullable JsonObject sectionTarget, @Nullable Runnable onApply, ItemStack sampleIcon) {
        super(Component.translatable("tabtailor.style.title." + scope.name().toLowerCase(Locale.ROOT), targetName));
        this.parent = parent;
        this.scope = scope;
        this.tabId = tabId;
        this.targetName = targetName;
        this.sectionTarget = sectionTarget;
        this.onApply = onApply;
        this.sampleIcon = sampleIcon.isEmpty() ? new ItemStack(Items.CRAFTING_TABLE) : sampleIcon;
        load();
        Group last = null;
        for (Prop p : PROPS) {
            if (scope == Scope.SECTION && p.group() != Group.HEADER) {
                continue;
            }
            if (p.group() != last) {
                rows.add(p.group());
                last = p.group();
            }
            rows.add(p);
            props.add(p);
        }
        selected = props.get(0);
        list = new DragList(16, rows::size, (a, b) -> { }, this::renderRow);
        list.selected = rows.indexOf(selected);
        list.onChange = this::rowChosen;
        choiceList = new DragList(14, choices::size, (a, b) -> { }, this::renderChoice);
        choiceList.onChange = this::choiceChosen;
    }

    // ------------------------------------------------------------------ data

    private void load() {
        objects.clear();
        if (scope == Scope.SECTION) {
            JsonObject o = new JsonObject();
            if (sectionTarget != null) {
                for (Prop p : PROPS) {
                    if (p.group() == Group.HEADER && sectionTarget.has(p.key())) {
                        o.add(p.key(), sectionTarget.get(p.key()).deepCopy());
                    }
                }
                if (sectionTarget.has("sprite")) {
                    o.add("sprite", sectionTarget.get("sprite").deepCopy());
                }
            }
            objects.put(Group.HEADER, o);
        } else {
            JsonObject root = new JsonObject();
            try {
                Path file = file(false);
                if (file != null && Files.isRegularFile(file)) {
                    root = ConfigManager.readJson(file);
                }
            } catch (Exception e) {
                setStatus(Component.translatable("tabtailor.sections.error.load", String.valueOf(e.getMessage())), 0xFFFF5555);
            }
            for (Group group : Group.values()) {
                JsonElement el = root.get(FILE_KEYS.get(group));
                objects.put(group, el != null && el.isJsonObject() ? el.getAsJsonObject().deepCopy() : new JsonObject());
            }
        }
        saved.clear();
        objects.forEach((g, o) -> saved.put(g, o.toString()));
    }

    private @Nullable Path file(boolean create) throws java.io.IOException {
        if (scope == Scope.GLOBAL) {
            return ConfigManager.root().resolve("tabtailor.json");
        }
        if (scope == Scope.TAB && tabId != null) {
            return TabEditorModel.tabFile(tabId, ConfigManager.get(), create);
        }
        return null;
    }

    private boolean dirty() {
        for (Map.Entry<Group, JsonObject> e : objects.entrySet()) {
            if (!e.getValue().toString().equals(saved.get(e.getKey()))) {
                return true;
            }
        }
        return false;
    }

    private JsonObject object(Prop p) {
        return objects.get(p.group());
    }

    private boolean isSet(Prop p) {
        return object(p).has(p.key());
    }

    private @Nullable String raw(Prop p) {
        JsonElement el = object(p).get(p.key());
        if (el == null || el.isJsonNull()) {
            return null;
        }
        return el.isJsonPrimitive() ? el.getAsString() : el.toString();
    }

    private void set(Prop p, @Nullable Object value) {
        JsonObject o = object(p);
        if (value == null) {
            o.remove(p.key());
        } else if (value instanceof Boolean b) {
            o.addProperty(p.key(), b);
        } else if (value instanceof Integer i) {
            o.addProperty(p.key(), i);
        } else {
            o.addProperty(p.key(), value.toString());
        }
        status = Component.empty();
        updateWidgets();
    }

    // parents: what applies when a value is not set here

    private SectionStyle headerParent() {
        TabTailorConfig config = ConfigManager.get();
        return switch (scope) {
            case GLOBAL -> SectionStyle.DEFAULT;
            case TAB -> config.sectionStyle();
            case SECTION -> {
                TabDefinition def = tabId == null ? null : config.tab(tabId);
                yield def == null ? config.sectionStyle() : def.sectionStyle().inherit(config.sectionStyle());
            }
        };
    }

    private SidebarStyle sidebarParent() {
        return scope == Scope.GLOBAL ? SidebarStyle.DEFAULT : ConfigManager.get().sidebarStyle().resolved();
    }

    private TabLook windowParent() {
        return scope == Scope.GLOBAL ? TabLook.DEFAULT : ConfigManager.get().tabLook().inherit(TabLook.DEFAULT);
    }

    private SectionStyle headerStyle() {
        return ConfigManager.parseSectionStyle(objects.get(Group.HEADER), new ArrayList<>()).inherit(headerParent()).inherit(SectionStyle.DEFAULT);
    }

    private SidebarStyle sidebarStyle() {
        JsonObject o = objects.get(Group.SIDEBAR);
        return (o == null ? SidebarStyle.EMPTY : ConfigManager.parseSidebar(o, new ArrayList<>())).inherit(sidebarParent()).resolved();
    }

    private TabLook windowLook() {
        JsonObject o = objects.get(Group.WINDOW);
        return (o == null ? TabLook.EMPTY : ConfigManager.parseTabLook(o, new ArrayList<>())).inherit(windowParent()).inherit(TabLook.DEFAULT);
    }

    /** Effective value of a property as text (set here or inherited). */
    private String effective(Prop p) {
        Object v = switch (p.group()) {
            case HEADER -> {
                SectionStyle s = headerStyle();
                yield switch (p.key()) {
                    case "background" -> s.background();
                    case "color" -> s.color();
                    case "line" -> s.line();
                    case "shadow" -> s.shadow();
                    case "image" -> s.image();
                    case "image_frames" -> s.imageFrames();
                    case "image_frametime" -> s.imageFrametime();
                    case "image_hover_only" -> s.imageHoverOnly();
                    default -> null;
                };
            }
            case SIDEBAR -> {
                SidebarStyle s = sidebarStyle();
                yield switch (p.key()) {
                    case "background" -> s.background();
                    case "slot" -> s.slot();
                    case "background_active" -> s.backgroundActive();
                    case "border_active" -> s.borderActive();
                    case "button" -> s.button();
                    case "button_active" -> s.buttonActive();
                    case "arrow" -> s.arrow();
                    case "arrow_active" -> s.arrowActive();
                    default -> null;
                };
            }
            case WINDOW -> {
                TabLook l = windowLook();
                yield switch (p.key()) {
                    case "background" -> l.background();
                    case "label_color" -> l.labelColor() != null ? l.labelColor() : TabLook.VANILLA_LABEL;
                    case "slot_highlight" -> l.slotHighlight() != null ? l.slotHighlight() : TabLook.VANILLA_SLOT_HIGHLIGHT;
                    case "scroller" -> l.scroller();
                    case "keep_mod_colors" -> l.keepModColors();
                    default -> null;
                };
            }
        };
        if (v == null) {
            return p.type() == Type.HEADER_IMAGE ? Component.translatable("tabtailor.style.none").getString()
                    : p.type() == Type.INT ? Component.translatable("tabtailor.style.auto").getString() : "-";
        }
        if (p.type() == Type.COLOR && v instanceof Integer i) {
            return ColorPicker.hex(i);
        }
        if (v instanceof Boolean b) {
            return Component.translatable(b ? "tabtailor.style.on" : "tabtailor.style.off").getString();
        }
        return v.toString();
    }

    private int effectiveColor(Prop p) {
        Integer c = ColorPicker.parse(effective(p));
        return c == null ? 0xFF000000 : c;
    }

    // ------------------------------------------------------------------ layout

    @Override
    protected void init() {
        bottom = height - 46;
        int lw = Mth.clamp(width * 30 / 100, 150, 210);
        lx1 = MARGIN + lw;
        rx0 = lx1 + MARGIN;
        rx1 = width - MARGIN;
        list.setBounds(MARGIN, TOP, lx1, bottom);

        int y = TOP + 14;
        picker = new ColorPicker(color -> {
            if (!syncing && selected.type() == Type.COLOR) {
                set(selected, ColorPicker.hex(color));
                syncing = true;
                valueBox.setValue(ColorPicker.hex(color));
                syncing = false;
            }
        });
        picker.setPosition(rx0 + 6, y);
        int fx = rx0 + 6 + ColorPicker.WIDTH + 10;
        int fw = Math.max(80, rx1 - 6 - fx);
        valueBox = new EditBox(font, fx, y, fw, 18, Component.translatable("tabtailor.style.value"));
        valueBox.setMaxLength(200);
        valueBox.setResponder(this::valueTyped);
        addRenderableWidget(valueBox);
        inheritButton = addRenderableWidget(Button.builder(Component.translatable("tabtailor.style.inherit"), b -> {
            set(selected, null);
            syncFields();
        }).bounds(fx, y + 22, fw, 20).build());
        cycleButton = addRenderableWidget(Button.builder(Component.empty(), b -> cycleBool())
                .bounds(fx, y + 46, fw, 20).build());
        folderButton = addRenderableWidget(Button.builder(Component.translatable("tabtailor.style.images_folder"), b -> openImages())
                .bounds(fx, y + 46, fw, 20).build());
        choiceList.setBounds(rx0 + 6, y, fx - 8, y + ColorPicker.SIZE + 4);
        previewY = y + ColorPicker.SIZE + 16;

        int w = Math.min(120, (width - 4 * MARGIN) / 2);
        int x = width / 2 - (w * 2 + MARGIN) / 2;
        addRenderableWidget(Button.builder(Component.translatable(scope == Scope.SECTION ? "tabtailor.style.apply" : "tabtailor.editor.save"),
                b -> save()).bounds(x, height - 28, w, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("tabtailor.sections.back"), b -> onClose())
                .bounds(x + w + MARGIN, height - 28, w, 20).build());
        syncFields();
    }

    private void rowChosen() {
        int i = list.selected;
        if (i >= 0 && i < rows.size() && rows.get(i) instanceof Prop p) {
            selected = p;
        } else {
            list.selected = rows.indexOf(selected);
        }
        syncFields();
    }

    private void syncFields() {
        syncing = true;
        Prop p = selected;
        String raw = raw(p);
        valueBox.setValue(raw == null ? "" : raw);
        valueBox.setHint(Component.literal(effective(p)).withStyle(ChatFormatting.DARK_GRAY));
        if (p.type() == Type.COLOR) {
            picker.setColor(effectiveColor(p));
        }
        choices.clear();
        if (p.type() == Type.HEADER_IMAGE || p.type() == Type.WINDOW_IMAGE || p.type() == Type.SCROLLER) {
            if (p.type() != Type.HEADER_IMAGE) {
                choices.add("vanilla");
                choices.add("mod");
            }
            if (p.type() != Type.SCROLLER) {
                choices.addAll(imageFiles());
            }
            choiceList.selected = raw == null ? -1 : choices.indexOf(raw);
            choiceList.scroll = 0;
        }
        syncing = false;
        updateWidgets();
    }

    private void updateWidgets() {
        Type t = selected.type();
        valueBox.visible = t != Type.BOOL;
        cycleButton.visible = t == Type.BOOL;
        folderButton.visible = t == Type.HEADER_IMAGE || t == Type.WINDOW_IMAGE;
        inheritButton.active = isSet(selected);
        if (t == Type.BOOL) {
            String raw = raw(selected);
            String key = raw == null ? "tabtailor.style.bool.inherit" : Boolean.parseBoolean(raw) ? "tabtailor.style.on" : "tabtailor.style.off";
            cycleButton.setMessage(Component.translatable(key, effective(selected)));
        }
    }

    private static List<String> imageFiles() {
        Path dir = ConfigManager.root().resolve("images");
        if (!Files.isDirectory(dir)) {
            return List.of();
        }
        try (Stream<Path> files = Files.list(dir)) {
            return files.map(f -> f.getFileName().toString())
                    .filter(n -> n.toLowerCase(Locale.ROOT).endsWith(".png"))
                    .sorted().toList();
        } catch (Exception e) {
            return List.of();
        }
    }

    // ------------------------------------------------------------------ actions

    private void valueTyped(String value) {
        if (syncing) {
            return;
        }
        String v = value.trim();
        switch (selected.type()) {
            case COLOR -> {
                if (v.isEmpty()) {
                    set(selected, null);
                    return;
                }
                Integer c = ColorPicker.parse(v);
                valueBox.setTextColor(c == null ? 0xFF5555 : 0xE0E0E0);
                if (c != null) {
                    set(selected, ColorPicker.hex(c));
                    picker.setColor(c);
                }
            }
            case INT -> {
                if (v.isEmpty()) {
                    set(selected, null);
                    return;
                }
                try {
                    int n = Integer.parseInt(v);
                    valueBox.setTextColor(n > 0 ? 0xE0E0E0 : 0xFF5555);
                    if (n > 0) {
                        set(selected, n);
                    }
                } catch (NumberFormatException e) {
                    valueBox.setTextColor(0xFF5555);
                }
            }
            default -> {
                valueBox.setTextColor(0xE0E0E0);
                set(selected, v.isEmpty() ? null : v);
                choiceList.selected = choices.indexOf(v);
            }
        }
    }

    private void choiceChosen() {
        int i = choiceList.selected;
        if (i >= 0 && i < choices.size()) {
            set(selected, choices.get(i));
            syncing = true;
            valueBox.setValue(choices.get(i));
            syncing = false;
        }
    }

    private void cycleBool() {
        String raw = raw(selected);
        if (raw == null) {
            set(selected, true);
        } else if (Boolean.parseBoolean(raw)) {
            set(selected, false);
        } else {
            set(selected, null);
        }
        updateWidgets();
    }

    private void openImages() {
        Path dir = ConfigManager.root().resolve("images");
        try {
            Files.createDirectories(dir);
        } catch (Exception ignored) {
            // opening shows the error
        }
        Util.getPlatform().openFile(dir.toFile());
    }

    private void save() {
        if (scope == Scope.SECTION) {
            if (sectionTarget != null) {
                for (Prop p : props) {
                    sectionTarget.remove(p.key());
                }
                for (Map.Entry<String, JsonElement> e : objects.get(Group.HEADER).entrySet()) {
                    sectionTarget.add(e.getKey(), e.getValue());
                }
            }
            if (onApply != null) {
                onApply.run();
            }
            saved.put(Group.HEADER, objects.get(Group.HEADER).toString());
            minecraft.setScreen(parent);
            return;
        }
        try {
            Path file = file(true);
            if (file == null) {
                return;
            }
            String text = Files.isRegularFile(file) ? Files.readString(file, StandardCharsets.UTF_8) : "{\n}\n";
            if (Files.isRegularFile(file)) {
                Path root = ConfigManager.root();
                Path backup = root.resolve("_backup").resolve(LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss")))
                        .resolve(root.relativize(file).toString());
                if (Files.notExists(backup)) {
                    Files.createDirectories(backup.getParent());
                    Files.copy(file, backup);
                }
            }
            for (Group group : Group.values()) {
                JsonObject o = objects.get(group);
                if (o.toString().equals(saved.get(group))) {
                    continue;
                }
                String key = FILE_KEYS.get(group);
                text = o.size() == 0 ? JsonText.remove(text, key) : JsonText.set(text, key, JsonText.object(o));
            }
            Files.createDirectories(file.getParent());
            Files.writeString(file, text, StandardCharsets.UTF_8);
        } catch (Exception e) {
            TabTailor.LOGGER.error("[Tab Tailor] Style editor could not save", e);
            setStatus(Component.translatable("tabtailor.editor.error.save", String.valueOf(e.getMessage())), 0xFFFF5555);
            return;
        }
        TabTailorConfig reloaded = ConfigManager.reload();
        TabTailorCommands.rebuildTabsNow();
        Set<String> problems = new LinkedHashSet<>(reloaded.problems());
        problems.addAll(TabContentProcessor.lastProblems());
        objects.forEach((g, o) -> saved.put(g, o.toString()));
        setStatus(problems.isEmpty() ? Component.translatable("tabtailor.editor.status.saved")
                : Component.translatable("tabtailor.editor.status.saved_problems", problems.size()),
                problems.isEmpty() ? 0xFF55FF55 : 0xFFFFAA00);
        updateWidgets();
    }

    private void setStatus(Component text, int color) {
        status = text;
        statusColor = color;
    }

    @Override
    public void onClose() {
        if (!dirty()) {
            minecraft.setScreen(parent);
            return;
        }
        minecraft.setScreen(new ConfirmScreen(yes -> minecraft.setScreen(yes ? parent : this),
                Component.translatable("tabtailor.editor.discard.title"), Component.translatable("tabtailor.editor.discard.message")));
    }

    // ------------------------------------------------------------------ input

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (super.mouseClicked(mx, my, button)) {
            return true;
        }
        if (button == 0 && selected.type() == Type.COLOR && picker.mouseClicked(mx, my)) {
            return true;
        }
        if (button == 0 && list.press(mx, my) >= 0) {
            return true;
        }
        if (button == 0 && choicesVisible() && choiceList.press(mx, my) >= 0) {
            return true;
        }
        return false;
    }

    private boolean choicesVisible() {
        Type t = selected.type();
        return t == Type.HEADER_IMAGE || t == Type.WINDOW_IMAGE || t == Type.SCROLLER;
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (picker.mouseDragged(mx, my)) {
            return true;
        }
        list.release();
        choiceList.release();
        return super.mouseDragged(mx, my, button, dx, dy);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        boolean any = picker.mouseReleased();
        any |= list.release();
        any |= choiceList.release();
        return any || super.mouseReleased(mx, my, button);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double sx, double sy) {
        if (list.scroll(mx, my, sy) || (choicesVisible() && choiceList.scroll(mx, my, sy))) {
            return true;
        }
        return super.mouseScrolled(mx, my, sx, sy);
    }

    @Override
    public boolean keyPressed(int key, int scan, int modifiers) {
        if (key == GLFW.GLFW_KEY_S && (modifiers & GLFW.GLFW_MOD_CONTROL) != 0) {
            save();
            return true;
        }
        return super.keyPressed(key, scan, modifiers);
    }

    // ------------------------------------------------------------------ rendering

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.renderBackground(g, mouseX, mouseY, partialTick);
        panel(g, MARGIN - 2, TOP - 2, lx1 + 2, bottom + 2);
        panel(g, rx0 - 2, TOP - 2, rx1 + 2, bottom + 2);
    }

    private static void panel(GuiGraphics g, int x0, int y0, int x1, int y1) {
        g.fill(x0, y0, x1, y1, C_PANEL);
        g.renderOutline(x0, y0, x1 - x0, y1 - y0, C_PANEL_EDGE);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        g.drawCenteredString(font, title, width / 2, 9, C_TEXT);
        g.drawString(font, Component.translatable("tabtailor.style.hint"), MARGIN, TOP - 12, C_DIM, false);
        list.render(g, mouseX, mouseY);

        g.drawString(font, Component.translatable(selected.label()), rx0 + 6, TOP + 3, C_TEXT, false);
        switch (selected.type()) {
            case COLOR -> picker.render(g);
            case HEADER_IMAGE, WINDOW_IMAGE, SCROLLER -> {
                choiceList.render(g, mouseX, mouseY);
                if (choices.isEmpty()) {
                    g.drawString(font, Component.translatable("tabtailor.style.no_images"), choiceList.x0 + 2, choiceList.y0 + 2, C_FAINT, false);
                }
            }
            default -> {
                List<net.minecraft.util.FormattedCharSequence> lines = font.split(
                        Component.translatable("tabtailor.style.help." + selected.key()), Math.max(60, valueBox.getX() - rx0 - 16));
                int y = TOP + 16;
                for (var line : lines) {
                    g.drawString(font, line, rx0 + 6, y, C_DIM, false);
                    y += 10;
                }
            }
        }
        renderPreview(g, mouseX, mouseY);
        if (!status.getString().isEmpty()) {
            g.drawCenteredString(font, status, width / 2, height - 40, statusColor);
        }
    }

    private void renderRow(GuiGraphics g, int index, int x0, int y, int x1, int h, boolean sel, boolean hovered, int mx, int my) {
        Object row = rows.get(index);
        if (row instanceof Group group) {
            g.fill(x0, y, x1, y + h, 0x60000000);
            g.drawString(font, Component.translatable("tabtailor.style.group." + group.name().toLowerCase(Locale.ROOT)), x0 + 4, y + 4, 0xFFE08A00, false);
            return;
        }
        Prop p = (Prop) row;
        boolean set = isSet(p);
        g.drawString(font, font.plainSubstrByWidth(Component.translatable(p.label()).getString(), x1 - x0 - 70), x0 + 6, y + 4, set ? C_TEXT : C_DIM, false);
        String value = effective(p);
        int vx = x1 - 4 - Math.min(56, font.width(value));
        if (p.type() == Type.COLOR) {
            int c = effectiveColor(p);
            g.fill(x1 - 16, y + 3, x1 - 4, y + 13, 0xFF000000);
            g.fill(x1 - 15, y + 4, x1 - 5, y + 12, 0xFFFFFFFF);
            g.fill(x1 - 15, y + 4, x1 - 5, y + 12, c);
            vx = x1 - 20 - font.width(value);
        }
        g.drawString(font, font.plainSubstrByWidth(value, 56), Math.max(x0 + 70, vx), y + 4, set ? 0xFFB0B0B0 : C_FAINT, false);
        if (set) {
            g.fill(x0 + 1, y + 2, x0 + 3, y + h - 2, 0xFFE08A00);
        }
    }

    private void renderChoice(GuiGraphics g, int index, int x0, int y, int x1, int h, boolean sel, boolean hovered, int mx, int my) {
        String choice = choices.get(index);
        String label = switch (choice) {
            case "vanilla" -> Component.translatable("tabtailor.style.choice.vanilla").getString();
            case "mod" -> Component.translatable("tabtailor.style.choice.mod").getString();
            default -> choice;
        };
        g.drawString(font, font.plainSubstrByWidth(label, x1 - x0 - 6), x0 + 3, y + 3, sel ? C_TEXT : C_DIM, false);
    }

    private void renderPreview(GuiGraphics g, int mouseX, int mouseY) {
        int x = rx0 + 6;
        int y = previewY;
        g.drawString(font, Component.translatable("tabtailor.style.preview"), x, y - 11, C_DIM, false);
        // header row
        Component sample = scope == Scope.SECTION ? targetName : Component.translatable("tabtailor.style.sample");
        boolean overHeader = mouseX >= x && mouseX < x + 162 && mouseY >= y && mouseY < y + 18;
        SectionScreenHelper.drawHeader(g, font, new SectionLayout.Header(sample, sampleIcon, headerStyle()), x, y, overHeader);
        if (scope == Scope.SECTION) {
            return;
        }
        // side bar and window
        int wy = y + 24;
        SectionScreenHelper.renderSidebarPreview(g, sidebarStyle(), x, wy,
                List.of(new ItemStack(Items.OAK_PLANKS), sampleIcon, new ItemStack(Items.IRON_INGOT)), 1);
        int wx = x + 34;
        int available = Math.max(40, bottom - 2 - wy);
        float scale = Math.min(1.0F, Math.min((rx1 - 6 - wx) / 195.0F, available / 136.0F));
        TabLook look = windowLook();
        ResourceLocation texture = TabLooks.texture(look.background() == null ? "vanilla" : look.background());
        g.pose().pushPose();
        g.pose().translate(wx, wy, 0);
        g.pose().scale(scale, scale, 1.0F);
        g.blit(texture, 0, 0, 0, 0, 195, 136);
        int label = look.labelColor() != null ? look.labelColor() : TabLook.VANILLA_LABEL;
        g.drawString(font, Component.translatable("tabtailor.style.sample"), 8, 6, label, false);
        // a few items and one highlighted slot
        g.renderItem(sampleIcon, 9, 18);
        g.renderItem(new ItemStack(Items.OAK_PLANKS), 27, 18);
        int highlight = look.slotHighlight() != null ? look.slotHighlight() : TabLook.VANILLA_SLOT_HIGHLIGHT;
        g.fill(27, 18, 43, 34, highlight);
        ResourceLocation scroller = look.scroller() == null || look.scroller().equalsIgnoreCase("vanilla") || look.scroller().equalsIgnoreCase("mod")
                ? VANILLA_SCROLLER : ResourceLocation.tryParse(look.scroller());
        g.blitSprite(scroller == null ? VANILLA_SCROLLER : scroller, 175, 18, 12, 15);
        g.pose().popPose();
    }

    @Override
    public boolean isPauseScreen() {
        return true;
    }
}
