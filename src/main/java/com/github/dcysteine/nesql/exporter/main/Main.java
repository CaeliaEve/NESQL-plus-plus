package com.github.dcysteine.nesql.exporter.main;

import com.github.dcysteine.nesql.exporter.main.config.Config;
import com.github.dcysteine.nesql.exporter.main.config.ConfigGuiFactory;
import com.github.dcysteine.nesql.exporter.main.config.ConfigOptions;
import com.github.dcysteine.nesql.exporter.util.render.Renderer;
import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.Mod.EventHandler;
import cpw.mods.fml.common.Mod.Instance;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLServerStartingEvent;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.network.FMLNetworkEvent;
import cpw.mods.fml.relauncher.Side;

/** Main entry point for NESQL++. */
@Mod(
        modid = Main.MOD_ID,
        name = Main.MOD_NAME,
        version = Main.MOD_VERSION,
        acceptableRemoteVersions = "*",  // Client-side-only mod.
        dependencies = Main.MOD_DEPENDENCIES,
        guiFactory = ConfigGuiFactory.CLASS_NAME)
public final class Main {
    public static final String MOD_ID = "nesql-exporter";
    public static final String MOD_NAME = "NESQL++";
    public static final String MOD_VERSION = "@version@";
    public static final String MOD_DEPENDENCIES = "required-after:NotEnoughItems;";

    @Instance(MOD_ID)
    @SuppressWarnings("unused")
    public static Main instance;

    @EventHandler
    @SuppressWarnings("unused")
    public void onInitialization(FMLInitializationEvent event) {
        if (event.getSide() != Side.CLIENT) {
            return;
        }
        Logger.MOD.info("Mod initialization starting...");

        ConfigGuiFactory.checkClassName();
        Config.initialize();
        Config.updateConfig();
        FMLCommonHandler.instance().bus().register(Renderer.INSTANCE);
        FMLCommonHandler.instance().bus().register(this);

        Logger.MOD.info("Mod initialization complete!");
    }

    @EventHandler
    @SuppressWarnings("unused")
    public void onServerStart(FMLServerStartingEvent event) {
        if (event.getSide() != Side.CLIENT) {
            return;
        }

        // Register all export commands
        event.registerServerCommand(new ExportCommand());  // Complete export (data + images)
        event.registerServerCommand(new DataExportCommand());  // Data only (no images)
        event.registerServerCommand(new ImageExportCommand());  // Images only (requires existing data)
        event.registerServerCommand(new ThaumcraftDataExportCommand()); // Debug: Thaumcraft-family data only
        event.registerServerCommand(new RenderContractsExportCommand()); // Rebuild render canonical/atlas/index only
        event.registerServerCommand(new AnimatedAtlasExportCommand()); // Animated atlas manifest only
        event.registerServerCommand(new GregTechMultiblockExportCommand()); // GregTech multiblock voxel blueprints
        event.registerServerCommand(new BlockFaceExportCommand()); // 3D block faces only
        event.registerServerCommand(new ThaumcraftUnlockAspectsCommand()); // Unlock scanned item aspects
        Logger.MOD.info("Export commands registered!");
        Logger.MOD.info("  /nesql - Complete export (data + images) [profile={}]", ExportProfile.FULL_V104.profileId);
        Logger.MOD.info("  /nesql-data - Data only (no images, with V14 export) [profile={}]", ExportProfile.DATA_ONLY_V14.profileId);
        Logger.MOD.info("  /nesql-data-thaumcraft - Debug data export for Thaumcraft-family NEI handlers only");
        Logger.MOD.info("  /nesql-images - Images only (requires existing database) [profile={}]", ExportProfile.IMAGES_ONLY.profileId);
        Logger.MOD.info("  /nesql-render-contracts - Rebuild render-assets / animation / atlas / render-index only");
        Logger.MOD.info("  /nesql-animated-atlas - Rebuild animated atlas outputs only");
        Logger.MOD.info("  /nesql-multiblocks - GregTech multiblock voxel blueprints");
        Logger.MOD.info("  /nesql-blockfaces - 3D block face metadata + per-face textures + UV");
        Logger.MOD.info("  /nesql-tc-unlock-aspects - Unlock scanned Thaumcraft item aspects for a player");
    }

    @SubscribeEvent
    @SuppressWarnings("unused")
    public void onClientConnected(FMLNetworkEvent.ClientConnectedToServerEvent event) {
        if (ConfigOptions.AUTO_EXPORT_ON_CONNECT.get()) {
            Logger.MOD.info("Automatically exporting...");
            new Thread(new Exporter()::exportReportException).start();
        }
    }
}
