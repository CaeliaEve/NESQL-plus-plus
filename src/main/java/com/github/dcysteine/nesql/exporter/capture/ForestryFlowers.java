package com.github.dcysteine.nesql.exporter.capture;

import com.github.dcysteine.nesql.exporter.task.Jobs;
import com.google.gson.JsonObject;
import forestry.api.genetics.IAlleleSpecies;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Locale;
import static com.github.dcysteine.nesql.exporter.source.Json.object;

/** Exact optional Botany 2.5.24 API, without loading its classes when the root is absent. */
final class ForestryFlowers {
    private ForestryFlowers() {}

    static Enum<?>[] forms() {
        Class<?> stages = type("binnie.botany.api.EnumFlowerStage");
        Object[] values = stages.getEnumConstants();
        if (values == null || values.length != 4) throw new Jobs.Fault("species_forms", "Unexpected Botany member stages");
        String[] expected = {"FLOWER", "SEED", "POLLEN"};
        Enum<?>[] result = new Enum<?>[3];
        for (int index = 0; index < result.length; index++) {
            result[index] = (Enum<?>) values[index];
            if (!result[index].name().equals(expected[index])) throw new Jobs.Fault("species_forms", "Unexpected Botany member stage");
        }
        return result;
    }

    static JsonObject traits(IAlleleSpecies allele) {
        Class<?> species = type("binnie.botany.api.IAlleleFlowerSpecies");
        if (!species.isInstance(allele)) throw new Jobs.Fault("species_type", "Flower root contains a non-flower species");
        Object acidity = call(species, "getPH", allele), moisture = call(species, "getMoisture", allele);
        Object flowerType = call(species, "getType", allele);
        Object id = call(type("binnie.botany.api.IFlowerType"), "getID", flowerType);
        if (!(acidity instanceof Enum) || !(moisture instanceof Enum) || !(id instanceof Integer)
                || (Integer) id < 0 || (Integer) id > 65535) throw new Jobs.Fault("species_traits", "Invalid Botany flower traits");
        return object("acidity", ((Enum<?>) acidity).name().toLowerCase(Locale.ROOT),
                "moisture", ((Enum<?>) moisture).name().toLowerCase(Locale.ROOT), "type", id);
    }

    private static Class<?> type(String name) {
        try { return Class.forName(name); }
        catch (ClassNotFoundException failure) { throw new Jobs.Fault("domain_unsupported", "Missing Botany API " + name); }
    }

    private static Object call(Class<?> owner, String name, Object target) {
        try {
            Method method = owner.getMethod(name);
            return method.invoke(target);
        } catch (InvocationTargetException failure) {
            Throwable cause = failure.getCause();
            if (cause instanceof RuntimeException) throw (RuntimeException) cause;
            if (cause instanceof Error) throw (Error) cause;
            throw new Jobs.Fault("species_traits", "Botany trait provider failed: " + cause);
        } catch (ReflectiveOperationException failure) {
            throw new Jobs.Fault("domain_unsupported", "Unexpected Botany API " + owner.getName() + "." + name);
        }
    }
}
