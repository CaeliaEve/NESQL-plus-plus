package com.github.dcysteine.nesql.exporter.capture;

import codechicken.nei.recipe.ICraftingHandler;
import codechicken.nei.recipe.TemplateRecipeHandler;

/** Thin route for the shared native Blood Orb workbench reader. */
final class BloodOrbShapelessRecipes implements RegistryRecipes {
  private final BloodOrbCraftingRecipes source;
  static boolean supports(ICraftingHandler handler){return handler.getClass().getName().equals("WayofTime.alchemicalWizardry.client.nei.NEIBloodOrbShapelessHandler");}
  BloodOrbShapelessRecipes(TemplateRecipeHandler handler){source=new BloodOrbCraftingRecipes(handler);}
  public int size(){return source.size();}
  public boolean capture(int index,RecipeRow row){return source.capture(index,row);}
  public void verify(){source.verify();}
  public void finishVerification(){source.finishVerification();}
  public void close(){source.close();}
}
