import assert from 'node:assert/strict';
import { execFileSync } from 'node:child_process';
import { readFile } from 'node:fs/promises';
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
for (const name of ['func_147439_a', 'func_147465_d', 'func_72805_g', 'func_147438_o', 'func_147455_a', 'func_147475_p', 'func_72838_d']) {
  assert.ok(preview.includes(`${name}(`), `Preview world override is not reobfuscated: ${name}`);
}
assert.ok(main.includes(`MOD_VERSION = "${version}"`), 'Build version was not substituted');
console.log(`Verified production mod: ${jar}`);
