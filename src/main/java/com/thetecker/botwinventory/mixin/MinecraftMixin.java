package com.thetecker.botwinventory.mixin;

import com.thetecker.botwinventory.BotwInventoryScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(Minecraft.class)
public abstract class MinecraftMixin {
    @ModifyVariable(method = "setScreen", at = @At("HEAD"), argsOnly = true)
    private Screen botw$replaceInventory(Screen screen) {
        if (screen != null && screen.getClass() == InventoryScreen.class) {
            Minecraft mc = (Minecraft) (Object) this;
            if (mc.player != null) return new BotwInventoryScreen(mc.player);
        }
        return screen;
    }
}
