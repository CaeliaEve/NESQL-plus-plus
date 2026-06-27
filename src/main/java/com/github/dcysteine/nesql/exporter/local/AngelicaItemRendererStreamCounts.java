package com.github.dcysteine.nesql.exporter.local;

import java.util.LinkedHashSet;
import java.util.Set;

/** Count contract for Angelica item renderer and shader item fact streaming. */
final class AngelicaItemRendererStreamCounts {
    long itemRenderers;
    long shaderItems;
    long shaderItemsRequiringCapture;
    long unknownSpecialRenderers;
    final Set<String> captureRequiredItemIds = new LinkedHashSet<String>();
}
