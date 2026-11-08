package com.thetecker.botwinventory;

import it.unimi.dsi.fastutil.objects.Object2IntMap;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Breath of the Wild style inventory (client side only). */
public class BotwInventoryScreen extends Screen {

    // ---------------------------------------------------------------- data
    private enum Cat {
        WEAPONS("Weapons", "weapons"),
        ARMOR("Armor", "armor"),
        TOOLS("Tools", "tools"),
        BLOCKS("Blocks", "blocks"),
        MATERIALS("Materials", "materials"),
        FOOD("Food", "food"),
        MISC("Misc", "misc");

        final String label;
        final Identifier icon, iconDim;

        Cat(String label, String file) {
            this.label = label;
            this.icon = tex("icons/" + file + ".png");
            this.iconDim = tex("icons/" + file + "_dim.png");
        }
    }

    private static Identifier tex(String path) {
        return Identifier.fromNamespaceAndPath("botw_inventory", "textures/gui/" + path);
    }

    private static final Identifier RING_BG = tex("food/ring_bg.png");
    // Food wheel: each gauge is ONE small piece (1/20), drawn pointing up, repeated around the center.
    // outer gauge (green): ON / OFF + state variants   |   inner gauge (yellow): ON / OFF
    private static final Identifier GREEN_ON = tex("food/green_on.png");
    private static final Identifier GREEN_OFF = tex("food/green_off.png");
    private static final Identifier GREEN_LOW = tex("food/green_low.png");
    private static final Identifier GREEN_SICK = tex("food/green_sick.png");
    private static final Identifier GREEN_ALERT = tex("food/green_alert.png");
    private static final Identifier GREEN_PREVIEW = tex("food/green_preview.png");
    private static final Identifier YELLOW_ON = tex("food/yellow_on.png");
    private static final Identifier YELLOW_OFF = tex("food/yellow_off.png");
    private static final Identifier RING_BG = tex("food/ring_bg.png");
    private static final Identifier BG_WARN = tex("food/ring_bg_warn.png");
    private static final Identifier BG_ALERT = tex("food/ring_bg_alert.png");
    private static final Identifier BG_SICK = tex("food/ring_bg_sick.png");
    private static final Identifier GLOW_1 = tex("food/glow_1.png");
    private static final Identifier GLOW_2 = tex("food/glow_2.png");
    // piece geometry, in design units (the whole wheel is 128 units wide); textures are 4 px per unit
    private static final int G_W = 21, G_H = 21, Y_W = 17, Y_H = 14, PX = 4;
    private static final float G_MID = 51.5f, Y_MID = 37.75f;
    private static final Set<String> BAD_FOOD = Set.of(
            "rotten_flesh", "spider_eye", "poisonous_potato", "pufferfish");

    private record Entry(ItemStack stack, int menuSlot, boolean equipped) {}

    private record Line(String text, int color) {}

    private static final Set<String> TOOL_NAMES = Set.of(
            "shears", "fishing_rod", "flint_and_steel", "brush", "carrot_on_a_stick", "warped_fungus_on_a_stick");

    private static final Set<String> MISC_NAMES = Set.of(
            "compass", "recovery_compass", "clock", "spyglass", "map", "filled_map", "ender_pearl", "ender_eye",
            "totem_of_undying", "written_book", "writable_book", "enchanted_book", "book", "name_tag", "lead",
            "saddle", "firework_rocket", "experience_bottle", "ominous_bottle", "bundle", "wolf_armor",
            "trial_key", "ominous_trial_key", "nether_star", "elytra_placeholder");

    private static final String[] HEART = {
            "011101110", "111111111", "111111111", "111111111",
            "011111110", "001111100", "000111000", "000010000"};

    private static final int COLS = 5, ROWS = 4, PER = COLS * ROWS, GAP = 3, TAB_W = 26;
    private static final int CYAN = 0xFF2FD8C8, GOLD = 0xFFD9B44A;

    // --------------------------------------------------------------- state
    private final LocalPlayer player;
    private Cat cat = Cat.WEAPONS;
    private int cursor = 0;
    private int sortMode = 0; // 0 default, 1 value, 2 name
    private List<Entry> entries = new ArrayList<>();
    private int lastMX = -1, lastMY = -1;

    // layout
    private int S, gx, gy, rx1, rx2, leftW, sysX1, sysX2;

    public BotwInventoryScreen(LocalPlayer player) {
        super(Component.literal("Inventory"));
        this.player = player;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void onClose() {
        if (minecraft != null && minecraft.player != null) minecraft.player.closeContainer();
        super.onClose();
    }

    // ------------------------------------------------------ classification
    private static boolean isMisc(String p) {
        return MISC_NAMES.contains(p) || p.contains("bucket") || p.contains("boat") || p.contains("minecart")
                || p.endsWith("_spawn_egg") || p.startsWith("music_disc") || p.endsWith("_pattern")
                || p.endsWith("_horse_armor") || p.endsWith("_raft");
    }

    private static Cat classify(ItemStack s) {
        String p = BuiltInRegistries.ITEM.getKey(s.getItem()).getPath();
        if (p.endsWith("_sword") || p.endsWith("_axe") || p.endsWith("_spear") || p.equals("trident")
                || p.equals("mace") || p.equals("bow") || p.equals("crossbow") || p.contains("arrow")) return Cat.WEAPONS;
        if (p.endsWith("_helmet") || p.endsWith("_chestplate") || p.endsWith("_leggings") || p.endsWith("_boots")
                || p.equals("elytra") || p.equals("shield")) return Cat.ARMOR;
        if (s.has(DataComponents.FOOD) || p.endsWith("potion")) return Cat.FOOD;
        if (p.endsWith("_pickaxe") || p.endsWith("_shovel") || p.endsWith("_hoe") || TOOL_NAMES.contains(p)
                || s.isDamageableItem()) return Cat.TOOLS;
        if (isMisc(p)) return Cat.MISC;
        if (s.getItem() instanceof BlockItem) return Cat.BLOCKS;
        return Cat.MATERIALS;
    }

    private void rebuild() {
        List<Entry> list = new ArrayList<>();
        Inventory inv = player.getInventory();
        int sel = inv.getSelectedSlot();

        if (cat == Cat.ARMOR) {
            EquipmentSlot[] slots = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
            for (int i = 0; i < 4; i++) {
                ItemStack s = player.getItemBySlot(slots[i]);
                if (!s.isEmpty()) list.add(new Entry(s, 5 + i, true));
            }
        }
        ItemStack off = player.getOffhandItem();
        if (!off.isEmpty() && classify(off) == cat) list.add(new Entry(off, 45, true));

        for (int i = 0; i < 36; i++) {
            ItemStack s = inv.getItem(i);
            if (s.isEmpty() || classify(s) != cat) continue;
            int menuSlot = i < 9 ? 36 + i : i;
            list.add(new Entry(s, menuSlot, i == sel));
        }

        if (sortMode == 1) {
            list.sort(Comparator.comparingDouble((Entry e) -> value(e)).reversed());
        } else if (sortMode == 2) {
            list.sort(Comparator.comparing((Entry e) -> e.stack().getHoverName().getString()));
        }
        entries = list;
        cursor = Mth.clamp(cursor, 0, Math.max(0, entries.size() - 1));
    }

    // --------------------------------------------------------------- stats
    private static boolean hasAttr(ItemStack s, Holder<Attribute> a) {
        for (var e : s.getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY).modifiers()) {
            if (e.attribute().value() == a.value()) return true;
        }
        return false;
    }

    private static double attr(ItemStack s, Holder<Attribute> a) {
        double v = 0;
        for (var e : s.getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY).modifiers()) {
            if (e.attribute().value() == a.value()
                    && e.modifier().operation() == AttributeModifier.Operation.ADD_VALUE) {
                v += e.modifier().amount();
            }
        }
        return v;
    }

    private static double attack(ItemStack s) {
        return s.isEmpty() ? 1.0 : 1.0 + attr(s, Attributes.ATTACK_DAMAGE);
    }

    /** Number shown on the slot: damage (weapons), defense (armor), count (stacks), -1 if none. */
    private double value(Entry e) {
        ItemStack s = e.stack();
        switch (cat) {
            case WEAPONS:
                if (hasAttr(s, Attributes.ATTACK_DAMAGE)) return attack(s);
                return s.getCount() > 1 ? s.getCount() : -1;
            case ARMOR: return hasAttr(s, Attributes.ARMOR) ? attr(s, Attributes.ARMOR) : -1;
            default: return s.getCount() > 1 ? s.getCount() : -1;
        }
    }

    /** Value of what is currently equipped, to show the green/red arrow. NaN = no comparison. */
    private double current(Entry e) {
        if (e.equipped()) return Double.NaN;
        if (cat == Cat.WEAPONS && hasAttr(e.stack(), Attributes.ATTACK_DAMAGE)) return attack(player.getMainHandItem());
        if (cat == Cat.ARMOR) {
            var eq = e.stack().get(DataComponents.EQUIPPABLE);
            if (eq == null) return Double.NaN;
            ItemStack worn = player.getItemBySlot(eq.slot());
            return hasAttr(worn, Attributes.ARMOR) ? attr(worn, Attributes.ARMOR) : 0;
        }
        return Double.NaN;
    }

    private static String fmt(double v) {
        return v == Math.rint(v) ? Integer.toString((int) v) : String.format(Locale.ROOT, "%.1f", v);
    }

    private List<Line> describe(Entry e) {
        ItemStack s = e.stack();
        List<Line> out = new ArrayList<>();

        if (s.isDamageableItem()) {
            int max = s.getMaxDamage();
            int left = max - s.getDamageValue();
            float f = left / (float) max;
            int col = f < 0.10f ? 0xFFFF5555 : f < 0.40f ? 0xFFFFD255 : 0xFF8CFF9B;
            out.add(new Line("Durability  " + left + " / " + max, col));
        }
        if (cat == Cat.WEAPONS && hasAttr(s, Attributes.ATTACK_SPEED)) {
            out.add(new Line("Attack Speed  " + fmt(4.0 + attr(s, Attributes.ATTACK_SPEED)), 0xFFE6F6F4));
        }
        if (cat == Cat.ARMOR) {
            double t = attr(s, Attributes.ARMOR_TOUGHNESS);
            double k = attr(s, Attributes.KNOCKBACK_RESISTANCE);
            if (t > 0) out.add(new Line("Toughness  " + fmt(t), 0xFFE6F6F4));
            if (k > 0) out.add(new Line("Knockback Resist.  " + fmt(k * 100) + "%", 0xFFE6F6F4));
        }
        var food = s.get(DataComponents.FOOD);
        if (food != null) {
            out.add(new Line("Hunger  +" + food.nutrition(), 0xFFFFC27A));
            out.add(new Line("Saturation  +" + fmt(food.saturation()), 0xFFFFC27A));
        }
        for (ItemEnchantments en : new ItemEnchantments[]{
                s.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY),
                s.getOrDefault(DataComponents.STORED_ENCHANTMENTS, ItemEnchantments.EMPTY)}) {
            for (Object2IntMap.Entry<Holder<Enchantment>> x : en.entrySet()) {
                out.add(new Line(Enchantment.getFullname(x.getKey(), x.getIntValue()).getString(), 0xFF9FE8FF));
            }
        }
        return out;
    }

    // -------------------------------------------------------------- layout
    private void layout() {
        S = Math.max(18, Math.min(30, (height - 120) / 4 - GAP));
        int gridW = COLS * (S + GAP) - GAP;
        leftW = Math.max(gridW, Cat.values().length * TAB_W);
        gx = Math.max(12, width / 2 - leftW - 40);
        gy = 72;
        rx1 = gx + leftW + 16;
        rx2 = width - 14;
    }

    private int slotX(int local) { return gx + (local % COLS) * (S + GAP); }

    private int slotY(int local) { return gy + (local / COLS) * (S + GAP); }

    private int slotAt(double mx, double my) {
        int page = cursor / PER;
        for (int k = 0; k < PER; k++) {
            int x = slotX(k), y = slotY(k);
            if (mx >= x && mx < x + S && my >= y && my < y + S) {
                int idx = page * PER + k;
                return idx < entries.size() ? idx : -1;
            }
        }
        return -1;
    }

    // -------------------------------------------------------------- helpers
    private void outline(GuiGraphics g, int x1, int y1, int x2, int y2, int c) {
        g.fill(x1, y1, x2, y1 + 1, c);
        g.fill(x1, y2 - 1, x2, y2, c);
        g.fill(x1, y1, x1 + 1, y2, c);
        g.fill(x2 - 1, y1, x2, y2, c);
    }

    private void corners(GuiGraphics g, int x1, int y1, int x2, int y2, int len, int c) {
        g.fill(x1, y1, x1 + len, y1 + 2, c); g.fill(x1, y1, x1 + 2, y1 + len, c);
        g.fill(x2 - len, y1, x2, y1 + 2, c); g.fill(x2 - 2, y1, x2, y1 + len, c);
        g.fill(x1, y2 - 2, x1 + len, y2, c); g.fill(x1, y2 - len, x1 + 2, y2, c);
        g.fill(x2 - len, y2 - 2, x2, y2, c); g.fill(x2 - 2, y2 - len, x2, y2, c);
    }

    private void text(GuiGraphics g, String s, int x, int y, float sc, int col, boolean center) {
        g.pose().pushMatrix();
        g.pose().translate((float) x, (float) y);
        g.pose().scale(sc, sc);
        int w = font.width(s);
        g.drawString(font, s, center ? -w / 2 : 0, 0, col, true);
        g.pose().popMatrix();
    }

    private void item(GuiGraphics g, ItemStack s, int x, int y, float scale) {
        g.pose().pushMatrix();
        g.pose().translate((float) x, (float) y);
        g.pose().scale(scale, scale);
        g.renderItem(s, 0, 0);
        g.pose().popMatrix();
    }

    /** Draws a 128x128 texture scaled to 'size' gui pixels, centered on (cx, cy). */
    private void icon(GuiGraphics g, Identifier id, int cx, int cy, int size) {
        float k = size / 128f;
        g.pose().pushMatrix();
        g.pose().translate(cx - size / 2f, cy - size / 2f);
        g.pose().scale(k, k);
        g.blit(RenderPipelines.GUI_TEXTURED, id, 0, 0, 0f, 0f, 128, 128, 128, 128);
        g.pose().popMatrix();
    }

    /** Draws one wheel piece (pointing up) rotated by 'angle' around the wheel center (cx, cy). */
    private void piece(GuiGraphics g, Identifier id, int cx, int cy, float k, double angle,
                       float mid, int w, int h) {
        g.pose().pushMatrix();
        g.pose().translate((float) cx, (float) cy);
        g.pose().scale(k, k);
        g.pose().rotate((float) angle);
        g.pose().translate(-w / 2f, -mid - h / 2f);
        g.pose().scale(1f / PX, 1f / PX);
        g.blit(RenderPipelines.GUI_TEXTURED, id, 0, 0, 0f, 0f, w * PX, h * PX, w * PX, h * PX);
        g.pose().popMatrix();
    }

    /**
     * Stamina-style wheel: outer ring = hunger (20 segments), inner ring = saturation.
     * States: normal (green), hungry (orange, no sprint at 6 or less), starving (red pulse),
     * Hunger effect (sickly olive), recovering (green halo while regenerating),
     * and a blinking preview of what the hovered food would restore (olive if it is bad food).
     */
    private void drawRing(GuiGraphics g, int cx, int cy, int size) {
        var fd = player.getFoodData();
        int food = Mth.clamp(fd.getFoodLevel(), 0, 20);
        int sat = Mth.clamp((int) Math.ceil(fd.getSaturationLevel()), 0, 20);

        boolean sick = player.hasEffect(MobEffects.HUNGER);
        boolean starving = food == 0;
        boolean low = !starving && food <= 6;
        boolean regen = !sick && !starving && ((food >= 18 && player.getHealth() < player.getMaxHealth())
                || player.hasEffect(MobEffects.REGENERATION));

        long t = System.currentTimeMillis();
        boolean blink = (t / 350) % 2 == 0;
        boolean pulse = (t / 450) % 2 == 0;

        int preview = 0;
        boolean badPreview = false;
        if (!entries.isEmpty()) {
            ItemStack hov = entries.get(cursor).stack();
            var f = hov.get(DataComponents.FOOD);
            if (f != null) {
                preview = f.nutrition();
                badPreview = BAD_FOOD.contains(BuiltInRegistries.ITEM.getKey(hov.getItem()).getPath());
            }
        }

        Identifier on = sick ? GREEN_SICK : starving ? GREEN_ALERT : low ? GREEN_LOW : GREEN_ON;
        Identifier off = starving && pulse ? GREEN_ALERT : GREEN_OFF;
        Identifier bg = sick ? BG_SICK : starving ? (pulse ? BG_ALERT : RING_BG) : low ? BG_WARN : RING_BG;
        float k = size / 128f;

        if (regen) {
            int phase = (int) ((t / 300) % 4);
            if (phase == 1 || phase == 3) icon(g, GLOW_1, cx, cy, size);
            else if (phase == 2) icon(g, GLOW_2, cx, cy, size);
        }
        icon(g, bg, cx, cy, size);
        for (int i = 0; i < 20; i++) {
            double a = Math.toRadians(i * 18.0);
            Identifier seg;
            if (i < food) seg = on;
            else if (blink && i < food + preview) seg = badPreview ? GREEN_SICK : GREEN_PREVIEW;
            else seg = off;
            piece(g, seg, cx, cy, k, a, G_MID, G_W, G_H);
            piece(g, i < sat ? YELLOW_ON : YELLOW_OFF, cx, cy, k, a, Y_MID, Y_W, Y_H);
        }

        String label;
        int col;
        if (starving) { label = "Starving"; col = 0xFFFF6B6B; }
        else if (sick) { label = "Hunger"; col = 0xFFC8D460; }
        else if (low) { label = "Hungry"; col = 0xFFFFB347; }
        else if (regen) { label = "Recovering"; col = 0xFF8CFF9B; }
        else { label = food + " / 20"; col = 0xFFE6F6F4; }
        text(g, label, cx, cy + size / 2 + 2, 0.9f, col, true);
    }

    private void arrow(GuiGraphics g, int x, int y, boolean up, int c) {
        for (int r = 0; r < 4; r++) {
            int w = up ? r : 3 - r;
            g.fill(x + 3 - w, y + r, x + 4 + w, y + r + 1, c);
        }
    }

    private static int light(int c) {
        int r = Math.min(255, ((c >> 16) & 255) + 90), gg = Math.min(255, ((c >> 8) & 255) + 90),
                b = Math.min(255, (c & 255) + 90);
        return 0xFF000000 | (r << 16) | (gg << 8) | b;
    }

    private void heart(GuiGraphics g, int x, int y, int fullness, int col) {
        for (int r = 0; r < 8; r++) {
            for (int c = 0; c < 9; c++) {
                if (HEART[r].charAt(c) != '1') continue;
                boolean filled = fullness == 2 || (fullness == 1 && c <= 4);
                int color = filled ? col : 0xFF2A1A1E;
                if (filled && r == 1 && (c == 1 || c == 2)) color = light(col);
                g.fill(x + c, y + r, x + c + 1, y + r + 1, color);
            }
        }
    }

    private int keyBox(GuiGraphics g, String key, int x, int y) {
        int w = Math.max(12, font.width(key) + 6);
        g.fill(x, y - 1, x + w, y + 11, 0x80061418);
        outline(g, x, y - 1, x + w, y + 11, 0xFFBFEFEA);
        g.drawString(font, key, x + (w - font.width(key)) / 2 + 1, y + 1, 0xFFFFFFFF, true);
        return w;
    }

    private int hint(GuiGraphics g, String label, String key, int xr, int y) {
        int w = Math.max(12, font.width(key) + 6);
        keyBox(g, key, xr - w, y);
        int tw = font.width(label);
        g.drawString(font, label, xr - w - 4 - tw, y + 1, 0xFFE6F6F4, true);
        return xr - w - 4 - tw - 14;
    }

    private void ping(float pitch) {
        minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, pitch));
    }

    // -------------------------------------------------------------- render
    /** Vanilla calls this before render(): blur once here, then tint. */
    @Override
    public void renderBackground(GuiGraphics g, int mx, int my, float pt) {
        renderBlurredBackground(g);
        g.fillGradient(0, 0, width, height, 0xB0123A33, 0xD0061418);
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        rebuild();
        layout();

        if (mx != lastMX || my != lastMY) {
            lastMX = mx;
            lastMY = my;
            int h = slotAt(mx, my);
            if (h >= 0 && h != cursor) { cursor = h; ping(1.5f); }
        }

        drawHeader(g);
        drawTabs(g);
        drawGrid(g);
        drawPlayer(g, mx, my);
        drawRing(g, rx1 + 30, 70, 52);
        drawInfo(g);
        drawFooter(g);
    }

    private void drawHeader(GuiGraphics g) {
        // hearts (top left)
        int hearts = (int) Math.ceil(player.getMaxHealth() / 2f);
        float hp = player.getHealth();
        for (int i = 0; i < hearts; i++) {
            float h = hp - 2 * i;
            int full = h >= 2 ? 2 : h >= 1 ? 1 : 0;
            heart(g, 12 + (i % 10) * 10, 8 + (i / 10) * 10, full, 0xFFE8302A);
        }
        int abs = (int) Math.ceil(player.getAbsorptionAmount() / 2f);
        for (int i = 0; i < abs; i++) {
            int n = hearts + i;
            heart(g, 12 + (n % 10) * 10, 8 + (n / 10) * 10, 2, 0xFFF2D83C);
        }

        // title + Adventure Log / System
        String title = "Inventory";
        int tw = (int) (font.width(title) * 1.5f);
        text(g, title, width / 2, 8, 1.5f, 0xFFF4FFFD, true);
        int advEnd = width / 2 - tw / 2 - 18;
        int advW = font.width("Adventure Log");
        g.drawString(font, "Adventure Log", advEnd - 14 - advW, 13, 0xFFBFEFEA, true);
        keyBox(g, "L", advEnd - 12, 12);
        sysX1 = width / 2 + tw / 2 + 18;
        int bw = keyBox(g, "R", sysX1, 12);
        g.drawString(font, "System", sysX1 + bw + 4, 13, 0xFFBFEFEA, true);
        sysX2 = sysX1 + bw + 4 + font.width("System");

        // top line with a notch
        g.fill(10, 30, width - 10, 31, 0x702FD8C8);
        g.fill(width / 2 - 14, 29, width / 2 + 14, 32, 0xB02FD8C8);
    }

    private void drawTabs(GuiGraphics g) {
        Cat[] cats = Cat.values();
        text(g, cat.label, gx + 4, 35, 1f, 0xFFE9FFFB, false);
        for (int i = 0; i < cats.length; i++) {
            int x = gx + 4 + i * TAB_W, y = 44;
            boolean sel = cats[i] == cat;
            icon(g, sel ? cats[i].icon : cats[i].iconDim, x + 11, y + 10, sel ? 24 : 20);
            if (sel) g.fill(x, y + 22, x + 22, y + 24, 0xFFBFFFF6);
        }
        g.fill(gx, 70, gx + leftW, 71, 0x502FD8C8);
    }

    private void drawGrid(GuiGraphics g) {
        int page = cursor / PER;
        long t = System.currentTimeMillis();

        for (int k = 0; k < PER; k++) {
            int idx = page * PER + k;
            int x = slotX(k), y = slotY(k), x2 = x + S, y2 = y + S;

            if (idx >= entries.size()) {
                g.fill(x, y, x2, y2, 0x50061418);
                outline(g, x, y, x2, y2, 0x302FD8C8);
                continue;
            }

            Entry e = entries.get(idx);
            ItemStack s = e.stack();
            boolean low = s.isDamageableItem() && (s.getMaxDamage() - s.getDamageValue()) <= s.getMaxDamage() * 0.10;

            g.fill(x, y, x2, y2, e.equipped() ? 0xD02F6FE8 : 0xB0061A20);
            outline(g, x, y, x2, y2, low ? 0xFFFF4040 : e.equipped() ? 0xFF8FB8FF : 0xA02FD8C8);
            item(g, s, x + 3, y + 3, (S - 6) / 16f);

            double v = value(e);
            if (v >= 0) {
                String str = fmt(v);
                int tx = x2 - 2 - font.width(str), ty = y2 - 9;
                g.drawString(font, str, tx, ty, 0xFFFFFFFF, true);
                double cur = current(e);
                if (!Double.isNaN(cur) && cur != v) {
                    arrow(g, tx - 8, ty + 2, v > cur, v > cur ? 0xFF4CFF6A : 0xFFFF4040);
                }
            }

            if (idx == cursor) {
                int a = (int) (170 + 85 * Math.sin(t / 180.0));
                int glow = (a << 24) | 0xFFD23C;
                outline(g, x - 2, y - 2, x2 + 2, y2 + 2, glow);
                outline(g, x - 1, y - 1, x2 + 1, y2 + 1, glow);
                corners(g, x - 3, y - 3, x2 + 3, y2 + 3, 6, 0xFFFFE27A);
            }
        }

        if (entries.isEmpty()) {
            g.drawString(font, "Nothing here yet", gx + 4, gy + 6, 0xFF7FA8A3, true);
        }
        int pages = Math.max(1, (entries.size() + PER - 1) / PER);
        if (pages > 1) {
            String p = "<  " + (page + 1) + " / " + pages + "  >";
            g.drawString(font, p, gx, gy + ROWS * (S + GAP) + 2, 0xFFBFEFEA, true);
        }
    }

    private void drawPlayer(GuiGraphics g, int mx, int my) {
        int y1 = 36, y2 = height - 40;
        int scale = (int) ((y2 - y1) * 30f / 70f);
        InventoryScreen.renderEntityInInventoryFollowsMouse(
                g, rx1, y1, rx2, y2, scale, 0.0625F, (float) mx, (float) my, player);
    }

    private void drawInfo(GuiGraphics g) {
        if (entries.isEmpty()) return;
        Entry e = entries.get(cursor);
        List<Line> lines = describe(e);

        int x1 = rx1, x2 = rx2, y2 = height - 26;
        int h = 30 + lines.size() * 10 + (lines.isEmpty() ? 0 : 4);
        int y1 = y2 - h;

        g.fill(x1, y1, x2, y2, 0xD0070C10);
        outline(g, x1, y1, x2, y2, 0xFFC9A74A);
        outline(g, x1 + 2, y1 + 2, x2 - 2, y2 - 2, 0x40C9A74A);
        g.fill(x1 + 6, y1 + 22, x2 - 6, y1 + 23, 0x60FFFFFF);

        // right side: value (+ arrow vs. equipped)
        double v = value(e);
        int rightEdge = x2 - 8;
        int reserved = 0;
        if (v >= 0 && (cat == Cat.WEAPONS || cat == Cat.ARMOR)) {
            double cur = current(e);
            boolean cmp = !Double.isNaN(cur) && cur != v;
            String str = cmp ? fmt(cur) + " > " + fmt(v) : fmt(v);
            if (cmp) {
                arrow(g, rightEdge - 7, y1 + 10, v > cur, v > cur ? 0xFF4CFF6A : 0xFFFF4040);
                rightEdge -= 10;
            }
            int sw = (int) (font.width(str) * 1.25f);
            text(g, str, rightEdge - sw, y1 + 7, 1.25f, 0xFFFFFFFF, false);
            icon(g, cat.icon, rightEdge - sw - 7, y1 + 12, 12);
            reserved = sw + 18 + (cmp ? 10 : 0);
        } else if (v >= 0) {
            String str = "x" + fmt(v);
            int sw = (int) (font.width(str) * 1.25f);
            text(g, str, rightEdge - sw, y1 + 7, 1.25f, 0xFFFFFFFF, false);
            reserved = sw + 6;
        }

        // title
        String name = e.stack().getHoverName().getString();
        int avail = (int) ((x2 - x1 - 16 - reserved) / 1.25f);
        text(g, font.plainSubstrByWidth(name, Math.max(10, avail)), x1 + 8, y1 + 7, 1.25f, 0xFFFFFFFF, false);

        // description lines
        int ly = y1 + 28;
        for (Line l : lines) {
            g.drawString(font, font.plainSubstrByWidth(l.text(), x2 - x1 - 16), x1 + 8, ly, l.color(), true);
            ly += 10;
        }
    }

    private void drawFooter(GuiGraphics g) {
        g.fill(10, height - 22, width - 10, height - 21, 0x702FD8C8);
        int y = height - 16;
        int x = width - 14;
        x = hint(g, "Select", "Enter", x, y);
        x = hint(g, "Back", "Esc", x, y);
        String[] sorts = {"Sort", "Sort: Stats", "Sort: Name"};
        x = hint(g, sorts[sortMode], "Y", x, y);
        hint(g, "Drop", "Q", x, y);
    }

    // --------------------------------------------------------------- input
    private void setCat(int i) {
        Cat[] cats = Cat.values();
        cat = cats[Math.floorMod(i, cats.length)];
        cursor = 0;
        ping(1.2f);
    }

    private void move(int d) {
        if (entries.isEmpty()) return;
        int n = Mth.clamp(cursor + d, 0, entries.size() - 1);
        if (n != cursor) { cursor = n; ping(1.5f); }
    }

    private void click(int slot, int button, ClickType type) {
        minecraft.gameMode.handleInventoryMouseClick(player.inventoryMenu.containerId, slot, button, type, player);
    }

    private void activate() {
        if (entries.isEmpty()) return;
        Entry e = entries.get(cursor);
        Inventory inv = player.getInventory();
        int slot = e.menuSlot();

        if ((slot >= 5 && slot <= 8) || slot == 45) {
            click(slot, 0, ClickType.QUICK_MOVE);          // unequip
        } else if (e.stack().is(Items.SHIELD)) {
            click(slot, 40, ClickType.SWAP);               // to off-hand
        } else if (cat == Cat.ARMOR) {
            click(slot, 0, ClickType.QUICK_MOVE);          // equip
        } else if (slot >= 36 && slot <= 44) {
            inv.setSelectedSlot(slot - 36);                // hold it
        } else {
            click(slot, inv.getSelectedSlot(), ClickType.SWAP); // swap into hand
        }
        ping(1.0f);
    }

    private void dropSelected(boolean all) {
        if (entries.isEmpty()) return;
        click(entries.get(cursor).menuSlot(), all ? 1 : 0, ClickType.THROW);
    }

    @Override
    public boolean keyPressed(KeyEvent ev) {
        if (minecraft.options.keyInventory.matches(ev)) { onClose(); return true; }
        if (minecraft.options.keyDrop.matches(ev)) {
            dropSelected((ev.modifiers() & GLFW.GLFW_MOD_CONTROL) != 0);
            return true;
        }
        int k = ev.key();
        switch (k) {
            case GLFW.GLFW_KEY_LEFT: move(-1); return true;
            case GLFW.GLFW_KEY_RIGHT: move(1); return true;
            case GLFW.GLFW_KEY_UP: move(-COLS); return true;
            case GLFW.GLFW_KEY_DOWN: move(COLS); return true;
            case GLFW.GLFW_KEY_PAGE_UP: move(-PER); return true;
            case GLFW.GLFW_KEY_PAGE_DOWN: move(PER); return true;
            case GLFW.GLFW_KEY_ENTER:
            case GLFW.GLFW_KEY_KP_ENTER:
            case GLFW.GLFW_KEY_SPACE: activate(); return true;
            case GLFW.GLFW_KEY_Y: sortMode = (sortMode + 1) % 3; ping(1.2f); return true;
            case GLFW.GLFW_KEY_TAB:
                setCat(cat.ordinal() + ((ev.modifiers() & GLFW.GLFW_MOD_SHIFT) != 0 ? -1 : 1));
                return true;
            default:
                if (k >= GLFW.GLFW_KEY_1 && k <= GLFW.GLFW_KEY_7) { setCat(k - GLFW.GLFW_KEY_1); return true; }
        }
        return super.keyPressed(ev);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent ev, boolean dbl) {
        double mx = ev.x(), my = ev.y();
        int b = ev.button();

        for (int i = 0; i < Cat.values().length; i++) {
            int x = gx + 4 + i * TAB_W;
            if (mx >= x && mx < x + 22 && my >= 42 && my < 68) { setCat(i); return true; }
        }
        if (my >= 10 && my <= 24 && mx >= sysX1 && mx <= sysX2) {
            minecraft.setScreen(new PauseScreen(true));
            return true;
        }
        int h = slotAt(mx, my);
        if (h >= 0) {
            cursor = h;
            if (b == 0) activate();
            else if (b == 1) dropSelected(false);
            return true;
        }
        return super.mouseClicked(ev, dbl);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double sx, double sy) {
        if (sy != 0) { move(sy < 0 ? PER : -PER); return true; }
        return super.mouseScrolled(mx, my, sx, sy);
    }
}
