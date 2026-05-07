package com.github.dcysteine.nesql.exporter.main;

import net.minecraft.client.Minecraft;
import net.minecraft.command.ICommand;
import net.minecraft.command.ICommandSender;

import java.util.List;

/** Command to export industrial slaughterhouse / EEC mob 3D model contracts plus preview fallbacks. */
final class IndustrialSlaughterhouseEntityExportCommand implements ICommand {
    @Override
    public String getCommandName() {
        return "nesql-eec-models";
    }

    @Override
    public String getCommandUsage(ICommandSender unused) {
        return "/nesql-eec-models [repository suffix]";
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

        IndustrialSlaughterhouseEntityExporter exporter =
                args.length == 1
                        ? new IndustrialSlaughterhouseEntityExporter(args[0])
                        : new IndustrialSlaughterhouseEntityExporter();

        ExportProgressGui gui = new ExportProgressGui();
        gui.clear();
        gui.setTitle("NESQL++ 1.04");
        gui.setSubtitle("EEC Entity Model Export / " + (args.length == 1 ? args[0] : "default"));
        gui.addMessage("Starting industrial slaughterhouse entity 3D model export...");
        gui.addMessage("Export targets: canonical/entity-models.json + canonical/entity-previews.json");

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
