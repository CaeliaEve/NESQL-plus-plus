package com.github.dcysteine.nesql.exporter.capture;

import com.github.dcysteine.nesql.exporter.source.Identity;
import com.github.dcysteine.nesql.exporter.task.Jobs;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import gregtech.api.enums.GTValues;
import gregtech.api.enums.Materials;
import gregtech.api.enums.OrePrefixes;
import gregtech.api.objects.MaterialStack;
import gregtech.api.util.GTOreDictUnificator;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import static com.github.dcysteine.nesql.exporter.source.Json.*;

/** Registry material facts. No ore-name parsing, hardcoded part matrix or recipe inference. */
final class GtMaterials {
    private static final OrePrefixes[] PREFIXES = java.util.Arrays.stream(OrePrefixes.values())
            .filter(prefix -> prefix.mIsMaterialBased).toArray(OrePrefixes[]::new);
    private GtMaterials() {}

    static List<Materials> all() {
        Map<String, Materials> materials = new TreeMap<>();
        List<Materials> pending = new ArrayList<>(Materials.getAll());
        for (int index = 0; index < pending.size(); index++) {
            Jobs.checkpoint();
            if (pending.size() > 262144) throw new Jobs.Fault("material_limit", "Material reference graph exceeds its budget");
            Materials material = pending.get(index);
            if (material == null || material.mName == null || material.mName.isEmpty()) throw new Jobs.Fault("material_identity", "Material has no registry identity");
            Materials previous = materials.putIfAbsent(material.mName, material);
            if (previous != null) {
                if (previous != material) throw new Jobs.Fault("material_conflict", "Different materials share the key " + material.mName);
                continue;
            }
            Colors.material(material.mName, material.mRGBa);
            if (material.mChemicalFormula == null) throw new Jobs.Fault("material_formula", "Material '" + material.mName + "' has no formula field");
            if (material.mMaterialList == null) throw new Jobs.Fault("material_amount", "Material '" + material.mName + "' has no component list");
            for (MaterialStack component : material.mMaterialList) {
                if (component == null || component.mMaterial == null || component.mAmount <= 0) {
                    throw new Jobs.Fault("material_amount", "Invalid component in material '" + material.mName + "'");
                }
                pending.add(component.mMaterial);
            }
        }
        return new ArrayList<>(materials.values());
    }

    static JsonObject inspect() {
        try {
            List<Materials> materials = all();
            JsonArray rows = new JsonArray();
            int adjusted = 0;
            for (Materials material : materials) {
                if (!Colors.adjusted(material.mRGBa)) continue;
                adjusted++;
                if (rows.size() >= 32) continue;
                JsonArray rgba = new JsonArray();
                for (short channel : material.mRGBa) rgba.add(value(channel));
                rows.add(object("key", material.mName, "rgba", rgba, "color", uint(Colors.material(material.mName, material.mRGBa))));
            }
            return object("valid", true, "count", materials.size(), "adjusted", adjusted, "rows", rows, "omitted", adjusted - rows.size());
        } catch (Jobs.Fault error) {
            return object("valid", false, "error", object("code", error.code, "message", error.getMessage()));
        }
    }

    private static JsonObject origin(Materials material) {
        return object("owner", "gregtech", "handler", Materials.class.getName(), "key", material.mName);
    }

    static final class Cursor {
        private final Materials material;
        private final JsonObject record;
        private final JsonArray parts = new JsonArray();
        private int next;
        private boolean finished;

        Cursor(Materials material, Facts facts) {
            this.material = material;
            JsonObject source = origin(material);
            JsonArray components = new JsonArray();
            Map<String, Long> amounts = new TreeMap<>();
            for (MaterialStack component : material.mMaterialList) {
                if (component.mAmount <= 0) throw new Jobs.Fault("material_amount", "Invalid component amount in " + material.mName);
                String id = Identity.origin("material", origin(component.mMaterial));
                amounts.merge(id, component.mAmount, Math::addExact);
            }
            for (Map.Entry<String, Long> component : amounts.entrySet()) {
                components.add(object("material", component.getKey(), "amount", Long.toString(component.getValue())));
            }
            long color = Colors.material(material.mName, material.mRGBa);
            if (material.mChemicalFormula == null) throw new Jobs.Fault("material_formula", "Material '" + material.mName + "' has no formula field");
            record = object("id", Identity.origin("material", source), "source", source, "name", facts.text(material.mLocalizedName),
                    "formula", material.mChemicalFormula, "color", uint(color), "components", components, "parts", parts);
        }

        /** Stop after sixteen existing forms or two milliseconds of registry scanning. */
        boolean capture(Facts facts) {
            if (finished) throw new IllegalStateException("Material already captured");
            long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.MILLISECONDS.toNanos(2);
            int captured = 0, begin = next;
            for (; next < PREFIXES.length && captured < 16; next++) {
                if (next > begin && System.nanoTime() >= deadline) break;
                Jobs.checkpoint();
                OrePrefixes prefix = PREFIXES[next];
                ItemStack stack = GTOreDictUnificator.get(prefix, material, 1L);
                if (stack == null) continue; // The registry explicitly has no item for this form.
                captured++;
                JsonObject content = null;
                if (prefix.mMaterialAmount > 0) {
                    long divisor = gcd(prefix.mMaterialAmount, GTValues.M);
                    content = object("numerator", Long.toString(prefix.mMaterialAmount / divisor), "denominator", Long.toString(GTValues.M / divisor));
                }
                parts.add(object("key", prefix.name(), "target", object("kind", "item", "id", facts.item(stack)), "content", content));
            }
            if (next < PREFIXES.length) return false;
            fluid(facts, "fluid", material.getFluid(1));
            fluid(facts, "gas", material.getGas(1));
            fluid(facts, "solid", material.getSolid(1));
            fluid(facts, "molten", material.getMolten(1));
            fluid(facts, "plasma", material.getPlasma(1));
            fluid(facts, "lightlyHydroCracked", material.getLightlyHydroCracked(1));
            fluid(facts, "moderatelyHydroCracked", material.getModeratelyHydroCracked(1));
            fluid(facts, "severelyHydroCracked", material.getSeverelyHydroCracked(1));
            fluid(facts, "lightlySteamCracked", material.getLightlySteamCracked(1));
            fluid(facts, "moderatelySteamCracked", material.getModeratelySteamCracked(1));
            fluid(facts, "severelySteamCracked", material.getSeverelySteamCracked(1));
            facts.row("materials", record);
            finished = true;
            return true;
        }

        private void fluid(Facts facts, String key, FluidStack stack) {
            if (stack != null) parts.add(object("key", key, "target", object("kind", "fluid", "id", facts.fluid(stack)), "content", null));
        }
    }

    private static long gcd(long left, long right) {
        while (right != 0) { long next = left % right; left = right; right = next; }
        return left;
    }
}
