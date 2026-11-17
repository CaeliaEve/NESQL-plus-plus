package com.github.dcysteine.nesql.exporter.capture;

import static com.github.dcysteine.nesql.exporter.source.Json.*;

import com.github.dcysteine.nesql.exporter.source.TypedNbt;
import com.google.gson.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import net.minecraft.item.*;
import net.minecraft.nbt.*;

/** Real vanilla callback outputs, not a cycle of NEI preview ingredients. */
final class FireworkReferencesTest {
  /** The exact protected-field rule in the target META-INF/etfuturum_at.cfg. */
  public static final class NativeAccess implements net.minecraft.launchwrapper.IClassTransformer {
    public byte[] transform(String name,String transformed,byte[] bytes){
      if(!"net.minecraft.item.crafting.RecipeFireworks".equals(transformed))return bytes;
      org.objectweb.asm.tree.ClassNode type=new org.objectweb.asm.tree.ClassNode();new org.objectweb.asm.ClassReader(bytes).accept(type,0);
      for(Object value:type.fields){org.objectweb.asm.tree.FieldNode field=(org.objectweb.asm.tree.FieldNode)value;
        if(field.name.equals("field_92102_a"))field.access=(field.access&~(org.objectweb.asm.Opcodes.ACC_PRIVATE|org.objectweb.asm.Opcodes.ACC_PUBLIC))|org.objectweb.asm.Opcodes.ACC_PROTECTED;}
      org.objectweb.asm.ClassWriter writer=new org.objectweb.asm.ClassWriter(0);type.accept(writer);return writer.toByteArray();
    }
  }
  static void fixed() throws Exception {
    try(NativeRegistryScope scope=new NativeRegistryScope()){
      NativeCoreFixesTest.version("etfuturum","2.6.2.25-GTNH");
      net.minecraft.item.crafting.RecipeFireworks nativeRule=(net.minecraft.item.crafting.RecipeFireworks)
          MagicApi.type("ganymedes01.etfuturum.recipes.crafting.RecipeFixedFireworks").newInstance();
      net.minecraft.item.crafting.CraftingManager manager=net.minecraft.item.crafting.CraftingManager.getInstance();
      scope.replace(manager.getClass(),manager,"recipes",new ArrayList<>(Collections.singletonList(nativeRule)));
      JsonObject source=object("owner","NEI","handler","codechicken.nei.recipe.FireworkRecipeHandler","key","crafting");
      try(FireworkRecipes reader=new FireworkRecipes(scope.allocate(codechicken.nei.recipe.FireworkRecipeHandler.class),source)){
        FireworkRecipes.Snapshot captured=(FireworkRecipes.Snapshot)MagicApi.field(reader,"snapshot");
        for(ItemStack stack:captured.specimens.values()){
          NBTTagCompound tag=stack.getTagCompound();
          require(tag==null||!tag.hasKey("Explosion")||tag.hasKey("Explosion",10),
              "Intentional malformed probe leaked into display facts and native tooltip capture");
        }
        boolean rawFailure=false;
        for(JsonElement row:captured.records){JsonObject data=row.getAsJsonObject().getAsJsonObject("data");
          if(data.has("failure")&&!data.get("failure").isJsonNull()){
            JsonArray states=data.getAsJsonObject("failure").getAsJsonArray("inputs");
            rawFailure=states!=null&&states.size()==1&&states.get(0).getAsJsonObject().getAsJsonObject("template").getAsJsonObject("nbt")!=null;
          }
        }
        require(rawFailure,"Malformed native input NBT disappeared instead of becoming explicit nondisplay state");
        JsonObject definition=captured.records.get(0).getAsJsonObject().getAsJsonObject("data");
        require(definition.get("recipeClass").getAsString().equals(nativeRule.getClass().getName())&&definition.get("outputAmount").getAsString().equals("3"),"Active Et Futurum rule was replaced by vanilla");
        boolean matched=false;
        for(JsonElement row:captured.records){JsonObject data=row.getAsJsonObject().getAsJsonObject("data");
          if(!data.has("label")||!data.get("label").getAsString().equals("rocket-3-0"))continue;
          JsonObject output=data.getAsJsonObject("output");ItemStack item=captured.specimens.get(output.get("item").getAsString());
          require(output.get("amount").getAsString().equals("3")&&item.getTagCompound().getCompoundTag("Fireworks").getByte("Flight")==3
              &&!item.getTagCompound().getCompoundTag("Fireworks").hasKey("Explosions"),"Empty fixed rocket lost flight, amount or native absent Explosions");matched=true;
        }
        require(matched&&nativeRule.getRecipeOutput()==null,"Captured rule invoked or mutated the global recipe instance");
        reader.finishVerification();
        NativeContractFixture.writeReference("fireworks-fixed",object("records",captured.records),captured.specimens.values());
      }
    }
    System.out.println("Native fixed fireworks: actual registered callback, three rockets, uncharged flight and owned recipe state passed");
  }
  static void run() throws Exception {
    JsonObject source =
        object(
            "owner",
            "NEI",
            "handler",
            "codechicken.nei.recipe.FireworkRecipeHandler",
            "key",
            "crafting");
    FireworkRecipes.Snapshot snapshot = FireworkRecipes.snapshot(source);
    // A registered handler with no GUI examples must still expose the real crafting rule.
    try (NativeRegistryScope scope = new NativeRegistryScope()) {
      net.minecraft.item.crafting.CraftingManager manager = net.minecraft.item.crafting.CraftingManager.getInstance();
      ArrayList<net.minecraft.item.crafting.IRecipe> recipes = new ArrayList<>();
      recipes.add(new net.minecraft.item.crafting.RecipeFireworks());
      scope.replace(manager.getClass(), manager, "recipes", recipes);
      codechicken.nei.recipe.FireworkRecipeHandler handler = scope.allocate(codechicken.nei.recipe.FireworkRecipeHandler.class);
      try (FireworkRecipes reader = new FireworkRecipes(handler, source)) {
        require(reader.size() == snapshot.records.size(), "GUI cache still gates semantic fireworks");
        reader.finishVerification();
        recipes.set(0, new net.minecraft.item.crafting.RecipeFireworks());
        try {reader.verify();throw new AssertionError("Firework source replacement was accepted");}
        catch(com.github.dcysteine.nesql.exporter.task.Jobs.Fault expected) {
          require(expected.code.equals("environment_changed"), "Wrong firework source drift failure");
        }
      }
    }
    JsonObject definition = snapshot.records.get(0).getAsJsonObject().getAsJsonObject("data");
    require(definition.getAsJsonArray("dyes").size() == 16, "Native dye palette incomplete");
    require(definition.getAsJsonArray("shapes").size() == 5, "Native firework shapes incomplete");
    require(definition.getAsJsonArray("effects").size() == 2, "Native firework effects incomplete");
    boolean emptyRocket = false,
        bothEffects = false,
        unchangedFade = false,
        duplicateRejected = false;
    for (JsonElement leaf : snapshot.records) {
      JsonObject data = leaf.getAsJsonObject().getAsJsonObject("data");
      if (!data.get("kind").getAsString().equals("fireworkExample")) continue;
      String label = data.get("label").getAsString();
      JsonElement output = data.get("output");
      if (label.equals("rocket-3-0")) {
        ItemStack stack =
            snapshot.specimens.get(output.getAsJsonObject().get("item").getAsString());
        emptyRocket =
            stack.hasTagCompound()
                && !stack.getTagCompound().hasKey("Fireworks")
                && stack.stackSize == 1;
      }
      if (label.equals("star-0-3")) {
        NBTTagCompound explosion =
            snapshot
                .specimens
                .get(output.getAsJsonObject().get("item").getAsString())
                .getTagCompound()
                .getCompoundTag("Explosion");
        bothEffects = explosion.getBoolean("Trail") && explosion.getBoolean("Flicker");
      }
      if (label.equals("fade-no-explosion")) {
        ItemStack stack =
            snapshot.specimens.get(output.getAsJsonObject().get("item").getAsString());
        unchangedFade =
            stack.hasTagCompound()
                && stack.getTagCompound().getString("owner").equals("retain")
                && !stack.getTagCompound().hasKey("Explosion");
      }
      if (label.equals("invalid-two-shapes")) duplicateRejected = output.isJsonNull();
    }
    require(
        emptyRocket && bothEffects && unchangedFade && duplicateRejected,
        "Native firework edge behavior was normalized away");
    JsonArray items = array();
    for (Map.Entry<String, ItemStack> entry : snapshot.specimens.entrySet()) {
      ItemStack stack = entry.getValue();
      items.add(
          object(
              "id",
              entry.getKey(),
              "registry",
              Item.itemRegistry.getNameForObject(stack.getItem()),
              "meta",
              stack.getItemDamage(),
              "nbt",
              TypedNbt.encode(stack.getTagCompound())));
    }
    Files.write(
        System.getProperty("nesql.nativeFixtureDirectory")==null?Paths.get("firework-reference-contract.json"):NativeContractFixture.directory().resolve("firework-reference-contract.json"),
        object("records", snapshot.records, "items", items)
            .toString()
            .getBytes(StandardCharsets.UTF_8));
  }

  private static void require(boolean condition, String message) {
    if (!condition) throw new AssertionError(message);
  }
}
