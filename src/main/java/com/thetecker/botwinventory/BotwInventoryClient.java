package com.thetecker.botwinventory;

import net.fabricmc.api.ClientModInitializer;

public class BotwInventoryClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        // Rien a enregistrer : le MinecraftMixin remplace l'ecran d'inventaire.
    }
}
