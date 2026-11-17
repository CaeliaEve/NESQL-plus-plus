package com.github.dcysteine.nesql.exporter.core;

import java.util.Map;
import java.util.HashMap;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.lwjgl.Sys;

/** Only reviewed renderers call these hooks. Outside a capture scope they read
 * the original clock; no game, world, player or global timer is advanced. */
public final class RendererClock {
    private static final ThreadLocal<long[]> phase = new ThreadLocal<>();
    private static final Set<String> installed = ConcurrentHashMap.newKeySet();
    private static final Map<String, Profile> profiles = new HashMap<>();
    static {
        add("micdoodle8.mods.galacticraft.core.client.render.item.ItemRendererTier1Rocket", "GalacticraftCore", "3.3.13-GTNH", 10800, true);
        add("micdoodle8.mods.galacticraft.planets.mars.client.render.item.ItemRendererTier2Rocket", "GalacticraftCore", "3.3.13-GTNH", 10800, true);
        add("micdoodle8.mods.galacticraft.planets.asteroids.client.render.item.ItemRendererTier3Rocket", "GalacticraftCore", "3.3.13-GTNH", 10800, true);
        add("micdoodle8.mods.galacticraft.planets.asteroids.client.render.item.ItemRendererAstroMiner", "GalacticraftCore", "3.3.13-GTNH", 10800, true);
        add("galaxyspace.core.render.item.ItemRendererRocket", "GalaxySpace", "1.1.121-GTNH", 10800, true);
        add("de.katzenpapst.amunra.client.renderer.item.ItemRendererShuttle", "GalacticraftAmunRa", "0.8.2", 10800, true);
        add("com.brandon3055.draconicevolution.client.render.block.RenderEarthItem", "DraconicEvolution", "1.4.27-GTNH", 23040, false);
        add("witchinggadgets.client.render.ItemRenderPrimordialGauntlet", "WitchingGadgets", "1.7.25-GTNH", 1600, false);
        // NEI uses a detached ItemStack, so the native inventory renderer selects
        // its non-held compass branch. Preserve its complete 100000ms modulo.
        add("appeng.client.render.blocks.RenderBlockSkyCompass", "appliedenergistics2", "rv3-beta-695-GTNH", 100000, false);
    }
    private static void add(String name, String mod, String version, long units, boolean sys) {
        profiles.put(name, new Profile(mod, version, units, sys));
    }
    public static Profile profile(String name) { return profiles.get(name); }
    public static boolean instrumented(String name) { return installed.contains(name); }
    public static boolean shaderCallback(String name) {
        return "fox.spiteful.avaritia.render.CosmicRenderShenanigans$1".equals(name)
                || "singulariteam.eternalsingularity.render.CosmicRenderStuffs$1".equals(name);
    }
    static void installed(String name) { installed.add(name); }
    public static boolean active() { return phase.get() != null; }
    public static long sysTime() { long[] value = phase.get(); return value == null ? Sys.getTime() : value[0]; }
    public static long millis() { long[] value = phase.get(); return value == null ? System.currentTimeMillis() : value[1]; }
    public static int entityTick(int nativeTick) { long[] value = phase.get(); return value == null ? nativeTick : (int) value[2]; }
    public static Scope at(long tick, long resolution) {
        if (tick < 0 || resolution <= 0) throw new IllegalArgumentException("Invalid renderer time");
        long[] previous = phase.get();
        phase.set(new long[] {Math.multiplyExact(tick, resolution) / 20, Math.multiplyExact(tick, 50), tick});
        return new Scope(previous);
    }
    public static int cycleTicks(long units, long resolution) {
        if (units <= 0 || resolution <= 0) throw new IllegalArgumentException("Invalid clock period");
        long numerator = Math.multiplyExact(units, 20), divisor = gcd(numerator, resolution);
        long ticks = numerator / divisor;
        if (ticks > 4096) throw new IllegalArgumentException("Native clock exceeds 4096 ticks");
        return (int) ticks;
    }
    private static long gcd(long a, long b) { while (b != 0) { long next = a % b; a = b; b = next; } return a; }
    public static final class Profile {
        public final String mod, version;
        public final long units; public final boolean sys;
        Profile(String mod, String version, long units, boolean sys) { this.mod=mod; this.version=version; this.units=units; this.sys=sys; }
    }
    public static final class Scope implements AutoCloseable {
        private final long[] previous;
        private Scope(long[] previous) { this.previous = previous; }
        @Override public void close() { if (previous == null) phase.remove(); else phase.set(previous); }
    }
}
