package com.github.dcysteine.nesql.exporter.core;

import cpw.mods.fml.relauncher.IFMLLoadingPlugin;
import java.util.Map;

@IFMLLoadingPlugin.MCVersion("1.7.10")
@IFMLLoadingPlugin.SortingIndex(1001) // Observe vanilla SRG names after FML's deobfuscation transformer.
@IFMLLoadingPlugin.TransformerExclusions({"com.github.dcysteine.nesql.exporter.core."})
public final class RendererPlugin implements IFMLLoadingPlugin {
    @Override public String[] getASMTransformerClass() { return new String[] {"com.github.dcysteine.nesql.exporter.core.RendererTransformer"}; }
    @Override public String getModContainerClass() { return null; }
    @Override public String getSetupClass() { return null; }
    @Override public void injectData(Map<String, Object> data) { }
    @Override public String getAccessTransformerClass() { return null; }
}
