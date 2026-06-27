package com.github.dcysteine.nesql.exporter.local;

import java.util.List;

final class RawExportValidation {
    String status;
    String readinessStatus;
    long missingTextureCount;
    long missingAnimationMetadataCount;
    long missingGroupOrOrderCount;
    List<String> failedStages;
    List<RawValidationGate> gates;
}
