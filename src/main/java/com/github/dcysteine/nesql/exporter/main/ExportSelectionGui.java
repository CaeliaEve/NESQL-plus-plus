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
    private int optionLeft;
    private int optionTop;
    private int optionWidth;
    private int optionHeight = 18;

    public ExportSelectionGui(String repositoryName) {
        this.repositoryName = repositoryName;
        options.add(new Option("基础物品数据", "items/{modId}/items.json", true));
        options.add(new Option("配方数据", "recipes/crafting/{modId}/recipes.json.gz", true));
        options.add(new Option("Canonical 快照", "NeoNEI 统一读取快照", true));
        options.add(new Option("GT 多方块蓝图", "multiblock blueprint 数据", true));
        options.add(new Option("方块面元数据", "3D block face / UV 元数据", true));
        options.add(new Option("物品贴图渲染", "静态图片与动画帧渲染", true));
        options.add(new Option("渲染清单", "render-assets / animation manifest / render index", true));
        options.add(new Option("静态 Atlas", "browser item atlas pages", true));
        options.add(new Option("动画 Atlas", "animated atlas pages", true));
        options.add(new Option("浏览区索引", "排序 / 分组 / atlas lookup index", true));
        options.add(new Option("保存数据库", "完整 /nesql 兼容提交", true));
    }

    @Override
    @SuppressWarnings("unchecked")
    public void initGui() {
        buttonList.clear();
        int panelWidth = Math.min(500, width - 32);
        int panelLeft = (width - panelWidth) / 2;
        int panelBottom = height - 24;
        optionLeft = panelLeft + 22;
        optionTop = 72;
        optionWidth = panelWidth - 44;

        buttonList.add(new GuiButton(START_BUTTON_ID, panelLeft + panelWidth - 104, panelBottom - 22, 96, 20, "开始导出"));
        buttonList.add(new GuiButton(ALL_BUTTON_ID, panelLeft + 8, panelBottom - 22, 78, 20, "全选"));
        buttonList.add(new GuiButton(DATA_BUTTON_ID, panelLeft + 90, panelBottom - 22, 90, 20, "仅数据"));
        buttonList.add(new GuiButton(CANCEL_BUTTON_ID, panelLeft + 184, panelBottom - 22, 72, 20, "取消"));
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
                    && mouseY <= y + optionHeight - 2) {
                options.get(i).enabled = !options.get(i).enabled;
                normalizeDependencies();
                return;
            }
        }
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawDefaultBackground();
        int panelWidth = Math.min(500, width - 32);
        int panelLeft = (width - panelWidth) / 2;
        int panelTop = 22;
        int panelBottom = height - 24;

        drawRect(panelLeft - 1, panelTop - 1, panelLeft + panelWidth + 1, panelBottom + 1, 0xE6081118);
        drawRect(panelLeft, panelTop, panelLeft + panelWidth, panelBottom, 0xDD101922);
        drawRect(panelLeft, panelTop, panelLeft + panelWidth, panelTop + 2, 0xFF49D38A);
        drawCenteredString(fontRendererObj, "NESQL++ 导出选择", width / 2, panelTop + 12, 0xF8FCFF);
        drawCenteredString(fontRendererObj, "/nesql " + repositoryName + "  ·  默认全选等价旧全量导出", width / 2, panelTop + 28, 0x8FD3E7);
        drawString(fontRendererObj, "勾选本次需要输出的模块：", optionLeft, panelTop + 48, 0xEDE7C5);

        for (int i = 0; i < options.size(); i++) {
            Option option = options.get(i);
            int y = optionTop + i * optionHeight;
            int rowColor = i % 2 == 0 ? 0x331B2B38 : 0x22131D26;
            drawRect(optionLeft, y - 2, optionLeft + optionWidth, y + optionHeight - 2, rowColor);
            drawRect(optionLeft + 5, y + 2, optionLeft + 17, y + 14, 0xFF0A1118);
            drawRect(optionLeft + 6, y + 3, optionLeft + 16, y + 13, option.enabled ? 0xFF49D38A : 0xFF26313B);
            drawString(fontRendererObj, option.enabled ? "✓" : "", optionLeft + 8, y + 4, 0xFF061018);
            drawString(fontRendererObj, option.label, optionLeft + 24, y + 3, option.enabled ? 0xF6FBFF : 0x8897A2);
            drawString(fontRendererObj, option.description, optionLeft + 170, y + 3, 0x8FB2C3);
        }

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
        options.get(2).enabled = true;
        options.get(9).enabled = true;
        options.get(10).enabled = false;
    }

    private void normalizeDependencies() {
        if (!options.get(5).enabled) {
            options.get(6).enabled = false;
            options.get(7).enabled = false;
            options.get(8).enabled = false;
        } else {
            options.get(2).enabled = true;
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
