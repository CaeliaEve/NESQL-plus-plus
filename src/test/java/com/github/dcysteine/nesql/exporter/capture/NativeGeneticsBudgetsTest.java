package com.github.dcysteine.nesql.exporter.capture;

import java.util.*;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

/** Repeated native witnesses must not consume the unique-fact budget repeatedly. */
final class NativeGeneticsBudgetsTest {
  static void run() throws Exception {
    try (NativeRegistryScope scope = new NativeRegistryScope()) {
      GendustryGeneticsCatalog catalog=scope.allocate(GendustryGeneticsCatalog.class);
      List<GendustryGeneticsCatalog.Entry> entries=new ArrayList<>();
      scope.replace(GendustryGeneticsCatalog.class,catalog,"entries",entries);
      ItemStack member=new ItemStack(Items.paper);NBTTagCompound tag=new NBTTagCompound();
      char[] payload=new char[32768];Arrays.fill(payload,'x');tag.setString("genome",new String(payload));member.setTagCompound(tag);
      GendustryGeneticsCatalog.Entry row=new GendustryGeneticsCatalog.Entry("sample",new ItemStack(Items.dye));
      row.inputs.put("individual",member);
      for(int i=0;i<2048;i++)entries.add(row);
      String signature=catalog.signature();
      if(signature.length()!=64)throw new AssertionError("Repeated catalogue failed to seal");
      entries.remove(entries.size()-1);
      if(signature.equals(catalog.signature()))throw new AssertionError("Catalogue multiplicity was lost");
      entries.add(row);tag.setString("mutation","changed");
      if(signature.equals(catalog.signature()))throw new AssertionError("Shared genotype drift was lost");
      entries.clear();String shared=new String(payload);
      for(int i=0;i<1100;i++){
        ItemStack item=new ItemStack(Items.paper);NBTTagCompound distinct=new NBTTagCompound();distinct.setString("genome",shared);distinct.setInteger("identity",i);item.setTagCompound(distinct);
        GendustryGeneticsCatalog.Entry unique=new GendustryGeneticsCatalog.Entry("sample",null);unique.inputs.put("individual",item);entries.add(unique);
      }
      try{catalog.signature();throw new AssertionError("Unique native facts bypassed the retained 32MiB budget");}
      catch(com.github.dcysteine.nesql.exporter.task.Jobs.Fault limit){if(!limit.code.equals("recipe_limit"))throw limit;}
    }
    System.out.println("Genetic budgets: 2048 repeated 32KiB witnesses seal without charging duplicates; multiplicity/NBT drift retained");
  }
}
