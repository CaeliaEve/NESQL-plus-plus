package com.github.dcysteine.nesql.exporter.main.config;

import com.github.dcysteine.nesql.sql.Plugin;
import com.google.common.collect.ImmutableList;
import net.minecraftforge.common.config.Property;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Supplier;

public final class ConfigOptions {
    private static final List<Option<?>> allOptions = new ArrayList<>();

    public static final Option<String> REPOSITORY_NAME =
            new StringOption(
                    Category.OPTIONS, "repository_name", "elysium-dev",
                    "Default raw-export repository name used when /nesql is run without"
                            + " an explicit target."
                            + "\nFinal Elysium validation should use:"
                            + " /nesql --native-ui-export elysium-dev")
                    .register();

    public static final Option<List<String>> ENABLED_PLUGINS =
            new StringListOption(
                    Category.OPTIONS, "enabled_plugins", Plugin.NAMES,
                    "Plugin-backed fact exporters enabled for the GTNH raw-export."
                            + " Do not trim this list for the native UI compiler export unless"
                            + " you are deliberately debugging a single plugin.")
                    .register();

    public static final Option<Boolean> AUTO_EXPORT_ON_CONNECT =
            new BooleanOption(
                    Category.OPTIONS, "auto_export_on_connect", false,
                    "Whether to automatically start the default full export upon connecting"
                            + " to a world."
                            + "\nFor final Elysium/NeoNEI validation, prefer the explicit"
                            + " native UI compiler export command:"
                            + " /nesql --native-ui-export elysium-dev")
                    .register();

    public static final Option<Boolean> ENABLE_CONFIG_FILE =
            new BooleanOption(
                    Category.OPTIONS, "enable_config_file", false,
                    "Whether to persist these in-game config values to"
                            + " config/NESQL-Exporter.cfg."
                            + "\nWhen false, NESQL++ keeps the built-in Elysium defaults and"
                            + " removes the generated config file after initialization."
                            + "\nEnable only if you need to override export tuning locally.")
                    .register();

    public static final Option<Integer> ICON_DIMENSION =
            new IntegerOption(
                    Category.OPTIONS, "icon_dimension", 64,
                    "Rendered item/fluid icon size in pixels for raw-export browser atlas"
                            + " artifacts."
                            + "\n64 is the Elysium/NeoNEI compiler ABI default."
                            + "\nHas no effect if render_icons is false.")
                    .register();

    public static final Option<Boolean> RENDER_ICONS =
            new BooleanOption(
                    Category.OPTIONS, "render_icons", true,
                    "Whether to render item and fluid icons during export."
                            + "\nMust remain true for /nesql --native-ui-export because the"
                            + " compiler requires browser atlas assets and render manifests.")
                    .register();

    public static final Option<Integer> RENDER_ICONS_PER_TICK =
            new IntegerOption(
                    Category.OPTIONS, "render_icons_per_tick", 512,
                    "Maximum icon render jobs processed per client tick."
                            + "\n512 is the current high-throughput GTNH default used by the"
                            + " native UI/compiler export lane."
                            + "\nLower this only if the client becomes unstable during rendering.")
                    .register();

    public static final Option<Integer> LOGGING_FREQUENCY =
            new IntegerOption(
                    Category.OPTIONS, "logging_frequency", 100,
                    "How often to log export progress, in processed-record intervals."
                            + "\nLower is more frequent; set to <=0 to disable progress logs.")
                    .register();

    // GIF Animation Options
    public static final Option<Boolean> EXPORT_GIF =
            new BooleanOption(
                    Category.OPTIONS, "export_gif", true,
                    "Whether to export animated GIF/atlas frames for items with texture"
                            + " animations."
                            + "\nKeep enabled for compiler-complete animated atlas output."
                            + "\nDisable only for local render debugging.")
                    .register();

    public static final Option<Boolean> EXPORT_FRAMEBUFFER_GIF =
            new BooleanOption(
                    Category.OPTIONS, "export_framebuffer_gif", true,
                    "Whether framebuffer-rendered items may be captured as multi-frame"
                            + " animations."
                            + "\nDefault is true so custom-rendered GTNH animations enter the"
                            + " animated atlas ABI exactly as they appear in NEI; stability is"
                            + " handled by render isolation and diagnostics.")
                    .register();

    public static final Option<Integer> GIF_FRAMES =
            new IntegerOption(
                    Category.OPTIONS, "gif_frames", 8,
                    "Number of frames to capture for animated item/fluid atlas entries."
                            + "\nMore frames increase animation smoothness, export time, and raw"
                            + " artifact size."
                            + "\nRecommended for GTNH native UI export: 8 frames with 6-8GB JVM heap.")
                    .register();

    public static final Option<Integer> GIF_LOOP_COUNT =
            new IntegerOption(
                    Category.OPTIONS, "gif_loop_count", 0,
                    "GIF animation loop count written for compatibility previews."
                            + "\n0 = infinite loop and is the expected NeoNEI/browser preview default.")
                    .register();

    public static final Option<Boolean> FORCE_ALL_ITEMS_ANIMATED =
            new BooleanOption(
                    Category.OPTIONS, "force_all_items_animated", false,
                    "Force every item/fluid through multi-frame capture."
                            + "\nLeave false for normal /nesql --native-ui-export; NESQL++ now"
                            + " detects known animated textures and framebuffer renderers without"
                            + " forcing the entire item set."
                            + "\nUse true only for local animation-discovery diagnostics.")
                    .register();

    public static final Option<String> TEST_MOD_FILTER =
            new StringOption(
                    Category.OPTIONS, "test_mod_filter", "",
                    "Debug-only mod-id filter for item export experiments"
                            + " (for example, Avaritia)."
                            + "\nLeave empty for final Elysium native UI export so all GTNH mods"
                            + " are included.")
                    .register();

    public enum Category {
        OPTIONS("options");

        private final String name;

        Category(String name) {
            this.name = name;
        }

        @Override
        public String toString() {
            return name;
        }
    }

    public abstract static class Option<T> implements Supplier<T> {
        final Category category;
        final String key;
        final T defaultValue;
        final String comment;
        final boolean requiresRestart;

        Property property;

        Option(
                Category category, String key, T defaultValue, String comment,
                boolean requiresRestart) {
            this.category = category;
            this.key = key;
            this.defaultValue = defaultValue;
            this.comment = comment + buildDefaultComment(defaultValue);
            this.requiresRestart = requiresRestart;
        }

        Option(Category category, String key, T defaultValue, String comment) {
            this(category, key, defaultValue, comment, false);
        }

        /** Chain this method right after construction. */
        Option<T> register() {
            allOptions.add(this);
            return this;
        }

        public void initialize() {
            property = getProperty();
            property.setRequiresMcRestart(requiresRestart);

            // Load this option, so that it gets saved if it's missing from the config.
            get();
        }

        /**
         * Sadly, this abstract method is needed because we cannot in-line getting the property in
         * {@link #initialize()} due to type shenanigans.
         */
        abstract Property getProperty();

        @Override
        public abstract T get();
    }

    public static final class BooleanOption extends Option<Boolean> {
        private BooleanOption(Category category, String key, boolean defaultValue, String comment) {
            super(category, key, defaultValue, comment);
        }

        private BooleanOption(
                Category category, String key, boolean defaultValue, String comment,
                boolean requiresRestart) {
            super(category, key, defaultValue, comment, requiresRestart);
        }

        @Override
        Property getProperty() {
            return Config.CONFIG.get(category.toString(), key, defaultValue, comment);
        }

        @Override
        public Boolean get() {
            return property.getBoolean();
        }
    }

    public static final class IntegerOption extends Option<Integer> {
        private IntegerOption(Category category, String key, int defaultValue, String comment) {
            super(category, key, defaultValue, comment);
        }

        private IntegerOption(
                Category category, String key, int defaultValue, String comment,
                boolean requiresRestart) {
            super(category, key, defaultValue, comment, requiresRestart);
        }

        @Override
        Property getProperty() {
            return Config.CONFIG.get(category.toString(), key, defaultValue, comment);
        }

        @Override
        public Integer get() {
            return property.getInt();
        }
    }

    public static final class StringOption extends Option<String> {
        private StringOption(
                Category category, String key, String defaultValue, String comment) {
            super(category, key, defaultValue, comment);
        }

        private StringOption(
                Category category, String key, String defaultValue, String comment,
                boolean requiresRestart) {
            super(category, key, defaultValue, comment, requiresRestart);
        }

        @Override
        Property getProperty() {
            return Config.CONFIG.get(category.toString(), key, defaultValue, comment);
        }

        @Override
        public String get() {
            return property.getString();
        }
    }

    public static final class StringListOption extends Option<List<String>> {
        private StringListOption(
                Category category, String key, List<String> defaultValue, String comment) {
            super(category, key, defaultValue, comment);
        }

        private StringListOption(
                Category category, String key, List<String> defaultValue, String comment,
                boolean requiresRestart) {
            super(category, key, defaultValue, comment, requiresRestart);
        }

        @Override
        Property getProperty() {
            return Config.CONFIG.get(
                    category.toString(), key, defaultValue.toArray(new String[0]), comment);
        }

        @Override
        public List<String> get() {
            return Arrays.asList(property.getStringList());
        }
    }

    // Static class.
    private ConfigOptions() {}

    static void setCategoryComments() {
        Config.CONFIG.setCategoryComment(
                Category.OPTIONS.toString(),
                "NESQL++ raw-export and native UI compiler export options.");
    }

    static ImmutableList<Option<?>> getAllOptions() {
        return ImmutableList.copyOf(allOptions);
    }

    private static String buildDefaultComment(Object defaultValue) {
        return String.format("\nDefault: %s", defaultValue);
    }
}
