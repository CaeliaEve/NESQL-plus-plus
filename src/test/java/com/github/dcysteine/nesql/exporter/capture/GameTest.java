package com.github.dcysteine.nesql.exporter.capture;

import cpw.mods.fml.common.Loader;
import net.minecraft.init.Bootstrap;
import net.minecraft.launchwrapper.LaunchClassLoader;

import java.util.Arrays;
import java.util.Collections;

/** One isolated native registry for capture regressions; no game client or world is started. */
public final class GameTest {
    private GameTest() {}

    public static void run() throws Exception {
        if (!(GameTest.class.getClassLoader() instanceof LaunchClassLoader)) {
            java.net.URL[] urls = Arrays.stream(System.getProperty("java.class.path").split(java.io.File.pathSeparator))
                    .map(java.io.File::new).map(java.io.File::toURI).map(uri -> {
                        try { return uri.toURL(); } catch (java.net.MalformedURLException error) { throw new IllegalStateException(error); }
                    }).toArray(java.net.URL[]::new);
            ClassLoader previous = Thread.currentThread().getContextClassLoader();
            java.io.PrintStream out = System.out, err = System.err;
            try (LaunchClassLoader loader = new LaunchClassLoader(urls)) {
                Thread.currentThread().setContextClassLoader(loader);
                try { loader.loadClass(GameTest.class.getName()).getMethod("run").invoke(null); }
                catch (java.lang.reflect.InvocationTargetException error) {
                    if (error.getCause() instanceof Error) throw (Error) error.getCause();
                    throw (Exception) error.getCause();
                }
            } finally {
                Thread.currentThread().setContextClassLoader(previous);
                System.setOut(out); System.setErr(err);
            }
            return;
        }
        Loader.injectData("7", "99", "40", "1614", "1.7.10", "9.05", new java.io.File("."), Collections.emptyList());
        cpw.mods.fml.relauncher.ReflectionHelper.setPrivateValue(cpw.mods.fml.relauncher.FMLRelaunchLog.class, null,
                cpw.mods.fml.relauncher.Side.CLIENT, "side");
        Bootstrap.func_151354_b();
        CluesTest.run();
        StructuresTest.run();
    }
}
