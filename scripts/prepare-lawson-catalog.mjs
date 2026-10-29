import fs from 'node:fs';
import assert from 'node:assert/strict';
import {createHash} from 'node:crypto';

const input = JSON.parse(fs.readFileSync('docs/nutrition/lawson-cn-search-catalog.json', 'utf8'));
assert.equal(input.products.length, 250);
assert.equal(new Set(input.products.map(p => p.id)).size, 250);
const products = input.products.map(p => {
  assert.equal(p.brand, '罗森');
  assert.equal(p.id, `lawson-cn-search-${createHash('sha256').update(p.detail_url).digest('hex').slice(0,16)}`);
  assert.ok(p.source_basis.weight_g > 0 && p.per_100g);
  for (const [key, value] of Object.entries(p.per_100g)) {
    if (p.source_nutrition[key] === null) assert.equal(value, null);
    else assert.ok(Number.isFinite(value) && value >= 0 && Math.abs(value - p.source_nutrition[key] * 100 / p.source_basis.weight_g) < 1e-8);
  }
  return {id:p.id, source_row:p.source_position, category:p.category, name:p.name, basis_g:100, ...p.per_100g,
    source_url:p.detail_url, source_page_url:p.source_page_url, source_basis:p.source_basis, source_nutrition:p.source_nutrition,
    evidence_summary:p.evidence_summary, packaging_verified:false, source_updated_on:null, flags:p.flags};
});
const catalog = {schema_version:1, brand:'罗森', source_name:'FatSecret 中国站搜索摘要', collected_on:input.compiled_on,
  disclaimer:'第三方资料，未核验包装。地区、配方及在售情况可能变化；缺失营养素不按零计算。',
  source_url:'https://www.fatsecret.cn/热量营养/罗森', source_pages:input.pages, products};
fs.writeFileSync('app/src/main/assets/nutrition/lawson-cn.json', JSON.stringify(catalog,null,2)+'\n');
console.log(`Prepared ${products.length} Lawson products`);
