package com.github.dcysteine.nesql.exporter.capture;

import java.util.*;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraftforge.oredict.OreDictionary;

/** Global ore names do not all belong to one infusion predicate's retained budget. */
final class NativeInfusionStorageTest {
    static void run() throws Exception {
        try(NativeRegistryScope scope=new NativeRegistryScope()){
            Class<?> network=MagicApi.type("net.glease.tc4tweak.network.NetworkedConfiguration");
            Class<?> mode=MagicApi.type("net.glease.tc4tweak.modules.infusionRecipe.InfusionOreDictMode");
            scope.replace(network,null,"infusionOreDictMode",Enum.valueOf((Class)mode,"Default"));
            Map<String,Integer> names=new HashMap<>();List<Object> stacks=new ArrayList<>();stacks.add(new ArrayList<>());
            for(int i=0;i<65537;i++)names.put("fixtureUnusedOre"+i,0);
            names.put("fixtureRelevantOre",1);stacks.add(new ArrayList<>(Arrays.asList(new ItemStack(Items.apple,1,32767))));
            scope.replace(OreDictionary.class,null,"nameToId",names);scope.replace(OreDictionary.class,null,"idToStack",stacks);
            try(InfusionLegacyInputs.Context context=new InfusionLegacyInputs.Context()){
                InfusionLegacyInputs.Context.Binding binding=context.bind(new ItemStack(Items.apple,1,2));
                if(!binding.groups.equals(Arrays.asList("fixtureRelevantOre")))throw new AssertionError("Infusion lost native target-side wildcard membership");
                context.finishVerification();
                ((ArrayList<?>)stacks.get(1)).clear();
                try{context.finishVerification();throw new AssertionError("Relevant ore membership drift was ignored");}
                catch(com.github.dcysteine.nesql.exporter.task.Jobs.Fault expected){if(!expected.code.equals("environment_changed"))throw expected;}
            }
        }
        System.out.println("Infusion bindings: large unrelated global name table, relevant wildcard groups and drift passed");
    }
}
