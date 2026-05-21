package com.github.dcysteine.nesql.exporter.main;

import net.minecraft.client.Minecraft;
import net.minecraft.command.ICommand;
import net.minecraft.command.ICommandSender;

import java.util.List;

/** Command to rebuild only browser order/grouping from an existing NESQL++ repository. */
final class BrowserLayoutExportCommand implements ICommand {
    @Override
    public String getCommandName() {
        return "nesql-browser-layout";
    }

    @Override
    public String getCommandUsage(ICommandSender unused) {
        return "/nesql-browser-layout [repository suffix]";
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

        BrowserLayoutExporter exporter = args.length == 1 ? new BrowserLayoutExporter(args[0]) : new BrowserLayoutExporter();

        ExportProgressGui gui = new ExportProgressGui();
        gui.clear();
        gui.setTitle("NESQL++ 1.04");
        gui.setSubtitle("Browser Layout Only / " + (args.length == 1 ? args[0] : "default"));
        gui.addMessage("Rebuilding NEI browser order + collapsible groups...");
        gui.addMessage("Skips recipe data, images, atlases, multiblocks and snapshots.");
        gui.addMessage("Export target: canonical/browser-layout-index.json");

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
        }, "NESQL++ Browser Layout Export").start();
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

    public int compareTo(ICommand other) {
        if (other == null) {
            return 1;
        }
        return this.getCommandName().compareTo(other.getCommandName());
    }

    @Override
    public int compareTo(Object other) {
        if (other instanceof ICommand) {
            return compareTo((ICommand) other);
        }
        return 0;
    }
}
