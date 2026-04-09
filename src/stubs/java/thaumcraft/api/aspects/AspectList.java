package thaumcraft.api.aspects;

import java.util.Map;

/**
 * Stub class for AspectList.
 */
public class AspectList {
    public int getAmount(Aspect aspect) {
        return 0;
    }

    public Aspect[] getAspects() {
        return null;
    }

    public int size() {
        return 0;
    }

    public Map<Aspect, Integer> getAspectsWithData() {
        return null;
    }

    public AspectList add(Aspect aspect, int amount) {
        return this;
    }
}
