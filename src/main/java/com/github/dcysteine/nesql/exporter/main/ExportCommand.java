package com.github.dcysteine.nesql.exporter.main;

import net.minecraft.command.ICommand;
import net.minecraft.command.ICommandSender;
import net.minecraft.util.EnumChatFormatting;

import java.util.List;

/** Command to export recipes and other data to a file. */
final class ExportCommand implements ICommand {
    @Override
    public String getCommandName() {
        return "nesql";
    }

    @Override
    public String getCommandUsage(ICommandSender unused) {
        return "/nesql [filename suffix] [--semantic-check|--full-export|--native-ui-export]";
    }

    @Override
    @SuppressWarnings("rawtypes")
    public List getCommandAliases() {
        return null;
    }

    @Override
    public void processCommand(ICommandSender sender, String[] args) {
        if (args.length > 2) {
            Logger.chatMessage("Too many parameters! Usage: " + getCommandUsage(sender));
            return;
        }

        boolean semanticCheck = false;
        boolean fullExport = false;
        boolean nativeUiExport = false;
        String repositoryName = null;
        for (String arg : args) {
            if ("--semantic-check".equalsIgnoreCase(arg) || "--semantic-only".equalsIgnoreCase(arg)) {
                semanticCheck = true;
            } else if ("--full-export".equalsIgnoreCase(arg) || "--full".equalsIgnoreCase(arg)) {
                fullExport = true;
            } else if ("--native-ui-export".equalsIgnoreCase(arg) || "--native-ui".equalsIgnoreCase(arg)) {
                nativeUiExport = true;
            } else if (repositoryName == null) {
                repositoryName = arg;
            } else {
                Logger.chatMessage("Too many parameters! Usage: " + getCommandUsage(sender));
                return;
            }
        }

        if (repositoryName == null || repositoryName.trim().length() == 0) {
            repositoryName = com.github.dcysteine.nesql.exporter.main.config.ConfigOptions.REPOSITORY_NAME.get();
        }

        int modeCount = (semanticCheck ? 1 : 0) + (fullExport ? 1 : 0) + (nativeUiExport ? 1 : 0);
        if (modeCount > 1) {
            Logger.chatMessage("Choose only one of --semantic-check, --full-export, or --native-ui-export.");
            return;
        }

        if (semanticCheck) {
            startSemanticCheck(repositoryName);
            return;
        }

        if (fullExport) {
            Logger.chatMessage(
                    EnumChatFormatting.AQUA
                            + "[NESQL] Starting explicit full export for repository: "
                            + repositoryName);
            startSelectedExport(repositoryName, ExportSelection.full());
            return;
        }

        if (nativeUiExport) {
            Logger.chatMessage(
                    EnumChatFormatting.AQUA
                            + "[NESQL] Starting fast native UI export for repository: "
                            + repositoryName);
            Logger.chatMessage(
                    EnumChatFormatting.YELLOW
                            + "[NESQL] Skipping render/atlas/database-commit lanes; use --full-export for full assets.");
            startNativeUiExport(repositoryName);
            return;
        }

        String finalRepositoryName = repositoryName;
        ClientGuiScheduler.open(new ExportSelectionGui(finalRepositoryName));
        Logger.chatMessage("Opened NESQL++ export selection for repository: " + finalRepositoryName);
    }

    private static void startSemanticCheck(String repositoryName) {
        final String finalRepositoryName = repositoryName;
        Logger.chatMessage(
                EnumChatFormatting.AQUA
                        + "[NESQL] Starting quick semantic JSONL check for repository: "
                        + finalRepositoryName);
        Thread thread = new Thread(() -> {
            try {
                SemanticIdentityQuickCheckRunner.run(finalRepositoryName);
            } catch (Exception e) {
                Logger.MOD.error("NESQL++ quick semantic JSONL check failed", e);
                Logger.chatMessage(
                        EnumChatFormatting.RED
                                + "[NESQL] Quick semantic JSONL check failed: "
                                + e.getMessage());
            }
        }, "NESQL++ Semantic Check");
        thread.setDaemon(true);
        thread.start();
    }

    static void startNativeUiExport(String repositoryName) {
        startExport(repositoryName, Exporter.nativeUiExport(repositoryName), "Native UI Fast Export / v1.04-data / ");
    }

    static void startSelectedExport(String repositoryName, ExportSelection selection, ExportSelectionGui selectionGui) {
        Exporter exporter = new Exporter(repositoryName, selection);
        startExport(repositoryName, exporter, "Selected Export / v1.04 / ");
    }

    private static void startExport(String repositoryName, Exporter exporter, String subtitlePrefix) {
        ExportProgressGui gui = new ExportProgressGui();
        gui.clear();
        gui.setTitle("NESQL++ 1.04");
        gui.setSubtitle(subtitlePrefix + repositoryName);
        gui.addMessage("Starting export...");

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

    /*
     * Compatibility overload kept for older call sites compiled against the
     * pre-GUI command surface.
     */
    static void startSelectedExport(String repositoryName, ExportSelection selection) {
        startSelectedExport(repositoryName, selection, null);
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
