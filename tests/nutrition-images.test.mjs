import test from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { createHash } from 'node:crypto';

let extractImageManifest;
try { ({ extractImageManifest } = await import('../scripts/download-mcdonalds-images.mjs')); }
catch (error) { if (error.code !== 'ERR_MODULE_NOT_FOUND') throw error; }

test('matches official images by both product ID and name, with traceable local paths', () => {
  assert.equal(typeof extractImageManifest, 'function', 'Official image importer must exist');
  const raw = { Product: { '49': { id: '49', title: '巨无霸', media_url: 'upload/attachment/test.png' } } };
  const manifest = extractImageManifest(`var raw_data = ${JSON.stringify(raw)}; var raw_category_list`, [{ id: '49', name: '巨无霸' }]);
  assert.deepEqual(manifest.images, [{ productId: '49', productName: '巨无霸', assetPath: 'nutrition/images/49.png', sourceUrl: 'https://officiaweb-afd.mcdonalds.com.cn/public/upload/attachment/test.png' }]);
});

test('unmatched names and absent images are recorded rather than assigned another image', () => {
  assert.equal(typeof extractImageManifest, 'function');
  const raw = { Product: { '49': { id: '49', title: '其他产品', media_url: 'upload/attachment/test.png' } } };
  const manifest = extractImageManifest(`var raw_data = ${JSON.stringify(raw)}; var raw_category_list`, [{ id: '49', name: '巨无霸' }, { id: '21', name: '薯条' }]);
  assert.equal(manifest.images.length, 0);
  assert.deepEqual(manifest.missing.map(x => x.productId), ['49', '21']);
});

test('rejects image paths outside the official attachment directory', () => {
  assert.equal(typeof extractImageManifest, 'function');
  const raw = { Product: { '49': { id: '49', title: '巨无霸', media_url: 'https://example.com/file.png' } } };
  assert.throws(() => extractImageManifest(`var raw_data = ${JSON.stringify(raw)}; var raw_category_list`, [{ id: '49', name: '巨无霸' }]), /image path/i);
});

test('bundled official images cover the catalog exactly and retain verified PNG contents', () => {
  const root = new URL('../app/src/main/assets/', import.meta.url);
  const catalog = JSON.parse(readFileSync(new URL('nutrition/mcdonalds-cn.json', root)));
  const manifest = JSON.parse(readFileSync(new URL('nutrition/mcdonalds-images.json', root)));
  assert.deepEqual(manifest.missing, []);
  assert.deepEqual(manifest.images.map(x => x.productId).sort(), catalog.products.map(x => x.id).sort());
  for (const image of manifest.images) {
    assert.equal(image.productName, catalog.products.find(p => p.id === image.productId).name);
    assert.equal(new URL(image.sourceUrl).hostname, 'officiaweb-afd.mcdonalds.com.cn');
    const bytes = readFileSync(new URL(image.assetPath, root));
    assert.equal(createHash('sha256').update(bytes).digest('hex'), image.sha256);
    assert.deepEqual([...bytes.subarray(0, 8)], [137,80,78,71,13,10,26,10]);
    assert.ok(bytes.readUInt32BE(16) > 0 && bytes.readUInt32BE(20) > 0);
  }
});
