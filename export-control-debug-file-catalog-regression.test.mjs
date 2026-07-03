import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';

const repoRoot = process.cwd();
const readSource = (relativePath) => fs.readFileSync(path.join(repoRoot, relativePath), 'utf8');
const kernelPath = 'src/main/java/com/github/dcysteine/nesql/elysium/kernel';

const controlFile = readSource(`${kernelPath}/ExportControlFile.java`);
const debugFile = readSource(`${kernelPath}/ExportDebugFile.java`);
const rawManifestBuilder = readSource(
  'src/main/java/com/github/dcysteine/nesql/exporter/local/RawExportManifestBuilder.java',
);
const integrityManifestWriter = readSource(
  'src/main/java/com/github/dcysteine/nesql/exporter/main/ExportIntegrityManifestWriter.java',
);

test('ControlFS file enum validates its stable ABI catalog at initialization', () => {
  assert.match(controlFile, /VALIDATED_FILES =\s*validateAndFreeze\(Arrays\.asList\(values\(\)\)\)/);
  assert.match(controlFile, /ControlFS file catalog must not be empty/);
  assert.match(controlFile, /ControlFS file descriptor must not be null/);
  assert.match(controlFile, /Duplicate ControlFS manifest key/);
  assert.match(controlFile, /Duplicate ControlFS index key/);
  assert.match(controlFile, /Duplicate ControlFS file name/);
  assert.match(controlFile, /Duplicate ControlFS schema version/);
  assert.match(controlFile, /ControlFS file name must be a single JSON file/);
  assert.match(controlFile, /for \(ExportControlFile file : VALIDATED_FILES\)/);
  assert.doesNotMatch(controlFile, /for \(ExportControlFile file : values\(\)\) \{\s*if \(file != INDEX\)/);
});

test('DebugFS file enum validates debug and validation-alias paths at initialization', () => {
  assert.match(debugFile, /VALIDATED_FILES =\s*validateAndFreeze\(Arrays\.asList\(values\(\)\)\)/);
  assert.match(debugFile, /DebugFS file catalog must not be empty/);
  assert.match(debugFile, /DebugFS file descriptor must not be null/);
  assert.match(debugFile, /Duplicate DebugFS manifest key/);
  assert.match(debugFile, /Duplicate DebugFS path/);
  assert.match(debugFile, /Duplicate DebugFS validation alias path/);
  assert.match(debugFile, /Duplicate DebugFS schema version/);
  assert.match(debugFile, /DebugFS validation alias path must live under validation\//);
  assert.match(debugFile, /must be a runtime-relative JSON path/);
});

test('manifest and integrity writers consume validated ControlFS and DebugFS enums', () => {
  assert.match(rawManifestBuilder, /for \(ExportControlFile file : ExportControlFile\.values\(\)\)/);
  assert.match(rawManifestBuilder, /file\.manifestKey\(\), file\.rawExportPath\(\)/);
  assert.match(rawManifestBuilder, /for \(ExportDebugFile file : ExportDebugFile\.values\(\)\)/);
  assert.match(rawManifestBuilder, /file\.manifestKey\(\), file\.rawExportDebugPath\(\)/);
  assert.match(integrityManifestWriter, /for \(ExportControlFile file : ExportControlFile\.values\(\)\)/);
  assert.match(integrityManifestWriter, /for \(ExportDebugFile file : ExportDebugFile\.values\(\)\)/);
  assert.doesNotMatch(rawManifestBuilder, /"control\/abi\.json"/);
  assert.doesNotMatch(integrityManifestWriter, /"debug\/trace\/latest\.json"/);
});
