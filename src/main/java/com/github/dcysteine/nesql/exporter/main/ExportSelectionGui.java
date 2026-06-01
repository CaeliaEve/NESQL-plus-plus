package com.github.dcysteine.nesql.exporter.main;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;

import java.util.ArrayList;
import java.util.List;

/** Preflight checkbox screen for the default /nesql full export command. */
public final class ExportSelectionGui extends GuiScreen {
    private static final int START_BUTTON_ID = 1;
    private static final int ALL_BUTTON_ID = 2;
    private static final int DATA_BUTTON_ID = 3;
    private static final int CANCEL_BUTTON_ID = 4;

    private final String repositoryName;
    private final List<Option> options = new ArrayList<>();
    private int panelLeft;
    private int panelTop;
    private int panelWidth;
    private int optionLeft;
    private int optionTop;
    private int optionWidth;
    private final int optionHeight = 22;

    public ExportSelectionGui(String repositoryName) {
        this.repositoryName = repositoryName;
        options.add(new Option("Core items", "Item and fluid facts", true));
        options.add(new Option("Recipes", "Crafting, machine and NEI handlers", true));
        options.add(new Option("Debug canonical snapshot", "Legacy compatibility files; off for lean raw-export", false));
        options.add(new Option("GT blueprints", "Multiblock structure contracts", true));
        options.add(new Option("Block faces", "3D block face and UV metadata", true));
        options.add(new Option("Item rendering", "Static icons and animation frames", true));
        options.add(new Option("Render manifests", "Asset, animation and render indexes", true));
        options.add(new Option("Static atlas", "Browser atlas pages", true));
        options.add(new Option("Animated atlas", "Native sprite timing and GIF atlas", true));
        options.add(new Option("Browser layout", "NEI order, groups and atlas lookup", true));
        options.add(new Option("Database commit", "Legacy SQL compatibility output", true));
    }

    @Override
    @SuppressWarnings("unchecked")
    public void initGui() {
        buttonList.clear();
        panelWidth = Math.min(560, width - 28);
        panelLeft = (width - panelWidth) / 2;
        panelTop = 16;
        int panelBottom = height - 18;
        optionLeft = panelLeft + 22;
        optionTop = panelTop + 78;
        optionWidth = panelWidth - 44;

        buttonList.add(new GuiButton(START_BUTTON_ID, panelLeft + panelWidth - 116, panelBottom - 26, 104, 20, "Begin Export"));
        buttonList.add(new GuiButton(ALL_BUTTON_ID, panelLeft + 12, panelBottom - 26, 78, 20, "All"));
        buttonList.add(new GuiButton(DATA_BUTTON_ID, panelLeft + 96, panelBottom - 26, 92, 20, "Data Only"));
        buttonList.add(new GuiButton(CANCEL_BUTTON_ID, panelLeft + 194, panelBottom - 26, 76, 20, "Cancel"));
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        switch (button.id) {
            case START_BUTTON_ID:
                startExport();
                return;
            case ALL_BUTTON_ID:
                setAll(true);
                return;
            case DATA_BUTTON_ID:
                selectDataOnly();
                return;
            case CANCEL_BUTTON_ID:
                mc.displayGuiScreen(null);
                return;
            default:
        }
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) {
        try {
            super.mouseClicked(mouseX, mouseY, mouseButton);
        } catch (Exception ignored) {
            // GuiScreen implementations in this MC/Forge line do not need checked handling.
        }
        if (mouseButton != 0) {
            return;
        }

        for (int i = 0; i < options.size(); i++) {
            int y = optionTop + i * optionHeight;
            if (mouseX >= optionLeft
                    && mouseX <= optionLeft + optionWidth
                    && mouseY >= y
                    && mouseY <= y + optionHeight - 4) {
                options.get(i).enabled = !options.get(i).enabled;
                normalizeDependencies();
                return;
            }
        }
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawDefaultBackground();
        if (fontRendererObj == null) {
            return;
        }

        panelWidth = Math.min(560, width - 28);
        panelLeft = (width - panelWidth) / 2;
        panelTop = 16;
        int panelBottom = height - 18;
        optionLeft = panelLeft + 22;
        optionTop = panelTop + 78;
        optionWidth = panelWidth - 44;

        drawRect(panelLeft - 2, panelTop - 2, panelLeft + panelWidth + 2, panelBottom + 2, 0xEA050A10);
        drawRect(panelLeft, panelTop, panelLeft + panelWidth, panelBottom, 0xDE0D151E);
        drawRect(panelLeft, panelTop, panelLeft + panelWidth, panelTop + 2, 0xFF57E2B2);
        drawRect(panelLeft + 1, panelTop + 2, panelLeft + panelWidth - 1, panelTop + 38, 0x371D6C7A);
        drawRect(panelLeft + 14, panelTop + 48, panelLeft + panelWidth - 14, panelTop + 49, 0x66425E73);

        drawString(fontRendererObj, "NESQL++ EXPORT", panelLeft + 22, panelTop + 12, 0xF8FCFF);
        drawString(fontRendererObj, "Repository", panelLeft + panelWidth - 178, panelTop + 10, 0x86B8CA);
        drawString(fontRendererObj, repositoryName, panelLeft + panelWidth - 178, panelTop + 22, 0xEDE7C5);
        drawString(
                fontRendererObj,
                "Select the data lanes for this run. Recommended full export writes raw-export without legacy canonical output.",
                panelLeft + 22,
                panelTop + 56,
                0x9CC7D8);

        for (int i = 0; i < options.size(); i++) {
            Option option = options.get(i);
            int y = optionTop + i * optionHeight;
            boolean hovered = mouseX >= optionLeft
                    && mouseX <= optionLeft + optionWidth
                    && mouseY >= y
                    && mouseY <= y + optionHeight - 4;
            int rowColor = hovered ? 0x55304C5B : (i % 2 == 0 ? 0x3315222C : 0x22101A22);
            drawRect(optionLeft, y - 2, optionLeft + optionWidth, y + optionHeight - 4, rowColor);
            drawRect(optionLeft, y - 2, optionLeft + 2, y + optionHeight - 4, option.enabled ? 0xFF57E2B2 : 0xFF34424C);

            int boxLeft = optionLeft + 8;
            drawRect(boxLeft, y + 2, boxLeft + 12, y + 14, 0xFF071017);
            drawRect(boxLeft + 1, y + 3, boxLeft + 11, y + 13, option.enabled ? 0xFF57E2B2 : 0xFF26313B);
            if (option.enabled) {
                drawString(fontRendererObj, "x", boxLeft + 4, y + 4, 0xFF061018);
            }
            drawString(fontRendererObj, option.label, optionLeft + 28, y + 2, option.enabled ? 0xF6FBFF : 0x8796A2);
            drawString(fontRendererObj, option.description, optionLeft + 178, y + 2, 0x8FB2C3);
        }

        drawString(
                fontRendererObj,
                "Tip: Debug canonical is opt-in only. Keep it off for smaller NeoNEI raw-export packages.",
                panelLeft + 22,
                panelBottom - 48,
                0x7FAABB);

        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }

    private void startExport() {
        normalizeDependencies();
        ExportSelection selection = ExportSelection.builder()
                .writeItems(options.get(0).enabled)
                .writeRecipes(options.get(1).enabled)
                .writeCanonicalSnapshot(options.get(2).enabled)
                .writeMultiblocks(options.get(3).enabled)
                .writeBlockFaces(options.get(4).enabled)
                .renderImages(options.get(5).enabled)
                .writeRenderManifests(options.get(6).enabled)
                .writeAtlasPacks(options.get(7).enabled)
                .writeAnimatedAtlasPacks(options.get(8).enabled)
                .writeBrowserIndexes(options.get(9).enabled)
                .commitDatabase(options.get(10).enabled)
                .build();
        ExportCommand.startSelectedExport(repositoryName, selection, this);
    }

    private void setAll(boolean value) {
        for (Option option : options) {
            option.enabled = value;
        }
    }

    private void selectDataOnly() {
        setAll(false);
        options.get(0).enabled = true;
        options.get(1).enabled = true;
        options.get(9).enabled = true;
        options.get(10).enabled = false;
    }

    private void normalizeDependencies() {
        if (!options.get(5).enabled) {
            options.get(6).enabled = false;
            options.get(7).enabled = false;
            options.get(8).enabled = false;
        }
        if (!options.get(0).enabled) {
            options.get(9).enabled = false;
        }
    }

    private static final class Option {
        private final String label;
        private final String description;
        private boolean enabled;

        private Option(String label, String description, boolean enabled) {
            this.label = label;
            this.description = description;
            this.enabled = enabled;
        }
    }
}
