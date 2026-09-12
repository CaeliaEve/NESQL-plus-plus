package com.github.dcysteine.nesql.exporter.main;

import com.github.dcysteine.nesql.exporter.task.Exports;
import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.relauncher.Side;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.IReloadableResourceManager;
import net.minecraftforge.client.ClientCommandHandler;
import org.apache.logging.log4j.LogManager;

/** Client-only local export service for the pinned GTNH target. */
@Mod(modid = Main.MOD_ID, name = Main.MOD_NAME, version = Main.MOD_VERSION,
        acceptableRemoteVersions = "*", dependencies = Main.MOD_DEPENDENCIES)
public final class Main {
    public static final String MOD_ID = "nesql-exporter";
    public static final String MOD_NAME = "NESQL++";
    public static final String MOD_VERSION = "@version@";
    public static final String MOD_DEPENDENCIES = "required-after:NotEnoughItems;required-after:gregtech;required-after:Forestry;required-after:structurelib;required-after:blockrenderer6343;required-after:Thaumcraft;";

    @Mod.EventHandler
    public void initialize(FMLInitializationEvent event) {
        if (event.getSide() != Side.CLIENT) return;
        try {
            Minecraft game = Minecraft.getMinecraft();
            Exports exports = new Exports(game.mcDataDir.toPath());
            FMLCommonHandler.instance().bus().register(exports.client);
            ((IReloadableResourceManager) game.getResourceManager()).registerReloadListener(exports.client);
            ClientCommandHandler.instance.registerCommand(new ExportCommand(exports));
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                try { exports.close(); }
                catch (Exception error) { LogManager.getLogger(MOD_NAME).error("Failed to close local exports", error); }
            }, "NESQL shutdown"));
            LogManager.getLogger(MOD_NAME).info("Local MCP export service ready. Load a single-player world and use /nesql or the MCP bridge.");
        } catch (Exception error) {
            throw new IllegalStateException("Cannot initialize NESQL local exports", error);
        }
    }
}
