package com.github.dcysteine.nesql.exporter.main;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import cpw.mods.fml.relauncher.Side;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

/** Client-tick GUI dispatcher for command handlers that may run off the render thread. */
final class ClientGuiScheduler {
    private static final Queue<Runnable> TASKS = new ConcurrentLinkedQueue<>();
    private static boolean registered;

    private ClientGuiScheduler() {}

    static void initialize() {
        if (registered) {
            return;
        }
        registered = true;
        FMLCommonHandler.instance().bus().register(new ClientGuiScheduler());
    }

    static void open(GuiScreen screen) {
        enqueue(() -> {
            Minecraft minecraft = Minecraft.getMinecraft();
            if (minecraft != null) {
                minecraft.displayGuiScreen(screen);
            }
        });
    }

    static void close(GuiScreen expectedScreen) {
        enqueue(() -> {
            Minecraft minecraft = Minecraft.getMinecraft();
            if (minecraft == null) {
                return;
            }
            if (expectedScreen == null || minecraft.currentScreen == expectedScreen) {
                minecraft.displayGuiScreen(null);
            }
        });
    }

    static void enqueue(Runnable task) {
        if (task != null) {
            TASKS.add(task);
        }
    }

    @SubscribeEvent
    @SuppressWarnings("unused")
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.side != Side.CLIENT || event.phase != TickEvent.Phase.END) {
            return;
        }

        Runnable task;
        while ((task = TASKS.poll()) != null) {
            try {
                task.run();
            } catch (Throwable t) {
                Logger.MOD.error("Failed to run queued NESQL++ client GUI task", t);
            }
        }
    }
}
