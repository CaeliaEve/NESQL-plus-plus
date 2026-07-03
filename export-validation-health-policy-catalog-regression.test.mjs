import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';

const repoRoot = process.cwd();
const readSource = (relativePath) => fs.readFileSync(path.join(repoRoot, relativePath), 'utf8');
const validationMain = 'src/main/java/com/github/dcysteine/nesql/exporter/main';

const healthCatalog = readSource(`${validationMain}/ExportValidationHealthPolicyCatalog.java`);
const healthPolicy = readSource(`${validationMain}/ExportValidationHealthPolicy.java`);

test('validation health policy is driven by explicit rule descriptor catalogs', () => {
  for (const catalog of [
    'WARNING_RULES',
    'BLOCKED_RULES',
    'ACTIONABLE_ISSUE_RULES',
    'STATUS_RULES',
  ]) {
    assert.match(healthCatalog, new RegExp(catalog));
  }
  assert.match(healthCatalog, /validateWarningRules\(Arrays\.asList\(/);
  assert.match(healthCatalog, /validateBlockedRules\(Arrays\.asList\(/);
  assert.match(healthCatalog, /validateActionableIssueRules\(Arrays\.asList\(/);
  assert.match(healthCatalog, /validateStatusRules\(Arrays\.asList\(/);
  assert.match(healthCatalog, /private interface WarningEmitter/);
  assert.match(healthCatalog, /private interface ReportPredicate/);
  assert.match(healthCatalog, /private interface MessageBuilder/);
  assert.match(healthCatalog, /private interface JsonArraySelector/);
  assert.match(healthCatalog, /private static final class WarningRuleDescriptor/);
  assert.match(healthCatalog, /private static final class BlockedRuleDescriptor/);
  assert.match(healthCatalog, /private static final class ActionableIssueDescriptor/);
  assert.match(healthCatalog, /private static final class StatusRuleDescriptor/);
  assert.match(healthCatalog, /Unknown export validation " \+ label \+ ": " \+ id/);
  assert.match(healthCatalog, /Duplicate export validation " \+ label \+ ": " \+ id/);
  assert.match(healthCatalog, /Missing export validation " \+ label \+ ": " \+ id/);
  assert.match(healthCatalog, /ExportValidationNativeUiEvidenceCatalog\.collectWarnings\(report\)/);
  assert.match(healthCatalog, /ExportValidationNativeUiEvidenceCatalog\.isAbiBlocked\(report\)/);
});

test('validation health rule order is stable and descriptor visible', () => {
  const expectedWarningRules = [
    'render-asset-manifest',
    'browser-layout',
    'item-shards',
    'recipe-shards',
    'render-images',
    'static-atlas-files',
    'animated-atlas-files',
    'render-asset-primary-artifacts',
    'render-asset-timeline-frames',
    'suspicious-static-singularity',
    'atlas-manifest-assets',
    'browser-layout-atlas-coverage',
    'export-path-hygiene',
    'semantic-diagnostics',
    'semantic-classification-coverage',
    'semantic-missing-facet-families',
    'semantic-missing-sort-key-families',
    'semantic-rule-pack',
    'angelica-backend',
    'render-texture-sprites-timing',
    'render-shader-items-capture',
    'render-unknown-special-renderers',
    'render-framebuffer-captures',
    'nei-handler-metadata',
    'native-ui-evidence',
  ];
  let cursor = -1;
  for (const id of expectedWarningRules) {
    const index = healthCatalog.indexOf(`"${id}"`, cursor + 1);
    assert.notEqual(index, -1, `missing health warning rule descriptor ${id}`);
    assert.equal(index > cursor, true, `health warning rule order drifted: ${id}`);
    cursor = index;
  }

  for (const id of [
    'no-item-facts',
    'no-recipe-facts',
    'machine-paths',
    'semantic-identity-map-mismatch',
    'semantic-diagnostic-item-mismatch',
    'native-ui-abi',
    'semantic-unclassified-families',
    'semantic-missing-facets',
    'semantic-missing-sort-keys',
    'blocked',
    'warning',
    'healthy',
  ]) {
    assert.equal(healthCatalog.includes(`"${id}"`), true, `missing health descriptor ${id}`);
  }
});

test('validation health policy service is a thin catalog dispatcher', () => {
  for (const call of [
    'ExportValidationHealthPolicyCatalog.collectWarnings(report)',
    'ExportValidationHealthPolicyCatalog.applyBlockedRules(report)',
    'ExportValidationHealthPolicyCatalog.collectActionableIssues(report)',
    'ExportValidationHealthPolicyCatalog.resolveStatus(report)',
  ]) {
    assert.equal(healthPolicy.includes(call), true, `missing health policy catalog call ${call}`);
  }
  assert.doesNotMatch(healthPolicy, /report\.warnings\.add/);
  assert.doesNotMatch(healthPolicy, /report\.blockedIssues\.add/);
  assert.doesNotMatch(healthPolicy, /report\.actionableIssues\.add/);
  assert.doesNotMatch(healthPolicy, /report\.healthStatus =/);
  assert.doesNotMatch(healthPolicy, /ExportValidationAbiCatalog\./);
  assert.doesNotMatch(healthPolicy, /ExportValidationNativeUiEvidenceCatalog\./);
});
