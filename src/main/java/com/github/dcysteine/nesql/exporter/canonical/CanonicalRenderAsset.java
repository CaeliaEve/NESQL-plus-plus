package com.github.dcysteine.nesql.exporter.canonical;

import java.util.List;
import java.util.Map;

/**
 * NESQL++ canonical render asset contract.
 */
public class CanonicalRenderAsset {
    public String schemaVersion;
    public String contractVersion;
    public String assetId;
    public String variantKey;
    public String sourceType;
    public String family;
    public String contentHash;
    public String mode;
    public String renderMode;
    public String animationMode;
    public String captureMethod;
    public String captureSource;
    public String rendererFamily;
    public String playbackHint;
    public String sourceFormat;
    public String sourcePath;
    public String atlasTexture;
    public String atlasExportFile;
    public String spriteMetadataFile;
    public String contractFile;
    public String nativeSpriteAtlasFile;
    public String primaryArtifact;
    public String staticFile;
    public String framePattern;
    public Integer frameCount;
    public Integer configuredFrameCount;
    public Integer capturedFrameCount;
    public String loopMode;
    public Integer detectionFrameCount;
    public Long captureTimeoutMs;
    public String atlasGroup;
    public Boolean atlasCandidate;
    public String atlasFile;
    public List<Map<String, Object>> frames;
    public List<Map<String, Object>> timeline;
    public Integer frameDurationMs;
    public Boolean loop;
    public Map<String, Object> rect;
    public Map<String, Object> baseSize;
    public List<Map<String, Object>> layers;
    public Map<String, Object> rendererContract;
    public Map<String, Object> shaderContract;
    public Map<String, Object> captureContract;
}
