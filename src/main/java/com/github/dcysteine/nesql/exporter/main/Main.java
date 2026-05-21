package com.github.dcysteine.nesql.exporter.main;

import com.github.dcysteine.nesql.exporter.main.config.Config;
import com.github.dcysteine.nesql.exporter.main.config.ConfigGuiFactory;
import com.github.dcysteine.nesql.exporter.main.config.ConfigOptions;
import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.Mod.EventHandler;
import cpw.mods.fml.common.Mod.Instance;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLServerStartingEvent;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.network.FMLNetworkEvent;
import cpw.mods.fml.relauncher.Side;
import net.minecraftforge.client.ClientCommandHandler;
import net.minecraft.command.ICommand;

import java.lang.reflect.Method;

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
        ClientGuiScheduler.initialize();
        if (ConfigOptions.AUTO_EXPORT_ON_CONNECT.get()) {
            FMLCommonHandler.instance().bus().register(this);
            Logger.MOD.info("Auto-export on connect is enabled; connection listener registered.");
        }

        registerClientCommands();

        Logger.MOD.info("Mod initialization complete!");
    }

    @EventHandler
    @SuppressWarnings("unused")
    public void onServerStart(FMLServerStartingEvent event) {
        if (event.getSide() != Side.CLIENT) {
            return;
        }

        registerServerCommand(event, new ExportCommand(), "/nesql");
        registerServerCommand(event, new DataExportCommand(), "/nesql-data");
        registerServerCommand(event, new ImageExportCommand(), "/nesql-images");
        registerServerCommand(event, new ThaumcraftDataExportCommand(), "/nesql-data-thaumcraft");
        registerServerCommand(event, new BotaniaDataExportCommand(), "/nesql-data-botania");
        registerServerCommand(event, new RenderContractsExportCommand(), "/nesql-render-contracts");
        registerServerCommand(event, new AnimatedAtlasExportCommand(), "/nesql-animated-atlas");
        registerServerCommand(event, new GregTechMultiblockExportCommand(), "/nesql-multiblocks");
        registerServerCommand(event, new BlockFaceExportCommand(), "/nesql-blockfaces");
        registerServerCommand(event, new IndustrialSlaughterhouseEntityExportCommand(), "/nesql-eec-models");
        registerServerCommand(event, new BrowserLayoutExportCommand(), "/nesql-browser-layout");
        event.registerServerCommand(new ThaumcraftUnlockAspectsCommand()); // Unlock scanned item aspects
        Logger.MOD.info("Server-side command registered: /nesql-tc-unlock-aspects");
        Logger.MOD.info("  /nesql-tc-unlock-aspects - Unlock scanned Thaumcraft item aspects for a player");
    }

    private void registerClientCommands() {
        registerOptionalClientCommand(new ExportCommand(), "/nesql"); // Complete export (data + images)
        registerOptionalClientCommand(new DataExportCommand(), "/nesql-data"); // Data only (no images)
        registerOptionalClientCommand(new ImageExportCommand(), "/nesql-images"); // Images only (requires existing data)
        registerOptionalClientCommand(new ThaumcraftDataExportCommand(), "/nesql-data-thaumcraft"); // Debug: Thaumcraft-family data only
        registerOptionalClientCommand(new BotaniaDataExportCommand(), "/nesql-data-botania"); // Debug: Botania-family data only
        registerOptionalClientCommand(new RenderContractsExportCommand(), "/nesql-render-contracts"); // Rebuild render canonical/atlas/index only
        registerOptionalClientCommand(new AnimatedAtlasExportCommand(), "/nesql-animated-atlas"); // Animated atlas manifest only
        registerOptionalClientCommand(new GregTechMultiblockExportCommand(), "/nesql-multiblocks"); // GregTech multiblock voxel blueprints
        registerOptionalClientCommand(new BlockFaceExportCommand(), "/nesql-blockfaces"); // 3D block faces only
        registerOptionalClientCommand(new IndustrialSlaughterhouseEntityExportCommand(), "/nesql-eec-models"); // EEC mob preview models
        registerOptionalClientCommand(new BrowserLayoutExportCommand(), "/nesql-browser-layout"); // NEI browser order + collapsible groups only

        Logger.MOD.info("Client export command registration finished.");
        Logger.MOD.info("  /nesql - Complete export (data + images) [profile={}]", ExportProfile.FULL_V104.profileId);
        Logger.MOD.info("  /nesql-data - Data only (no images, v1.04 indexed export) [profile={}]", ExportProfile.DATA_ONLY_V104.profileId);
        Logger.MOD.info("  /nesql-data-thaumcraft - Debug data export for Thaumcraft-family NEI handlers only");
        Logger.MOD.info("  /nesql-data-botania - Debug data export for Botania-family NEI handlers only");
        Logger.MOD.info("  /nesql-images - Images only (requires existing data) [profile={}]", ExportProfile.IMAGES_ONLY.profileId);
        Logger.MOD.info("  /nesql-render-contracts - Rebuild render-assets / animation / atlas / render-index only");
        Logger.MOD.info("  /nesql-animated-atlas - Rebuild animated atlas outputs only");
        Logger.MOD.info("  /nesql-multiblocks - GregTech multiblock voxel blueprints");
        Logger.MOD.info("  /nesql-blockfaces - 3D block face metadata + per-face textures + UV");
        Logger.MOD.info("  /nesql-eec-models - Industrial slaughterhouse / EEC 3D entity models + preview fallbacks");
        Logger.MOD.info("  /nesql-browser-layout - Rebuild NEI browser order + collapsible groups only");
    }

    private void registerServerCommand(FMLServerStartingEvent event, ICommand command, String label) {
        event.registerServerCommand(command);
        Logger.MOD.info("Server-side export command registered: {}", label);
    }

    private void registerOptionalClientCommand(ICommand command, String label) {
        try {
            registerClientCommand(command);
            Logger.MOD.info("Client-side export command registered: {}", label);
        } catch (Throwable t) {
            Logger.MOD.warn(
                    "Client-side registration unavailable for {}. Falling back to in-world server command registration.",
                    label);
            Logger.MOD.debug("Client-side registration failure for {}", label, t);
        }
    }

    private void registerClientCommand(ICommand command) {
        Object handler = ClientCommandHandler.instance;
        Method registerMethod = findClientCommandRegisterMethod(handler.getClass());
        if (registerMethod == null) {
            throw new IllegalStateException("Failed to locate client command registration method on ClientCommandHandler");
        }

        try {
            registerMethod.setAccessible(true);
            registerMethod.invoke(handler, command);
        } catch (Exception e) {
            throw new RuntimeException("Failed to register client command: " + command.getClass().getName(), e);
        }
    }

    private Method findClientCommandRegisterMethod(Class<?> handlerClass) {
        Class<?> current = handlerClass;
        while (current != null) {
            for (Method method : current.getDeclaredMethods()) {
                Class<?>[] parameterTypes = method.getParameterTypes();
                if (parameterTypes.length == 1 && ICommand.class.isAssignableFrom(parameterTypes[0])) {
                    return method;
                }
            }
            current = current.getSuperclass();
        }
        return null;
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


