package com.github.dcysteine.nesql.exporter.local;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Count contract for Angelica framebuffer capture fact streaming. */
final class AngelicaFramebufferCaptureStreamCounts {
    long framebufferCaptures;
    long framebufferCapturesWithoutFrames;
    final Set<String> captureAssetIds = new LinkedHashSet<String>();
    final Set<String> captureVariantKeys = new LinkedHashSet<String>();
    final List<String> framebufferCapturesWithoutFramesSamples = new ArrayList<String>();
}
