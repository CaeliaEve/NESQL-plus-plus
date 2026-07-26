import assert from 'node:assert/strict';
import fs from 'node:fs';

const source = fs.readFileSync(
  new URL('./src/main/java/com/github/dcysteine/nesql/exporter/plugin/nei/NeiItemUniverse.java', import.meta.url),
  'utf8',
);

assert.match(source, /ItemStack normalized = IdUtil\.stableIdentityCopy\(stack\);/);
const idUtil = fs.readFileSync(
  new URL('./src/main/java/com/github/dcysteine/nesql/exporter/util/IdUtil.java', import.meta.url),
  'utf8',
);
assert.match(idUtil, /ItemStack identityStack = stableIdentityCopy\(itemStack\);/);
assert.match(idUtil, /!normalized\.isItemStackDamageable\(\)/);
assert.match(idUtil, /normalized\.setItemDamage\(0\)/);
assert.match(idUtil, /nbt\.removeTag\("ench"\)/);
assert.match(idUtil, /nbt\.removeTag\("RepairCost"\)/);
assert.doesNotMatch(source, /stack\.setTagCompound\(null\);[\s\S]*return;[\s\S]*normalizeStableIdentity/s);

console.log('NEI item stable identity architecture regression passed');
