import test from 'node:test';
import assert from 'node:assert/strict';

let extractCatalog;
try {
  ({ extractCatalog } = await import('../scripts/import-mcdonalds-nutrition.mjs'));
} catch (error) {
  if (error.code !== 'ERR_MODULE_NOT_FOUND') throw error;
}

function fixture() {
  return {
    category_list: [{ ProductCategory: { id: '7', title: '汉堡和卷' }, Product: ['49'] }],
    Product: { '49': { id: '49', title: '巨无霸', nutrition_desc: null, ProductType: ['28'] } },
    ProductType: { '28': { id: '28', title: '1个', nutrition_desc: null,
      nutrition: ['220', '2146', '27', '26', '42', '961', '10', '0', '8', '6', '65', '8', '3', '171'] } },
  };
}

function extract(raw = fixture()) {
  assert.equal(typeof extractCatalog, 'function', 'Offline nutrition importer must be implemented');
  return extractCatalog(`<script>var raw_data = ${JSON.stringify(raw)}; var raw_category_list = [];</script>
    营养成分数据以单份餐品计量。上述营养成分数据更新时间为2025年4月。`, '2026-10-17T00:00:00Z');
}

test('extracts official per-serving fields without treating unknown array columns as weight', () => {
  const catalog = extract();
  assert.equal(catalog.brandName, '麦当劳');
  assert.equal(catalog.sourceUpdated, '2025-04');
  assert.equal(catalog.collectedAt, '2026-10-17T00:00:00Z');
  assert.equal(catalog.sourceUrl, 'https://www.mcdonalds.com.cn/nutrition_calculator');
  assert.match(catalog.sourceSha256, /^[a-f0-9]{64}$/);
  assert.deepEqual(catalog.categories, [{ id: '7', name: '汉堡和卷', productIds: ['49'] }]);
  assert.deepEqual(catalog.products[0].variants[0], {
    id: '28', name: '1个', note: '',
    perServing: { energyKj: 2146, proteinG: 27, fatG: 26, carbohydrateG: 42, sodiumMg: 961, calciumMg: 171 },
  });
  assert.equal('weightG' in catalog.products[0].variants[0], false);
});

test('keeps different sizes and their official order', () => {
  const raw = fixture();
  raw.Product['49'].ProductType.push('29');
  raw.ProductType['29'] = { ...raw.ProductType['28'], id: '29', title: '大' };
  assert.deepEqual(extract(raw).products[0].variants.map(x => x.name), ['1个', '大']);
});

test('preserves missing nutrient values rather than manufacturing zero', () => {
  const raw = fixture();
  raw.ProductType['28'].nutrition[5] = null;
  raw.ProductType['28'].nutrition[13] = '';
  assert.equal(extract(raw).products[0].variants[0].perServing.sodiumMg, null);
  assert.equal(extract(raw).products[0].variants[0].perServing.calciumMg, null);
});

test('rejects missing category, product, or size references', () => {
  for (const mutate of [
    raw => raw.category_list[0].Product.push('missing'),
    raw => raw.Product['49'].ProductType.push('missing'),
    raw => { raw.category_list = []; },
  ]) {
    const raw = fixture(); mutate(raw);
    assert.throws(() => extract(raw), /reference|empty/i);
  }
});

test('rejects negative, nonnumeric, and nonfinite nutrition data', () => {
  for (const value of ['-1', 'unknown', '1e999', 'NaN']) {
    const raw = fixture(); raw.ProductType['28'].nutrition[1] = value;
    assert.throws(() => extract(raw), /nutrient/i);
  }
});

test('refuses changed or incomplete page structure instead of saving an empty catalog', () => {
  assert.equal(typeof extractCatalog, 'function');
  assert.throws(() => extractCatalog('<html>maintenance</html>', '2026-10-17T00:00:00Z'), /data|structure/i);
});

test('does not execute JavaScript from the downloaded page', () => {
  assert.equal(typeof extractCatalog, 'function');
  assert.throws(() => extractCatalog('<script>var raw_data = (() => { throw Error("executed") })(); var raw_category_list = [];</script>', '2026-10-17T00:00:00Z'), /data|JSON|structure/i);
});
