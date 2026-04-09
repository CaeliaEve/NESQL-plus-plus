package com.github.dcysteine.nesql.exporter.main;

import net.minecraft.client.Minecraft;
import net.minecraft.command.ICommand;
import net.minecraft.command.ICommandSender;

import java.util.List;

/** Command to rebuild animated atlas outputs from an existing NESQL++ repository. */
final class AnimatedAtlasExportCommand implements ICommand {
    @Override
    public String getCommandName() {
        return "nesql-animated-atlas";
    }

    @Override
    public String getCommandUsage(ICommandSender unused) {
        return "/nesql-animated-atlas [repository suffix]";
    }

    @Override
    @SuppressWarnings("rawtypes")
    public List getCommandAliases() {
        return null;
    }

    @Override
    public void processCommand(ICommandSender sender, String[] args) {
        if (args.length > 1) {
            Logger.chatMessage("Too many parameters! Usage: " + getCommandUsage(sender));
            return;
        }

        AnimatedAtlasExporter exporter = args.length == 1 ? new AnimatedAtlasExporter(args[0]) : new AnimatedAtlasExporter();

        ExportProgressGui gui = new ExportProgressGui();
        gui.clear();
        gui.setTitle("NESQL++ 1.04");
        gui.setSubtitle("Animated Atlas Rebuild / " + (args.length == 1 ? args[0] : "default"));
        gui.addMessage("Rebuilding animated atlas outputs...");
        gui.addMessage("Reuses existing data + rendered images only.");
        gui.addMessage("Export target: canonical/animated-atlas-manifest.json");

        Minecraft.getMinecraft().displayGuiScreen(gui);

        new Thread(() -> {
            try {
                exporter.exportReportException();
            } finally {
                try {
                    Thread.sleep(3000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                ExportProgressGui.clearActiveGui();
                Minecraft.getMinecraft().displayGuiScreen(null);
            }
        }).start();
    }

    @Override
    public boolean canCommandSenderUseCommand(ICommandSender unused) {
        return true;
    }

    @Override
    @SuppressWarnings("rawtypes")
    public List addTabCompletionOptions(ICommandSender unused, String[] args) {
        return null;
    }

    @Override
    public boolean isUsernameIndex(String[] strings, int i) {
        return false;
    }

    @Override
    public int compareTo(Object other) {
        if (other instanceof ICommand) {
            return this.getCommandName().compareTo(((ICommand) other).getCommandName());
        }
        return 0;
    }
}
