import assert from 'node:assert/strict';
import { execFileSync } from 'node:child_process';
import { readFile, mkdtemp, rm } from 'node:fs/promises';
import os from 'node:os';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const root = fileURLToPath(new URL('../', import.meta.url));
const properties = await readFile(path.join(root, 'gradle.properties'), 'utf8');
const version = properties.match(/^modVersion=([0-9A-Za-z.+-]+)$/m)?.[1];
assert.ok(version, 'Missing modVersion');
const jar = path.resolve(process.argv[2] ?? path.join(root, 'build', 'libs', `NESQL++-${version}.jar`));
const jdk = process.env.JAVA_HOME;
assert.ok(jdk, 'JAVA_HOME must point to a Java 8 JDK');
const tool = (name, args) => execFileSync(path.join(jdk, 'bin', name + (process.platform === 'win32' ? '.exe' : '')),
  ['-J-Xms16m', '-J-Xmx128m', '-J-XX:+UseSerialGC', ...args], { encoding: 'utf8', windowsHide: true });
const entries = tool('jar', ['tf', jar]).split(/\r?\n/);
assert.ok(entries.includes('mcmod.info'), 'The production mod descriptor is missing');
for(const name of ['NativeUsageReferences','NativeCellQueries','NativeOcQueries','NativeOcManual','NativeOcCallbacks','NativeUsageAdmission'])
  assert.ok(entries.includes(`com/github/dcysteine/nesql/exporter/capture/${name}.class`),'Missing native usage adapter: '+name);
assert.ok(entries.includes('native-usage-admission.json'),'Pinned usage admission resource is missing');
assert.ok(!entries.some(entry=>entry.includes('NativeUsageTest')),'Native usage test fixture leaked into production JAR');
const temporary = await mkdtemp(path.join(os.tmpdir(), 'nesql-manifest-'));
try {
  execFileSync(path.join(jdk, 'bin', process.platform === 'win32' ? 'jar.exe' : 'jar'),
    ['-J-Xms16m', '-J-Xmx128m', '-J-XX:+UseSerialGC', 'xf', jar, 'META-INF/MANIFEST.MF','native-usage-admission.json'], { cwd: temporary, windowsHide: true });
  const admission=await readFile(path.join(temporary,'native-usage-admission.json'),'utf8');
  assert.equal(admission,await readFile(path.join(root,'src/main/resources/native-usage-admission.json'),'utf8'),'Production usage admission differs from reviewed source');
  const policy=JSON.parse(admission);
  assert.equal(policy.format,'nesql.pinned-usage-callbacks');
  assert.ok(policy.classes.length>0&&policy.classes.every(row=>/^[a-f0-9]{64}$/.test(row.sha256)&&!('testSha256' in row)),'Test admission hashes leaked into production');
  const manifest = (await readFile(path.join(temporary, 'META-INF/MANIFEST.MF'), 'utf8')).replace(/\r?\n /g, '');
  assert.match(manifest, /^FMLCorePlugin: com\.github\.dcysteine\.nesql\.exporter\.core\.RendererPlugin$/m, 'Native renderer clock core-plugin missing');
  assert.match(manifest, /^FMLCorePluginContainsFMLMod: true$/m, 'Core-plugin must retain the exporter mod container');
} finally { await rm(temporary, { recursive: true, force: true }); }
for (const name of ['RendererPlugin', 'RendererTransformer', 'RendererClock'])
  assert.ok(entries.includes(`com/github/dcysteine/nesql/exporter/core/${name}.class`), `Missing capture clock class: ${name}`);
assert.ok(entries.includes('com/github/dcysteine/nesql/exporter/capture/NativeOreStorage.class'), 'Native ore storage adapter missing');
const aeWorld = tool('javap', ['-classpath', jar, '-p', '-c', 'com.github.dcysteine.nesql.exporter.capture.AeWorldSource']);
assert.ok(aeWorld.includes('NativeOreStorage.names:'), 'AE2 world source did not bind the ore storage adapter');
for (const entry of entries.filter(entry => entry.endsWith('.class'))) {
  assert.match(entry, /^com\/github\/dcysteine\/nesql\/exporter\//, `Embedded dependency or game stub: ${entry}`);
}
const command = tool('javap', ['-classpath', jar, '-p', 'com.github.dcysteine.nesql.exporter.main.ExportCommand']);
// Verify the command overrides, including CommandBase's permission-level method.
for (const name of ['func_71517_b', 'func_71518_a', 'func_82362_a', 'func_71515_b']) {
  assert.ok(command.includes(`${name}(`), `Production command is not reobfuscated: ${name}`);
}
const main = tool('javap', ['-classpath', jar, '-constants', 'com.github.dcysteine.nesql.exporter.main.Main']);
const preview = tool('javap', ['-classpath', jar, '-p', 'com.github.dcysteine.nesql.exporter.capture.Preview$Space']);
const magic = tool('javap', ['-classpath', jar, '-p', '-c', 'com.github.dcysteine.nesql.exporter.capture.Magic']);
// TC4's three-argument overload always draws the dark/unknown-aspect variant.
assert.match(magic, /iconst_0\s+\d+: invokestatic[^\n]+UtilsFX\.drawTag:\(IILthaumcraft\/api\/aspects\/Aspect;FIDIFZ\)V/,
  'Aspect capture must explicitly select the native colored (not gray) branch');
for (const name of ['func_147439_a', 'func_147465_d', 'func_72805_g', 'func_147438_o', 'func_147455_a', 'func_147475_p', 'func_72838_d']) {
  assert.ok(preview.includes(`${name}(`), `Preview world override is not reobfuscated: ${name}`);
}
assert.ok(main.includes(`MOD_VERSION = "${version}"`), 'Build version was not substituted');
console.log(`Verified production mod: ${jar}`);
