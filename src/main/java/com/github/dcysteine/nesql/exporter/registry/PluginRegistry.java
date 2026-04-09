package com.github.dcysteine.nesql.exporter.registry;

import com.github.dcysteine.nesql.exporter.plugin.ExporterState;
import com.github.dcysteine.nesql.exporter.plugin.PluginExporter;
import com.github.dcysteine.nesql.exporter.plugin.avaritia.AvaritiaPluginExporter;
import com.github.dcysteine.nesql.exporter.plugin.base.BasePluginExporter;
import com.github.dcysteine.nesql.exporter.plugin.bloodmagic.BloodMagicPluginExporter;
import com.github.dcysteine.nesql.exporter.plugin.botania.BotaniaPluginExporter;
import com.github.dcysteine.nesql.exporter.plugin.forge.ForgePluginExporter;
import com.github.dcysteine.nesql.exporter.plugin.gregtech.GregTechPluginExporter;
import com.github.dcysteine.nesql.exporter.plugin.minecraft.MinecraftPluginExporter;
import com.github.dcysteine.nesql.exporter.plugin.nei.NeiPluginExporter;
import com.github.dcysteine.nesql.exporter.plugin.quest.QuestPluginExporter;
import com.github.dcysteine.nesql.exporter.plugin.thaumcraft.ThaumcraftPluginExporter;
import com.github.dcysteine.nesql.exporter.plugin.witchery.WitcheryPluginExporter;
import com.github.dcysteine.nesql.sql.Plugin;
import com.google.common.collect.ImmutableList;

import java.util.EnumMap;
import java.util.Map;

import static com.github.dcysteine.nesql.exporter.plugin.gregtech.util.GTRecipeMap.makeGTRecipe;


/** Registry of plugins. Register new plugins here! */
public class PluginRegistry {
    private static final ImmutableList<RegistryEntry> entries;

    static {
        ImmutableList.Builder<RegistryEntry> builder = ImmutableList.builder();

        // Add new plugins here!
        // Core plugins
        builder.add(RegistryEntry.create(Plugin.BASE, BasePluginExporter::new));
        builder.add(RegistryEntry.create(Plugin.MINECRAFT, MinecraftPluginExporter::new));
        builder.add(RegistryEntry.create(Plugin.FORGE, ForgePluginExporter::new));
        makeGTRecipe();
        builder.add(
                RegistryEntry.create(
                        Plugin.GREGTECH, GregTechPluginExporter::new, ModDependency.GREGTECH_5));
        builder.add(RegistryEntry.create(Plugin.QUEST, QuestPluginExporter::new, ModDependency.BETTER_QUESTING));

        // Special recipe mod plugins (magic mods with direct API access)
        builder.add(
                RegistryEntry.create(
                        Plugin.AVARITIA, AvaritiaPluginExporter::new, ModDependency.AVARITIA));
        builder.add(
                RegistryEntry.create(
                        Plugin.THAUMCRAFT, ThaumcraftPluginExporter::new, ModDependency.THAUMCRAFT));
        builder.add(
                RegistryEntry.create(
                        Plugin.BOTANIA, BotaniaPluginExporter::new, ModDependency.BOTANIA));
        builder.add(
                RegistryEntry.create(
                        Plugin.BLOOD_MAGIC, BloodMagicPluginExporter::new, ModDependency.BLOOD_MAGIC));
        builder.add(
                RegistryEntry.create(
                        Plugin.WITCHERY, WitcheryPluginExporter::new, ModDependency.WITCHERY));

        // NEI plugin as fallback (must be last to catch all recipes from other handlers)
        builder.add(RegistryEntry.create(Plugin.NEI, NeiPluginExporter::new));

        entries = builder.build();
    }

    private final Map<Plugin, PluginExporter> activePlugins = new EnumMap<>(Plugin.class);

    /** Constructs plugins whose dependencies are met. Returns a list of activated plugins. */
    public Map<Plugin, PluginExporter> initialize(ExporterState exporterState) {
        entries.stream()
                .filter(RegistryEntry::areDependenciesSatisfied)
                .filter(RegistryEntry::isEnabled)
                .forEach(
                        entry ->
                                activePlugins.put(
                                        entry.getPlugin(), entry.instantiate(exporterState)));

        if (!activePlugins.containsKey(Plugin.BASE)) {
            throw new IllegalStateException("base plugin must be enabled!");
        }
        return activePlugins;
    }

    public Map<Plugin, PluginExporter> getActivePlugins() {
        return activePlugins;
    }

    public void initializePlugins() {
        activePlugins.values().forEach(PluginExporter::initialize);
    }

    public void processPlugins() {
        activePlugins.values().forEach(PluginExporter::process);
    }

    public void postProcessPlugins() {
        activePlugins.values().forEach(PluginExporter::postProcess);
    }
}
