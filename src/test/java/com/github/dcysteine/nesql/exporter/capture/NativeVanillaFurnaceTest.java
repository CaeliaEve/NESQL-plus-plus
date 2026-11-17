package com.github.dcysteine.nesql.exporter.capture;

import codechicken.nei.recipe.FurnaceRecipeHandler;
import com.github.dcysteine.nesql.exporter.source.Identity;
import com.github.dcysteine.nesql.exporter.source.TypedNbt;
import com.github.dcysteine.nesql.exporter.task.Jobs;
import java.lang.reflect.*;
import java.util.*;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.FurnaceRecipes;
import static com.github.dcysteine.nesql.exporter.source.Json.*;

/** One controlled native furnace registry; real matching/experience, no GUI or live world. */
final class NativeVanillaFurnaceTest {
    static void run() throws Exception {
        Constructor<FurnaceRecipes> constructor = FurnaceRecipes.class.getDeclaredConstructor();
        constructor.setAccessible(true);
        FurnaceRecipes manager = constructor.newInstance();
        Map<ItemStack, ItemStack> nativeMap = new LinkedHashMap<>();
        Field sourceField = FurnaceRecipes.class.getDeclaredField("smeltingList");
        sourceField.setAccessible(true); sourceField.set(manager, nativeMap);
        @SuppressWarnings("unchecked") Map<ItemStack, Float> experience = (Map<ItemStack, Float>) MagicApi.field(manager, "experienceList");
        experience.clear();
        ItemStack input = new ItemStack(Items.paper, 17, 2), output = new ItemStack(Items.gold_ingot, 3);
        input.setTagInfo("ignored", new net.minecraft.nbt.NBTTagInt(7));
        output.setTagInfo("preserved", new net.minecraft.nbt.NBTTagInt(8));
        nativeMap.put(input, output); experience.put(output, 0.7f);
        ItemStack wildcard = new ItemStack(Items.apple, 19, 32767), exact = new ItemStack(Items.apple, 21, 0);
        nativeMap.put(wildcard, new ItemStack(Items.diamond));
        nativeMap.put(exact, new ItemStack(Items.iron_ingot));
        require(manager.getSmeltingResult(new ItemStack(Items.apple)).getItem() == Items.diamond, "Native furnace wildcard precedence changed");
        require(manager.getSmeltingResult(new ItemStack(Items.paper, 1, 2)).getItem() == Items.gold_ingot, "Native furnace uses display amounts/NBT");

        Class<?> adapter = Class.forName("com.github.dcysteine.nesql.exporter.capture.VanillaFurnaceRecipes");
        Constructor<?> factory = adapter.getDeclaredConstructor(FurnaceRecipes.class); factory.setAccessible(true);
        RegistryRecipes reader = (RegistryRecipes) factory.newInstance(manager);
        RecipeRow row = row(input, output);
        require(reader.size() == 3 && reader.capture(0, row), "Native furnace definitions were dropped");
        require(row.inputs.get(0).getAsJsonObject().getAsJsonArray("choices").get(0).getAsJsonObject().get("amount").getAsString().equals("1"), "Furnace consumes a display stack");
        require(row.outputs.get(0).getAsJsonObject().get("amount").getAsString().equals("3") && row.record.get("duration").getAsString().equals("200"), "Native furnace output/time changed");
        require(row.properties.has("minecraft:experience"), "Native furnace experience disappeared with GUI removal");
        NativeContractFixture.writeOwned("vanilla-furnace", row, Arrays.asList(input, output));
        RecipeRow hidden = row(exact, new ItemStack(Items.iron_ingot), wildcard);
        require(!reader.capture(2, hidden) && hidden.exclusion != null, "Earlier wildcard shadow was exported as a usable result");
        reader.finishVerification();
        output.stackSize = 4;
        expectChanged(() -> reader.capture(0, row(input, output)));
        output.stackSize = 3;
        experience.put(output, 0.8f);
        expectChanged(reader::finishVerification);
        experience.put(output, 0.7f);
        nativeMap.remove(wildcard); nativeMap.put(wildcard, new ItemStack(Items.diamond));
        expectChanged(reader::finishVerification);
        reader.close(); reader.close();
        try { reader.size(); throw new AssertionError("Closed furnace reader exposed rows"); }
        catch (Jobs.Fault failure) { require(failure.code.equals("capture_closed"), failure.toString()); }

        nativeMap.clear();
        nativeMap.put(exact, new ItemStack(Items.iron_ingot));
        nativeMap.put(wildcard, new ItemStack(Items.diamond));
        RegistryRecipes exceptionsReader = (RegistryRecipes)factory.newInstance(manager);
        RecipeRow exceptionsRow = row(wildcard, exact, new ItemStack(Items.diamond));
        require(exceptionsReader.capture(1, exceptionsRow), "Remaining native wildcard domain disappeared");
        require(exceptionsRow.inputs.get(0).getAsJsonObject().getAsJsonArray("choices").get(0).getAsJsonObject().getAsJsonObject("rule").get("kind").getAsString().equals("except"), "Wildcard recipe stole an earlier exact definition");
        require(manager.getSmeltingResult(new ItemStack(Items.apple)).getItem() == Items.iron_ingot
                && manager.getSmeltingResult(new ItemStack(Items.apple, 1, 1)).getItem() == Items.diamond, "Actual native partial wildcard precedence changed");
        NativeContractFixture.writeOwned("vanilla-furnace-except", exceptionsRow, Arrays.asList(wildcard, exact, new ItemStack(Items.diamond)));
        exceptionsReader.finishVerification(); exceptionsReader.close();
        nativeMap.clear();
        Map<ItemStack,ItemStack> optimized=(Map<ItemStack,ItemStack>)MagicApi.type(NativeSmeltingTable.LIB).getConstructor(boolean.class).newInstance(false);
        optimized.put(exact,new ItemStack(Items.iron_ingot));optimized.put(wildcard,new ItemStack(Items.diamond));
        sourceField.set(manager,optimized);
        require(optimized.get(exact).getItem()==Items.diamond,"Native optimized map must prefer the wildcard");
        try(RegistryRecipes optimizedReader=new VanillaFurnaceRecipes(manager)){
            require(optimizedReader.size()==2,"Optimized native map lost raw exact definitions");
            boolean excluded=false,produced=false;
            for(int i=0;i<2;i++){
                RecipeRow candidate=row(wildcard,exact,new ItemStack(Items.diamond),new ItemStack(Items.iron_ingot));
                if(optimizedReader.capture(i,candidate)){
                    produced=true;
                    require(candidate.inputs.get(0).getAsJsonObject().getAsJsonArray("choices").get(0).getAsJsonObject().getAsJsonObject("rule").get("meta").getAsBoolean(),"Optimized wildcard lost selection priority");
                }else excluded=true;
            }
            require(excluded&&produced,"Optimized native wildcard did not shadow the exact definition");
            optimizedReader.finishVerification();
            optimized.put(exact,new ItemStack(Items.gold_ingot));expectChanged(optimizedReader::finishVerification);
        }
        sourceField.set(manager,nativeMap);
        RegistryRecipes emptyReader = (RegistryRecipes) factory.newInstance(manager);
        require(emptyReader.size() == 0, "Ready empty furnace registry became a failure");
        emptyReader.finishVerification(); emptyReader.close();
        net.minecraft.item.Item customMetadata = new net.minecraft.item.Item() {
            @Override public int getDamage(ItemStack stack) { return 0; }
        };
        net.minecraft.item.Item.itemRegistry.addObject(30080, "nesql:custom_furnace_metadata", customMetadata);
        nativeMap.put(new ItemStack(customMetadata, 1, 2), new ItemStack(Items.paper));
        expectUnsupported(factory, manager);
        nativeMap.clear();
        net.minecraft.item.Item customExperience = new net.minecraft.item.Item() {
            @Override public float getSmeltingExperience(ItemStack stack) { return 0.2f; }
        };
        net.minecraft.item.Item.itemRegistry.addObject(30081, "nesql:custom_furnace_experience", customExperience);
        nativeMap.put(new ItemStack(Items.paper), new ItemStack(customExperience));
        expectUnsupported(factory, manager);
        nativeMap.clear();
        sourceField.set(manager, null);
        try { factory.newInstance(manager); throw new AssertionError("Missing furnace registry became a ready empty set"); }
        catch (InvocationTargetException failure) { require(failure.getCause() instanceof Jobs.Fault && ((Jobs.Fault)failure.getCause()).code.equals("handler_not_ready"), failure.toString()); }
        System.out.println("Native furnace: consumption, output/time/experience, wildcard precedence, drift, ready empty and close passed");
    }
    private static void expectUnsupported(Constructor<?> factory, FurnaceRecipes manager) throws Exception {
        try { factory.newInstance(manager); throw new AssertionError("Unknown furnace callback was promoted to a static rule"); }
        catch (InvocationTargetException failure) { require(failure.getCause() instanceof Jobs.Fault && ((Jobs.Fault)failure.getCause()).code.equals("recipe_unsupported"), failure.toString()); }
    }
    private static void expectChanged(Runnable operation) {
        try { operation.run(); throw new AssertionError("Native furnace definition/order drift was ignored"); }
        catch (Jobs.Fault failure) { require(failure.code.equals("recipe_changed"), failure.toString()); }
    }
    @SuppressWarnings("unchecked") private static RecipeRow row(ItemStack... stacks) {
        Facts facts = new Facts("en_US");
        Set<String> known = (Set<String>)MagicApi.field(facts, "items");
        for (ItemStack stack : stacks) known.add(Identity.item(net.minecraft.item.Item.itemRegistry.getNameForObject(stack.getItem()), stack.getItemDamage(), TypedNbt.encode(stack.getTagCompound())));
        String handler = FurnaceRecipeHandler.class.getName();
        com.google.gson.JsonObject origin = object("owner", "NEI", "handler", handler, "key", handler);
        return new RecipeRow(facts, origin, Identity.origin("category", origin), 0);
    }
    private static void require(boolean value, String message) { if (!value) throw new AssertionError(message); }
}
