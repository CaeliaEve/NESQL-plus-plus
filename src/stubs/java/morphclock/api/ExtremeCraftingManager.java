package morphclock.api;

import java.util.Map;

/**
 * Stub class for ExtremeCraftingManager.
 */
public class ExtremeCraftingManager {
    private static ExtremeCraftingManager instance;

    public static ExtremeCraftingManager getInstance() {
        if (instance == null) {
            instance = new ExtremeCraftingManager();
        }
        return instance;
    }

    public Map<Object, IRecipe> getRecipes() {
        return null;
    }
}
