package com.github.cinnaio.relations.gui;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.jetbrains.annotations.NotNull;

public class RelationsHolder implements InventoryHolder {

    private final int page;
    private Inventory inventory;

    public RelationsHolder(int page) {
        this.page = page;
    }

    public int getPage() {
        return page;
    }

    public void setInventory(Inventory inventory) {
        this.inventory = inventory;
    }

    @Override
    public @NotNull Inventory getInventory() {
        return inventory;
    }
}
