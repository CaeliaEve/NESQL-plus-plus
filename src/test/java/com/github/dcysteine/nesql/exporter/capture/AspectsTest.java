package com.github.dcysteine.nesql.exporter.capture;

import com.github.dcysteine.nesql.exporter.source.Identity;
import com.github.dcysteine.nesql.exporter.task.Jobs;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import thaumcraft.api.aspects.Aspect;
import thaumcraft.api.aspects.AspectList;

import static com.github.dcysteine.nesql.exporter.source.Json.object;

/** Exercises TC4's actual empty-list and copy behavior without a running game. */
public final class AspectsTest {
    private AspectsTest() {}

    public static void run() {
        AspectList empty = new AspectList();
        require(empty.size() == 0 && empty.getAspects().length == 1 && empty.getAspects()[0] == null,
                "The target TC4 empty-array behavior changed");
        AspectList copied = empty.copy();
        require(copied.size() == 1 && copied.aspects.containsKey(null) && copied.getAmount(null) == 0,
                "The target TC4 copy no longer materializes its null sentinel");
        for (AspectList list : new AspectList[] {null, empty, copied, new AspectList().add(empty), new AspectList().merge(empty)}) {
            int size = list == null ? 0 : list.size();
            require(Aspects.amounts(list).size() == 0, "An empty native list acquired exported quantities");
            require(Aspects.knowledge(list).entrySet().isEmpty(), "Empty player knowledge acquired a fake aspect");
            require(list == null || list.size() == size, "Capture changed the native list");
        }

        AspectList mixed = empty.copy().add(Aspect.AIR, 3).add(Aspect.FIRE, 0);
        JsonArray amounts = Aspects.amounts(mixed);
        require(amounts.size() == 2 && amount(amounts, Aspect.AIR).equals("3") && amount(amounts, Aspect.FIRE).equals("0"),
                "The sentinel hid a real cost or a registered zero quantity");
        require(mixed.size() == 3 && mixed.aspects.containsKey(null), "Capture mutated the shared native list");
        require(amounts.equals(Aspects.amounts(new AspectList().add(Aspect.FIRE, 0).add(Aspect.AIR, 3))),
                "Native map order or the null sentinel changed source records");
        JsonObject known = Aspects.knowledge(mixed);
        require(known.entrySet().size() == 2 && known.get(Aspect.FIRE.getTag()).getAsInt() == 0,
                "A discovered aspect with zero points was lost");
        require(Aspects.knowledge(new AspectList().add(Aspect.AIR, -1)).get(Aspect.AIR.getTag()).getAsInt() == -1,
                "Environment fingerprint changed observed player points");
        require(Aspects.id(Aspect.AIR).equals(Identity.origin("aspect", object("owner", "Thaumcraft",
                        "handler", "thaumcraft.api.aspects.Aspect", "key", Aspect.AIR.getTag()))),
                "Registered aspect identity changed");

        for (int value : new int[] {1, -1}) {
            AspectList broken = new AspectList().add(null, value);
            rejected("aspect_reference", () -> Aspects.amounts(broken), Integer.toString(value));
            rejected("aspect_reference", () -> Aspects.knowledge(broken), Integer.toString(value));
            require(broken.getAmount(null) == value, "Invalid native data was removed during validation");
        }
        rejected("aspect_amount", () -> Aspects.amounts(new AspectList().add(Aspect.AIR, -2)), Aspect.AIR.getTag(), "-2");
        rejected("aspect_reference", () -> Aspects.id(null), "Null");

        String tag = "nesql_aspect_fixture";
        Aspect previous = Aspect.aspects.get(tag);
        try {
            Aspect orphan = new Aspect(tag, 0x112233, new Aspect[] {Aspect.AIR, Aspect.FIRE});
            Aspect.aspects.remove(tag);
            rejected("aspect_reference", () -> Aspects.amounts(new AspectList().add(orphan, 1)), tag, "not registered");
            // A same-tag replacement is a different native object, not an empty sentinel.
            new Aspect(tag, 0x445566, new Aspect[] {Aspect.AIR, Aspect.FIRE});
            rejected("aspect_reference", () -> Aspects.amounts(new AspectList().add(orphan, 1)), tag, "differs");
        } finally {
            if (previous == null) Aspect.aspects.remove(tag);
            else Aspect.aspects.put(tag, previous);
        }
        System.out.println("Aspect lists: native empty/copy/add/merge, zero costs, player knowledge and invalid references passed");
    }

    private static String amount(JsonArray rows, Aspect aspect) {
        for (JsonElement row : rows) {
            JsonObject value = row.getAsJsonObject();
            if (value.get("aspect").getAsString().equals(Aspects.id(aspect))) return value.get("amount").getAsString();
        }
        throw new AssertionError("Missing aspect: " + aspect.getTag());
    }

    private static void rejected(String code, Runnable action, String... details) {
        try { action.run(); throw new AssertionError("Expected " + code); }
        catch (Jobs.Fault expected) {
            require(expected.code.equals(code), "Wrong aspect failure: " + expected.code);
            for (String detail : details) require(expected.getMessage().contains(detail), "Missing diagnostic detail: " + detail);
        }
    }

    private static void require(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
