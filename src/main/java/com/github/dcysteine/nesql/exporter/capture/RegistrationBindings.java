package com.github.dcysteine.nesql.exporter.capture;

import static com.github.dcysteine.nesql.exporter.capture.MagicApi.*;

import codechicken.nei.recipe.IRecipeHandler;
import codechicken.nei.recipe.ICraftingHandler;
import codechicken.nei.recipe.FurnaceRecipeHandler;
import com.github.dcysteine.nesql.exporter.task.Jobs;
import gregtech.nei.GTNEIDefaultHandler;
import java.util.*;

/** Same-process semantic source anchors. Never a cross-restart proof or a GUI/cache comparison. */
final class RegistrationBindings {
    private final Object[] anchors;

    private RegistrationBindings(Object... anchors) {
        for (Object anchor : anchors)
            if (anchor == null)
                throw new Jobs.Fault(
                        "handler_not_ready",
                        "Native registration has an uninitialized semantic source");
        this.anchors = anchors;
    }

    static RegistrationBindings read(IRecipeHandler handler) {
        if (NativeUsageReferences.supports(handler))
            return new RegistrationBindings(NativeUsageReferences.registrationAnchors(handler));
        if (handler instanceof GTNEIDefaultHandler)
            return new RegistrationBindings(field(handler, "recipeCategory"));
        if (handler.getClass() == codechicken.nei.recipe.ShapedRecipeHandler.class
                || handler.getClass() == codechicken.nei.recipe.ShapelessRecipeHandler.class) {
            net.minecraft.item.crafting.CraftingManager manager = net.minecraft.item.crafting.CraftingManager.getInstance();
            return new RegistrationBindings(handler.getClass(), manager, manager.getRecipeList());
        }
        if (handler.getClass() == FurnaceRecipeHandler.class) {
            net.minecraft.item.crafting.FurnaceRecipes manager = net.minecraft.item.crafting.FurnaceRecipes.smelting();
            return new RegistrationBindings(handler.getClass(), manager, manager.getSmeltingList());
        }
        String name = handler.getClass().getName();
        if (CropsNHReferences.supportsClass(name))
            return new RegistrationBindings(CropsNHReferences.registrationAnchors());
        if (name.equals(AspectCombinationReferences.HANDLER))
            return new RegistrationBindings(AspectRecipeIndexContext.registrationAnchors(false));
        if (name.equals(ItemAspectReferences.HANDLER))
            return new RegistrationBindings(AspectRecipeIndexContext.registrationAnchors(true));
        if(name.equals(NativeInformationReferences.HANDLER))
            return new RegistrationBindings(handler.getClass(),codechicken.nei.ItemList.items,
                    Boolean.valueOf(codechicken.nei.ItemList.loadFinished),field(handler.getClass(),null,"ITEM_INFO"));
        if(name.equals(MobDropsReferences.HANDLER))
            return new RegistrationBindings(handler.getClass(),field(handler.getClass(),null,"cachedRecipes"),
                    field(type("com.kuba6000.mobsinfo.api.MobRecipe"),null,"MobNameToRecipeMap"),
                    field(type("com.kuba6000.mobsinfo.config.Config$MobHandler"),null,"hiddenMode"));
        if(name.equals(VillagerTradeReferences.HANDLER))
            return new RegistrationBindings(handler.getClass(),field(handler.getClass(),null,"cachedRecipes"));
        if(SmeltingRecipes.supportsClass(name))
            return new RegistrationBindings(SmeltingRecipes.registrationAnchors(name));
        if(HarvestCraftRecipes.supportsClass(name))
            return new RegistrationBindings(HarvestCraftRecipes.registrationAnchors(name));
        if(WitcheryOvenRecipes.supportsClass(name))
            return new RegistrationBindings(WitcheryOvenRecipes.registrationAnchors());
        if(WitcheryCauldronRecipes.supportsClass(name))
            return new RegistrationBindings(WitcheryCauldronRecipes.registrationAnchors());
        if(ForestryRecipeBindings.supportsClass(name))
            return new RegistrationBindings(ForestryRecipeBindings.registrationAnchors(handler.getClass()));
        if(ProjectRedRecipes.supportsClass(name))
            return new RegistrationBindings(ProjectRedRecipes.registrationAnchors(handler.getClass()));
        if(BotaniaWorldRecipes.supportsClass(name))
            return new RegistrationBindings(BotaniaWorldRecipes.registrationAnchors(handler.getClass()));
        if(SagRecipes.supportsClass(name))
            return new RegistrationBindings(SagRecipes.registrationAnchors());
        if(SoulRecipes.supportsClass(name))
            return new RegistrationBindings(SoulRecipes.registrationAnchors());
        if(TinkerRecipes.supportsClass(name))
            return new RegistrationBindings(TinkerRecipes.registrationAnchors(name));
        if(MagicRecipes.supportsClass(name))
            return new RegistrationBindings(MagicRecipes.registrationAnchors());
        if(WitcherySpinningRecipes.supportsClass(name))
            return new RegistrationBindings(WitcherySpinningRecipes.registrationAnchors());
        if(SpinningRecipes.supportsClass(name))
            return new RegistrationBindings(SpinningRecipes.registrationAnchors());
        if(MatterManipulatorRecipes.supportsClass(name))
            return new RegistrationBindings(MatterManipulatorSource.registrationAnchors());
        if(Ic2CropBreedingRecipes.supportsClass(name))
            return new RegistrationBindings(Ic2CropSource.registrationAnchors());
        if(FireworkRecipes.supportsClass(name))
            return new RegistrationBindings(FireworkRecipes.registrationAnchors());
        if(handler instanceof ICraftingHandler&&DisabledRecipes.supports((ICraftingHandler)handler))
            return new RegistrationBindings(DisabledRecipes.registrationAnchors((ICraftingHandler)handler));
        if(WitcheryCookingRecipes.supportsClass(name))
            return new RegistrationBindings(WitcheryCookingRecipes.registrationAnchors(name));
        if(ScrapboxRecipes.supportsClass(name))
            return new RegistrationBindings(ScrapboxRecipes.registrationAnchors());
        if(ImbuingRecipes.supportsClass(name))
            return new RegistrationBindings(ImbuingRecipes.registrationAnchors());
        if(BloodOrbCraftingRecipes.supportsClass(name))
            return new RegistrationBindings(BloodOrbCraftingRecipes.registrationAnchors());
        if(BloodAlchemyRecipes.supportsClass(name))
            return new RegistrationBindings(BloodAlchemyRecipes.registrationAnchors());
        if(LogisticsPipesRecipes.supportsClass(name))
            return new RegistrationBindings(LogisticsPipesRecipes.registrationAnchors());
        if(BloodCalcinatorRecipes.supportsClass(name))
            return new RegistrationBindings(BloodCalcinatorRecipes.registrationAnchors());
        if(BrewingRecipes.supportsClass(name))
            return new RegistrationBindings(BrewingRecipes.registrationAnchors());
        if(GendustryFluidRecipes.supportsClass(name))
            return new RegistrationBindings(GendustryFluidRecipes.registrationAnchors(handler.getClass()));
        if (name.equals(GendustryTemplateRecipes.HANDLER))
            return new RegistrationBindings(GendustryTemplateRecipes.registrationAnchors());
        if (GendustryGeneticsRecipes.supportsClass(name))
            return new RegistrationBindings(GendustryGeneticsRecipes.registrationAnchors(handler.getClass()));
        if(handler instanceof ICraftingHandler && AeRecipes.supports((ICraftingHandler)handler))
            return new RegistrationBindings(AeRecipes.registrationAnchors(handler));
        if(handler instanceof ICraftingHandler && InscriberRecipes.supports((ICraftingHandler)handler)) {
            List<Object> roots=new ArrayList<>(Arrays.asList(InscriberSource.registrationAnchors()));
            roots.add(0,handler.getClass());
            return new RegistrationBindings(roots.toArray());
        }
        if(BotaniaRecipeBindings.supportsClass(name))
            return new RegistrationBindings(new BotaniaRecipeBindings(handler).anchors());
        if (BloodRitualRecipes.supportsClass(name))
            return new RegistrationBindings(new BloodRitualBindings(handler).anchors());
        if (handler instanceof ICraftingHandler && (BinnieGeneticsRecipes.supports((ICraftingHandler)handler)
                || BinnieGeneticsRecipes.supportsReference((ICraftingHandler)handler)))
            return new RegistrationBindings(BinnieGeneticsRecipes.registrationAnchors(handler));
        if(name.equals("crazypants.enderio.nei.EnchanterRecipeHandler"))
            return new RegistrationBindings(EnchanterSource.registrationAnchors(handler));
        if(name.equals("binnie.extratrees.nei.NEIHandlerLumbermill"))
            return new RegistrationBindings(LumbermillSource.registrationAnchors(handler));
        if(name.equals("com.creativemd.creativecore.api.nei.NEIRecipeInfoHandler"))
            return new RegistrationBindings(CreativeCoreBindings.registrationAnchors(handler));
        if (name.equals("alkalus.main.nei.NEI_Handler_Distillery"))
            return new RegistrationBindings(DistillerySource.registrationAnchors(handler));
        if (ChiselRecipes.supportsClass(name))
            return new RegistrationBindings(ChiselSource.registrationAnchors(handler));
        if(handler instanceof ICraftingHandler&&RefineryRecipes.supports((ICraftingHandler)handler))
            return new RegistrationBindings(RefinerySource.registrationAnchors(handler));
        if (handler instanceof ICraftingHandler && FmpMicroblockRecipes.supports((ICraftingHandler) handler))
            return new RegistrationBindings(FmpNativeSource.registrationRoots());
        if (handler instanceof ICraftingHandler && XuMicroblockRecipes.supports((ICraftingHandler) handler))
            return new RegistrationBindings(XuMicroblockSource.registrationAnchors());
        if (handler instanceof ICraftingHandler && ExtraUtilsSoulRecipes.supports((ICraftingHandler) handler))
            return new RegistrationBindings(ExtraUtilsSoulRecipes.registrationAnchors());
        if (handler instanceof ICraftingHandler && DecayableRecipes.supports((ICraftingHandler) handler))
            return new RegistrationBindings(handler.getClass(),DecayableRecipes.registrationSource());
        if (handler instanceof ICraftingHandler && SolarRecipes.supports((ICraftingHandler) handler))
            return new RegistrationBindings(handler.getClass(),SolarRecipes.registrationSource());
        if (handler instanceof codechicken.nei.recipe.TemplateRecipeHandler && CircuitRecipes.supports((ICraftingHandler)handler))
            return new RegistrationBindings(CircuitSource.registrationAnchors((codechicken.nei.recipe.TemplateRecipeHandler)handler));
        if (ExtremeRecipes.supports(handler)) {
            ExtremeRecipes.Source source = ExtremeRecipes.registrationSource(handler);
            return new RegistrationBindings(handler.getClass(), source.manager, source.recipes);
        }
        if (QedRecipes.supports(handler))
            return new RegistrationBindings(handler.getClass(), QedRecipes.registrationSource(handler));
        if (GalaxyAssemblyRecipes.supports(handler))
            return new RegistrationBindings(handler.getClass(), GalaxyAssemblyRecipes.registrationSource(handler), Boolean.valueOf(GalaxyAssemblyRecipes.hardMode()));
        if (handler instanceof ICraftingHandler && BannerRecipes.supports((ICraftingHandler)handler))
            return new RegistrationBindings(BannerRecipes.registrationAnchors(handler));
        if (ForgeRegistryReferences.supportsClass(name))
            return new RegistrationBindings(ForgeRegistryReferences.registrationAnchors(handler));
        if (ProjectBlueRecipes.supportsClass(name))
            return new RegistrationBindings(handler.getClass(), ProjectBlueRecipes.registrationSource(handler));
        if(Ic2AdvancedRecipes.supportsClass(name))return new RegistrationBindings(Ic2AdvancedRecipes.registrationAnchors(handler));
        if(FuelRecipes.supportsClass(name))return new RegistrationBindings(FuelRecipes.registrationSource(handler));
        if(CannerRecipes.supports(handler)) {
            Object manager=CannerRecipes.registrationSource(handler);
            return new RegistrationBindings(handler.getClass(),manager,invoke(manager.getClass(),manager,"getRecipes",new Class<?>[0]));
        }
        if(Ic2Recipes.supports(handler)) {
            Ic2Recipes.NativeSource source=Ic2Recipes.registrationSource(handler);
            return new RegistrationBindings(handler.getClass(),source.manager,source.recipes);
        }
        if(BlastRecipes.supports(handler)) {
            Ic2Recipes.NativeSource source=BlastRecipes.registrationSource(handler);
            return new RegistrationBindings(handler.getClass(),source.manager,source.recipes,field(source.manager,"recipeCache"));
        }
        if(RefiningRecipes.supports(handler)) {
            Object manager=RefiningRecipes.registrationSource(handler);
            return new RegistrationBindings(handler.getClass(),manager,field(manager,"recipes"),field(manager,"validFluids1"),field(manager,"validFluids2"));
        }
        if(BuildcraftRecipes.supports(handler)) {
            Object manager=BuildcraftRecipes.registrationSource(handler);
            return new RegistrationBindings(handler.getClass(),manager,field(manager,"assemblyRecipes"));
        }
        if(IntegrationRecipes.supports(handler)) {
            IntegrationRecipes.Source source=IntegrationRecipes.registrationSource(handler);
            return new RegistrationBindings(handler.getClass(),source.manager,source.recipes);
        }
        if(RailRecipes.supports(handler)) {
            RailRecipes.Source source=RailRecipes.registrationSource(handler);
            List<Object> anchors=new ArrayList<>(Arrays.asList(handler.getClass(),source.manager,source.recipes));
            if(source.blast)Collections.addAll(anchors,RailBlastFuelSource.registrationAnchors(source.manager));
            return new RegistrationBindings(anchors.toArray());
        }
        if(handler instanceof ICraftingHandler&&RollingRecipes.supports((ICraftingHandler)handler)){
            RollingRecipes.Source source=RollingRecipes.registrationSource(handler);
            return new RegistrationBindings(handler.getClass(),source.manager,source.recipes);
        }
        if(CalImprintRecipes.supports(handler)) {
            CalImprintRecipes.Source source=CalImprintRecipes.registrationSource(handler);
            return new RegistrationBindings(handler.getClass(),source.values,source.ids,source.names,source.machine,source.entities,source.machineEntity,source.parts);
        }
        if(SpaceAcquisitionRecipes.supports(handler))
            return new RegistrationBindings(handler.getClass(),SpaceAcquisitionRecipes.registrationSource(handler));
        if(handler instanceof ICraftingHandler&&LootBagRecipes.supports((ICraftingHandler)handler))
            return new RegistrationBindings(LootBagRecipes.registrationSource(handler).anchors.toArray());
        String root = "com.github.dcysteine.neicustomdiagram.api.diagram.";
        if(handler instanceof ICraftingHandler&&AeWorldRecipes.supports((ICraftingHandler)handler))
            return new RegistrationBindings(AeWorldSource.registrationAnchors(handler));
        if(AeFacadeRecipes.supports(handler)) {
            AeFacadeRecipes.Source source=AeFacadeRecipes.registrationSource(handler);
            return new RegistrationBindings(handler.getClass(),source.api,source.definitions,source.items,source.parts,
                    source.facadeDefinition,source.anchorDefinition,source.anchorSource,source.facade,source.config,
                    source.categories,source.examples);
        }
        if (name.equals(root + "DiagramGroup"))
            return new RegistrationBindings(field(handler, "info"), field(handler, "matcher"));
        if (name.equals(root + "CustomDiagramGroup"))
            return new RegistrationBindings(
                    field(handler, "info"),
                    field(handler, "matcher"),
                    field(handler, "customBehaviorMap"));
        String enderManager = name.equals("crazypants.enderio.nei.VatRecipeHandler") ? "vat.VatRecipeManager"
                : name.equals("crazypants.enderio.nei.AlloySmelterRecipeHandler") ? "alloy.AlloyRecipeManager"
                : name.equals("crazypants.enderio.nei.SliceAndSpliceRecipeHandler") ? "slicensplice.SliceAndSpliceRecipeManager" : null;
        if (enderManager != null) {
            Object manager = invoke(type("crazypants.enderio.machine." + enderManager), null, "getInstance", new Class<?>[0]);
            if (manager == null) throw new Jobs.Fault("handler_not_ready", "Native EnderIO registration manager is unavailable");
            return new RegistrationBindings(handler.getClass(), manager,
                    invoke(manager.getClass(), manager, "getRecipes", new Class<?>[0]),
                    field(type("crazypants.enderio.machine.MachineRecipeRegistry"), null, "instance"));
        }
        if (handler instanceof ICraftingHandler && SpaceRecipes.supports((ICraftingHandler) handler))
            return new RegistrationBindings(handler.getClass(), SpaceRecipes.registrationSource((codechicken.nei.recipe.TemplateRecipeHandler) handler));
        return null; // Other families need their own audited semantic binding declaration.
    }

    boolean same(RegistrationBindings other) {
        if (other == null || anchors.length != other.anchors.length) return false;
        for (int index = 0; index < anchors.length; index++)
            if (!NativeDefinitionGraph.sameRoot(anchors[index], other.anchors[index])) return false;
        return true;
    }
}
