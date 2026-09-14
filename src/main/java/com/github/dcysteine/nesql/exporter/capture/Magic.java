package com.github.dcysteine.nesql.exporter.capture;

import com.github.dcysteine.nesql.exporter.source.Identity;
import com.github.dcysteine.nesql.exporter.task.Jobs;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import cpw.mods.fml.common.Loader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.renderer.entity.RenderItem;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraftforge.oredict.OreDictionary;
import thaumcraft.api.ThaumcraftApi;
import thaumcraft.api.aspects.Aspect;
import thaumcraft.api.aspects.AspectList;
import thaumcraft.api.research.ResearchCategories;
import thaumcraft.api.research.ResearchItem;
import thaumcraft.client.lib.UtilsFX;
import thaumcraft.common.Thaumcraft;
import thaumcraft.common.lib.research.PlayerKnowledge;
import thaumcraft.common.lib.utils.InventoryUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.TreeMap;

import static com.github.dcysteine.nesql.exporter.source.Json.*;

/** Public Thaumcraft facts and observed client knowledge. No research completion or scanning actions. */
final class Magic {
    private final List<Aspect> aspects;
    private final List<ResearchItem> research;
    private final Studies studies;
    private Clues clues;
    private final String player = Minecraft.getMinecraft().thePlayer.getCommandSenderName();
    private final PlayerKnowledge knowledge = Thaumcraft.proxy.getPlayerKnowledge();

    Magic() {
        if (!Loader.isModLoaded("Thaumcraft")) throw new Jobs.Fault("magic_missing", "The target requires Thaumcraft");
        aspects = new ArrayList<>(new TreeMap<>(Aspect.aspects).values());
        if (aspects.isEmpty() || aspects.size() > 4096) throw new Jobs.Fault("aspect_registry", "Aspect registry is unavailable or exceeds its budget");
        studies = Studies.capture();
        research = studies.all();
    }

    int aspectCount() { return aspects.size(); }
    int researchCount() { return research.size(); }

    void aspect(int index, Facts facts) {
        Aspect aspect = aspects.get(index);
        JsonArray components = new JsonArray();
        if (!aspect.isPrimal()) {
            Aspect[] parts = aspect.getComponents();
            if (parts == null || parts.length != 2) throw new Jobs.Fault("aspect_components", "Compound aspect has no component pair");
            for (Aspect part : parts) components.add(value(Aspects.id(part)));
        }
        AspectList known = knowledge.aspectsDiscovered.get(player);
        JsonObject record = object("id", Aspects.id(aspect), "source", Aspects.source(aspect),
                "name", facts.text(aspect.getName()), "description", facts.text(aspect.getLocalizedDescription()),
                "color", uint(Integer.toUnsignedLong(aspect.getColor() | 0xff000000)), "components", components,
                "discovered", known == null ? null : known.aspects.containsKey(aspect), "icon", null);
        facts.row("aspects", record);
        facts.picture(new Facts.Picture(record, "icon", "thaumcraft:aspect/" + aspect.getTag(), () -> UtilsFX.drawTag(0, 0, aspect)));
    }

    Cursor research(int index) {
        if (clues == null) clues = new Clues(codechicken.nei.ItemList.items);
        ResearchItem study = studies.get(research.get(index).key);
        return new Cursor(study, index);
    }

    private void research(ResearchItem study, JsonArray items, Facts facts) {
        ItemStack icon = icon(study);
        JsonArray flags = new JsonArray();
        if (study.isAutoUnlock()) flags.add(value("auto"));
        if (study.isConcealed()) flags.add(value("concealed"));
        if (study.isHidden()) flags.add(value("hidden"));
        if (study.isLost()) flags.add(value("lost"));
        if (study.isRound()) flags.add(value("round"));
        if (study.isSecondary()) flags.add(value("secondary"));
        if (study.isSpecial()) flags.add(value("special"));
        if (study.isStub()) flags.add(value("stub"));
        if (study.isVirtual()) flags.add(value("virtual"));
        JsonArray entities = new JsonArray(), aspectTriggers = new JsonArray();
        if (study.getEntityTriggers() != null) for (String entity : study.getEntityTriggers()) entities.add(value(entity));
        if (study.getAspectTriggers() != null) for (Aspect aspect : study.getAspectTriggers()) aspectTriggers.add(value(Aspects.id(aspect)));
        JsonObject record = object("id", researchId(study.key), "source", origin(study.key), "name", facts.text(study.getName()),
                "text", facts.text(study.getText()), "category", study.category, "categoryName", facts.text(ResearchCategories.getCategoryName(study.category)),
                "position", array(study.displayColumn, study.displayRow), "complexity", study.getComplexity(), "warp", ThaumcraftApi.getWarp(study.key),
                "flags", flags, "completed", completed(study.key), "parents", links(study.parents), "hiddenParents", links(study.parentsHidden), "siblings", links(study.siblings),
                "aspects", Aspects.amounts(study.tags), "itemTriggers", items, "entityTriggers", entities, "aspectTriggers", aspectTriggers,
                "icon", null, "texture", null);
        facts.row("research", record);
        if (icon != null) picture(icon, record, facts);
        else if (study.icon_resource != null) facts.picture(new Facts.Picture(record, "texture", study.icon_resource.toString(), () -> {
            Minecraft.getMinecraft().getTextureManager().bindTexture(study.icon_resource);
            UtilsFX.drawTexturedQuadFull(0, 0, 0);
        }));
    }

    /** Research art only becomes an item link when that exact item is already in the catalog. */
    static void picture(ItemStack icon, JsonObject record, Facts facts) {
        String known = facts.known(icon);
        if (known != null) {
            record.addProperty("icon", known);
            return;
        }
        ItemStack shown = icon.copy();
        String location = net.minecraft.item.Item.itemRegistry.getNameForObject(shown.getItem()) + "#" + Items.feather.getDamage(shown);
        facts.picture(new Facts.Picture(record, "texture", location, () -> {
            Minecraft game = Minecraft.getMinecraft();
            org.lwjgl.opengl.GL11.glEnable(org.lwjgl.opengl.GL12.GL_RESCALE_NORMAL);
            org.lwjgl.opengl.GL11.glEnable(org.lwjgl.opengl.GL11.GL_BLEND);
            org.lwjgl.opengl.GL11.glBlendFunc(org.lwjgl.opengl.GL11.GL_SRC_ALPHA, org.lwjgl.opengl.GL11.GL_ONE_MINUS_SRC_ALPHA);
            RenderHelper.enableGUIStandardItemLighting();
            // GuiResearchBrowser renders the stack without querying its item text.
            // This also honors Forge's custom GUI item renderer.
            new RenderItem().renderItemAndEffectIntoGUI(game.fontRenderer, game.getTextureManager(), shown.copy(), 0, 0);
        }));
    }

    /** One observed frame of the native research icon, not an inventory or clue template. */
    static ItemStack icon(ResearchItem study) {
        if (study.icon_item == null) return null;
        ItemStack template = study.icon_item.copy();
        if (template.getItem() == null) throw new Jobs.Fault("research_icon", "Research '" + study.key + "' has an empty item icon");
        // GuiResearchBrowser gives item icons precedence over resource icons and
        // resolves subtype/durability cycles before rendering. Native cycle code
        // may reuse the input's NBT; copies on both sides isolate the registry.
        ItemStack shown = InventoryUtils.cycleItemStack(template);
        if (shown == null || shown.getItem() == null || Items.feather.getDamage(shown) == OreDictionary.WILDCARD_VALUE) {
            throw new Jobs.Fault("research_icon", "Research '" + study.key + "' has no concrete native icon frame: "
                    + net.minecraft.item.Item.itemRegistry.getNameForObject(template.getItem())
                    + "; meta=" + Items.feather.getDamage(template));
        }
        return shown.copy();
    }

    final class Cursor {
        private final ResearchItem study;
        private final int index;
        private Clues.Cursor triggers;
        private boolean finished;

        Cursor(ResearchItem study, int index) { this.study = study; this.index = index; }

        boolean capture(Facts facts) {
            if (finished) throw new IllegalStateException("Research already captured");
            try {
                if (studies.get(study.key) != study) throw new Jobs.Fault("registry_changed", "Research changed during capture");
                if (triggers == null) triggers = clues.open(study.getItemTriggers());
                if (!triggers.capture(facts::item)) return false;
                research(study, triggers.records(), facts);
                finished = true;
                return true;
            } catch (java.util.concurrent.CancellationException error) { throw error; }
            catch (RuntimeException error) {
                Jobs.Fault fault = new Jobs.Fault(error instanceof Jobs.Fault ? ((Jobs.Fault) error).code : "research_capture",
                        "Research '" + study.key + "' in " + study.category + " (index " + index + "): " + error);
                fault.initCause(error);
                throw fault;
            }
        }
    }

    private JsonArray links(String[] keys) {
        JsonArray rows = new JsonArray();
        if (keys == null) return rows;
        for (String key : keys) {
            ResearchItem study = studies.get(key);
            rows.add(object("key", key, "id", study == null ? null : researchId(key), "completed", completed(key)));
        }
        return rows;
    }

    private Boolean completed(String key) {
        List<String> completed = knowledge.researchCompleted.get(player);
        return completed == null ? null : completed.contains(key);
    }

    private static String researchId(String key) { return Identity.origin("research", origin(key)); }
    static JsonObject study(String key) {
        ResearchItem study = Studies.find(key);
        String player = Minecraft.getMinecraft().thePlayer.getCommandSenderName();
        List<String> completed = Thaumcraft.proxy.getPlayerKnowledge().researchCompleted.get(player);
        return object("key", key, "id", study == null ? null : researchId(key), "completed", completed == null ? null : completed.contains(key));
    }
    private static JsonObject origin(String key) { return object("owner", "Thaumcraft", "handler", ResearchItem.class.getName(), "key", key); }
}
