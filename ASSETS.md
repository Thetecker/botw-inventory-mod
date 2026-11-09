# Textures of the BOTW Inventory mod (assets/botw_inventory/textures/gui)

Every PNG has a `.png.mcmeta` next to it with `{"texture":{"blur":true}}` (smooth scaling).
Replace any file by your own with the SAME name and size, and remove the
"Generate textures" step from .github/workflows/build.yml.

## Category icons  (icons/)  128 x 128, transparent
weapons, armor, tools, blocks, materials, food, misc  ->  <name>.png (selected) and <name>_dim.png (not selected)

## Food wheel pieces  (food/)  drawn POINTING UP, saved tight, 4 px per unit
The wheel is built in code: one piece repeated 20 times around the center (every 18 degrees).
- Green gauge (outer), 84 x 84 px:  green_on, green_off
  state variants of "on": green_low (hungry), green_sick (Hunger effect), green_alert (starving / empty-red), green_preview (food preview)
- Yellow gauge (inner, saturation), 68 x 56 px:  yellow_on, yellow_off
- Backgrounds 128 x 128: ring_bg, ring_bg_warn, ring_bg_alert, ring_bg_sick
- Regeneration halos 128 x 128: glow_1, glow_2
