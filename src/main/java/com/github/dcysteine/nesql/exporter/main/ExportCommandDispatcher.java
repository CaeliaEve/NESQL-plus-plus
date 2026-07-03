package com.github.dcysteine.nesql.exporter.main;

import net.minecraft.command.ICommandSender;
import net.minecraft.util.EnumChatFormatting;

/** Executes parsed /nesql command requests and owns GUI/thread scheduling. */
final class ExportCommandDispatcher {
    private ExportCommandDispatcher() {}

    static void dispatch(ICommandSender sender, String[] args) {
        ExportCommandParser.ExportCommandParseResult result = ExportCommandParser.parse(args);
        if (!result.ok()) {
            Logger.chatMessage(result.errorMessage);
            return;
        }
        dispatch(result.request);
    }

    static void dispatch(ExportCommandRequest request) {
        if (request.mode == ExportCommandMode.SEMANTIC_CHECK) {
            startSemanticCheck(request.repositoryName);
            return;
        }
        if (request.mode == ExportCommandMode.FULL_EXPORT) {
            Logger.chatMessage(
                    EnumChatFormatting.AQUA
                            + "[NESQL] Starting explicit full export for repository: "
                            + request.repositoryName);
            startSelectedExport(request.repositoryName, ExportSelection.full());
            return;
        }
        if (request.mode == ExportCommandMode.NATIVE_UI_EXPORT) {
            Logger.chatMessage(
                    EnumChatFormatting.AQUA
                            + "[NESQL] Starting native UI compiler export for repository: "
                            + request.repositoryName);
            Logger.chatMessage(
                    EnumChatFormatting.YELLOW
                            + "[NESQL] Rendering browser atlas lanes; skipping multiblock/block-face/database-commit lanes.");
            startNativeUiExport(request.repositoryName);
            return;
        }

        ClientGuiScheduler.open(new ExportSelectionGui(request.repositoryName));
        Logger.chatMessage("Opened NESQL++ export selection for repository: " + request.repositoryName);
    }

    static void startSemanticCheck(String repositoryName) {
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
        startExport(repositoryName, Exporter.nativeUiExport(repositoryName), "Native UI Compiler Export / v1.04-native-ui / ");
    }

    static void startSelectedExport(String repositoryName, ExportSelection selection) {
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
}
