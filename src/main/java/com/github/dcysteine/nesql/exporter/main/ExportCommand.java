package com.github.dcysteine.nesql.exporter.main;

import net.minecraft.command.ICommand;
import net.minecraft.command.ICommandSender;

import java.util.List;

/** Command to export recipes and other data to a file. */
final class ExportCommand implements ICommand {
    @Override
    public String getCommandName() {
        return "nesql";
    }

    @Override
    public String getCommandUsage(ICommandSender unused) {
        return "/nesql [filename suffix]";
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

        String repositoryName = args.length == 1
                ? args[0]
                : com.github.dcysteine.nesql.exporter.main.config.ConfigOptions.REPOSITORY_NAME.get();

        ClientGuiScheduler.open(new ExportSelectionGui(repositoryName));
        Logger.chatMessage("Opened NESQL++ export selection for repository: " + repositoryName);
    }

    static void startSelectedExport(String repositoryName, ExportSelection selection, ExportSelectionGui selectionGui) {
        Exporter exporter = new Exporter(repositoryName, selection);
        ExportProgressGui gui = new ExportProgressGui();
        gui.clear();
        gui.setTitle("NESQL++ 1.04");
        gui.setSubtitle("Selected Export / v1.04 / " + repositoryName);
        gui.addMessage("Starting export with selection: " + selection.describe());

        ClientGuiScheduler.open(gui);

        Thread thread = new Thread(() -> {
            try {
                exporter.exportReportException();
            } finally {
                try {
                    Thread.sleep(3000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                ExportProgressGui.clearActiveGui();
                ClientGuiScheduler.close(gui);
            }
        }, "NESQL++ Export");
        thread.setDaemon(true);
        thread.start();
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
