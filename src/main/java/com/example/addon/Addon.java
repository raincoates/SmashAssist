package com.example.addon;

import com.example.addon.modules.SmashAssist;
import meteordevelopment.meteorclient.addons.MeteorAddon;
import meteordevelopment.meteorclient.systems.modules.Category;
import meteordevelopment.meteorclient.systems.modules.Modules;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class Addon extends MeteorAddon {
    // Custom category so the module shows up nicely grouped in the module list,
    // instead of dumping it in Meteor's built-in "Combat" category.
    public static final Category CATEGORY = new Category("SmashAssist");

    public static final Logger LOG = LogManager.getLogger("SmashAssistAddon");

    @Override
    public void onInitialize() {
        LOG.info("Initializing SmashAssist addon");

        Modules.get().add(new SmashAssist());
    }

    @Override
    public void onRegisterCategories() {
        Modules.registerCategory(CATEGORY);
    }

    @Override
    public String getPackage() {
        return "com.example.addon";
    }
}
