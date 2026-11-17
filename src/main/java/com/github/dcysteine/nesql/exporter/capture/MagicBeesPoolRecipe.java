package com.github.dcysteine.nesql.exporter.capture;

import static com.github.dcysteine.nesql.exporter.capture.MagicApi.*;
import static com.github.dcysteine.nesql.exporter.source.Json.*;
import com.github.dcysteine.nesql.exporter.source.CanonicalJson;
import com.github.dcysteine.nesql.exporter.task.Jobs;
import com.google.gson.*;
import forestry.api.apiculture.*;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

/** Read-only constructor/output-cache observation. Never calls matches/getOutput
 * or BeeHelper.getMember. A caller still needs the published recipe/result path. */
final class MagicBeesPoolRecipe {
  private static final String NATIVE="magicbees.main.utils.compat.botania.SpeciesRecipeManaInfusion";
  private final Object recipe;
  private final MagicBeesPoolSource source;
  private final BotaniaBorrowed borrowed;
  private final JsonObject entry;
  private final String signature;

  MagicBeesPoolRecipe(Object recipe,MagicBeesPoolSource source){
    MagicApi.version("MagicBeesPoolRecipe","MagicBees");MagicApi.version("MagicBeesPoolRecipe","Forestry");MagicApi.version("MagicBeesPoolRecipe","Botania");
    if(recipe==null||recipe.getClass()!=type(NATIVE))throw unsupported("Unadapted native bee pool callback");
    this.recipe=recipe;this.source=source;
    borrowed=new BotaniaBorrowed(new BotaniaBorrowed.Budget());
    JsonObject definition=read(borrowed);
    entry=definition.getAsJsonObject("entry");signature=CanonicalJson.digest(definition);
  }
  JsonObject entry(){return new JsonParser().parse(entry.toString()).getAsJsonObject();}
  void verify(){
    BotaniaBorrowed now=new BotaniaBorrowed(new BotaniaBorrowed.Budget());
    if(!signature.equals(CanonicalJson.digest(read(now)))||!borrowed.same(now))
      throw new Jobs.Fault("recipe_changed","Native bee pool species, prototype or output-cache identity changed");
  }
  private JsonObject read(BotaniaBorrowed pins){
    Object inputSpecies=field(recipe,"inputSpecies"),outputSpecies=field(recipe,"outputSpecies"),beeType=field(recipe,"beeType");
    if(!(inputSpecies instanceof IAlleleBeeSpecies)||!(outputSpecies instanceof IAlleleBeeSpecies)
        ||beeType==null||beeType.getClass()!=EnumBeeType.class)throw unsupported("Unadapted native bee pool species/form fields");
    pins.bind(inputSpecies);pins.bind(outputSpecies);pins.bind(beeType);
    String input=species((IAlleleBeeSpecies)inputSpecies),output=species((IAlleleBeeSpecies)outputSpecies),form;
    switch((EnumBeeType)beeType){
      case DRONE:form="beeDroneGE";break;case PRINCESS:form="beePrincessGE";break;
      case QUEEN:form="beeQueenGE";break;case LARVAE:form="beeLarvaeGE";break;
      default:throw unsupported("Unadapted native bee form");
    }
    ItemStack display=pins.stack(field(recipe,"input")),prototype=pins.stack(field(recipe,"output")),
        cached=pins.stack(field(recipe,"outputCache"));
    String registry=source.formRegistry(form);
    for(ItemStack stack:new ItemStack[]{display,prototype,cached})
      if(stack.stackSize<=0||!registry.equals(Item.itemRegistry.getNameForObject(stack.getItem())))
        throw unsupported("Native bee pool stack differs from its bound form");
    return object("input",RecipeRow.stackEvidence(display),"entry",object("kind","bee","form",form,
        "inputSpecies",input,"outputSpecies",output,"alchemy",field(recipe,"isAlchemy"),
        "conjuration",field(recipe,"isConjuration"),"mana",field(recipe,"mana"),
        "displayInput",RecipeRow.stackEvidence(display),
        "prototype",RecipeRow.stackEvidence(prototype),"cachedOutput",RecipeRow.stackEvidence(cached)));
  }
  private String species(IAlleleBeeSpecies allele){
    JsonObject value=source.cachedAllele(allele);
    if(!value.get("sourceIdentity").getAsBoolean()||!value.get("beeSpecies").getAsBoolean())
      throw unsupported("Native bee pool recipe species is outside the bound source identity");
    return value.get("uid").getAsString();
  }
  private static Jobs.Fault unsupported(String text){return new Jobs.Fault("recipe_unsupported",text);}
}
