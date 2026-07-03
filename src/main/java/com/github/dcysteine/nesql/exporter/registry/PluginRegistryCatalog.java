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

import java.util.Arrays;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.function.BiFunction;

import static com.github.dcysteine.nesql.exporter.plugin.gregtech.util.GTRecipeMap.makeGTRecipe;

/** Descriptor-owned plugin registry order, dependency, and construction catalog. */
final class PluginRegistryCatalog {
    private static final String ROLE_NEI_HANDLER_INGEST = "nei-handler-ingest";

    private static final CatalogAction NO_ACTION = new CatalogAction() {
        @Override
        public void run() {}
    };
    private static boolean catalogActionsExecuted;

    private static final ImmutableList<PluginDescriptor> PLUGINS = validateAndFreeze(Arrays.asList(
            descriptor(Plugin.BASE, "core", BasePluginExporter::new),
            descriptor(Plugin.MINECRAFT, "core", MinecraftPluginExporter::new),
            descriptor(Plugin.FORGE, "core", ForgePluginExporter::new),
            descriptor(
                    Plugin.GREGTECH,
                    "recipe-api",
                    GregTechPluginExporter::new,
                    new CatalogAction() {
                        @Override
                        public void run() {
                            makeGTRecipe();
                        }
                    },
                    ModDependency.GREGTECH_5),
            descriptor(
                    Plugin.QUEST,
                    "quest-api",
                    QuestPluginExporter::new,
                    ModDependency.BETTER_QUESTING),
            descriptor(
                    Plugin.AVARITIA,
                    "direct-recipe-api",
                    AvaritiaPluginExporter::new,
                    ModDependency.AVARITIA),
            descriptor(
                    Plugin.THAUMCRAFT,
                    "direct-recipe-api",
                    ThaumcraftPluginExporter::new,
                    ModDependency.THAUMCRAFT),
            descriptor(
                    Plugin.BOTANIA,
                    "direct-recipe-api",
                    BotaniaPluginExporter::new,
                    ModDependency.BOTANIA),
            descriptor(
                    Plugin.BLOOD_MAGIC,
                    "direct-recipe-api",
                    BloodMagicPluginExporter::new,
                    ModDependency.BLOOD_MAGIC),
            descriptor(
                    Plugin.WITCHERY,
                    "direct-recipe-api",
                    WitcheryPluginExporter::new,
                    ModDependency.WITCHERY),
            descriptor(Plugin.NEI, ROLE_NEI_HANDLER_INGEST, NeiPluginExporter::new)));

    private PluginRegistryCatalog() {}

    static ImmutableList<RegistryEntry> entries() {
        runCatalogActions();
        ImmutableList.Builder<RegistryEntry> builder = ImmutableList.builder();
        for (PluginDescriptor descriptor : PLUGINS) {
            builder.add(descriptor.entry());
        }
        return builder.build();
    }

    static ImmutableList<PluginDescriptor> descriptors() {
        return PLUGINS;
    }

    private static PluginDescriptor descriptor(
            Plugin plugin,
            String role,
            BiFunction<Plugin, ExporterState, PluginExporter> constructor,
            ModDependency... hardDependencies) {
        return descriptor(plugin, role, constructor, NO_ACTION, hardDependencies);
    }

    private static PluginDescriptor descriptor(
            Plugin plugin,
            String role,
            BiFunction<Plugin, ExporterState, PluginExporter> constructor,
            CatalogAction catalogAction,
            ModDependency... hardDependencies) {
        return new PluginDescriptor(plugin, role, constructor, catalogAction, hardDependencies);
    }

    private static void runCatalogActions() {
        if (catalogActionsExecuted) {
            return;
        }
        for (PluginDescriptor descriptor : PLUGINS) {
            descriptor.runCatalogAction();
        }
        catalogActionsExecuted = true;
    }

    private static ImmutableList<PluginDescriptor> validateAndFreeze(
            Iterable<PluginDescriptor> descriptors) {
        if (descriptors == null) {
            throw new IllegalStateException("Plugin registry catalog must not be null");
        }
        Set<Plugin> seenPlugins = EnumSet.noneOf(Plugin.class);
        Set<String> roles = new LinkedHashSet<String>();
        ImmutableList.Builder<PluginDescriptor> builder = ImmutableList.builder();
        for (PluginDescriptor descriptor : descriptors) {
            if (descriptor == null) {
                throw new IllegalStateException("Plugin registry descriptor must not be null");
            }
            if (descriptor.plugin == null) {
                throw new IllegalStateException("Plugin registry descriptor plugin must not be null");
            }
            if (!seenPlugins.add(descriptor.plugin)) {
                throw new IllegalStateException("Duplicate plugin registry descriptor: " + descriptor.plugin);
            }
            requireNonEmpty("Plugin registry descriptor role", descriptor.role);
            roles.add(descriptor.role);
            if (descriptor.constructor == null) {
                throw new IllegalStateException(
                        "Plugin registry constructor must not be null: " + descriptor.plugin);
            }
            if (descriptor.catalogAction == null) {
                throw new IllegalStateException(
                        "Plugin registry catalog action must not be null: " + descriptor.plugin);
            }
            builder.add(descriptor);
        }
        for (Plugin plugin : Plugin.values()) {
            if (!seenPlugins.contains(plugin)) {
                throw new IllegalStateException("Missing plugin registry descriptor: " + plugin);
            }
        }
        if (!roles.contains(ROLE_NEI_HANDLER_INGEST)) {
            throw new IllegalStateException(
                    "Plugin registry catalog must include the NEI handler ingest role");
        }
        return builder.build();
    }

    private static void requireNonEmpty(String label, String value) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalStateException(label + " must be non-empty");
        }
    }

    interface CatalogAction {
        void run();
    }

    static final class PluginDescriptor {
        private final Plugin plugin;
        private final String role;
        private final BiFunction<Plugin, ExporterState, PluginExporter> constructor;
        private final CatalogAction catalogAction;
        private final ModDependency[] hardDependencies;

        private PluginDescriptor(
                Plugin plugin,
                String role,
                BiFunction<Plugin, ExporterState, PluginExporter> constructor,
                CatalogAction catalogAction,
                ModDependency[] hardDependencies) {
            this.plugin = plugin;
            this.role = role;
            this.constructor = constructor;
            this.catalogAction = catalogAction;
            this.hardDependencies = hardDependencies == null
                    ? new ModDependency[0]
                    : Arrays.copyOf(hardDependencies, hardDependencies.length);
        }

        Plugin plugin() {
            return plugin;
        }

        String role() {
            return role;
        }

        RegistryEntry entry() {
            return RegistryEntry.create(plugin, constructor, hardDependencies);
        }

        void runCatalogAction() {
            catalogAction.run();
        }
    }
}
