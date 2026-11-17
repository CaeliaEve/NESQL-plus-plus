package com.github.dcysteine.nesql.exporter.capture;

import static com.github.dcysteine.nesql.exporter.source.Json.object;

import com.github.dcysteine.nesql.exporter.task.Jobs;
import java.util.*;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

/** Owned content examples for native predicates; never constructs a recipe display stack. */
final class NativeItemExamples implements AutoCloseable {
  private final Map<Item,NativeItemVariants> variants=new IdentityHashMap<>();
  private boolean closed;
  private int candidates;
  private long bytes;

  ItemStack[] copies(ItemStack template){return copies(template,true);}
  /** NEI content permutations preserve native variant tags, without constructing a display wrapper. */
  ItemStack[] permutations(ItemStack template){return copies(template,false);}
  private ItemStack[] copies(ItemStack template,boolean templateTags){
    alive();NativeStackAudit.auditRaw(template);
    if(net.minecraft.init.Items.feather.getDamage(template)!=32767)return new ItemStack[]{template.copy()};
    NativeItemVariants source=variants.get(template.getItem());
    if(source==null){if(variants.size()>=65536)throw new Jobs.Fault("recipe_limit","Native example item budget exceeded");source=new NativeItemVariants(template);
      ItemStack[] initial=source.copies();candidates+=initial.length;if(candidates>262144)throw new Jobs.Fault("recipe_limit","Native example cumulative candidate budget exceeded");
      for(ItemStack stack:initial){bytes+=com.github.dcysteine.nesql.exporter.source.CanonicalJson.bytes(RecipeRow.stackEvidence(stack)).length;if(bytes>32L*1024*1024)throw new Jobs.Fault("recipe_limit","Native example cumulative byte budget exceeded");}
      variants.put(template.getItem(),source);}
    ItemStack[] copies=source.copies();
    for(ItemStack stack:copies){stack.stackSize=template.stackSize;if(templateTags||!source.hasObservedCandidates())stack.setTagCompound(template.getTagCompound()==null?null:(NBTTagCompound)template.getTagCompound().copy());}
    return copies;
  }

  List<RecipeRow.Ingredient> choices(ItemStack template,long amount,boolean keep){
    List<RecipeRow.Ingredient> values=new ArrayList<>();
    for(ItemStack copy:copies(template))values.add(new RecipeRow.Ingredient(copy,amount,keep,object("kind","wildcard","meta",net.minecraft.init.Items.feather.getDamage(template)==32767,"nbt",template.getTagCompound()==null||template.getTagCompound().hasNoTags())));
    return values;
  }

  void verify(){alive();for(NativeItemVariants source:variants.values())source.verify();}
  private void alive(){Jobs.checkpoint();if(closed)throw new Jobs.Fault("capture_closed","Native item examples are closed");}
  public void close(){closed=true;variants.clear();}
}
