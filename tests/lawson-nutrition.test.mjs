import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import {createHash} from 'node:crypto';
const catalog=JSON.parse(fs.readFileSync(new URL('../app/src/main/assets/nutrition/lawson-cn.json',import.meta.url),'utf8'));

test('Lawson snapshot keeps 250 distinct source identities and verifiable mass conversions',()=>{
 assert.equal(catalog.products.length,250);
 assert.equal(new Set(catalog.products.map(p=>p.id)).size,250);
 assert.equal(new Set(catalog.products.map(p=>p.source_url)).size,250);
 for(const p of catalog.products) {
  assert.equal(p.id,`lawson-cn-search-${createHash('sha256').update(p.source_url).digest('hex').slice(0,16)}`);
  assert.equal(p.basis_g,100);
  assert.equal(p.packaging_verified,false);
  assert.equal(p.sodium_mg,null);
  assert.ok(p.flags.includes('third_party_source'));
  assert.ok(p.source_basis.weight_g>0);
  for(const key of ['energy_kcal','protein_g','fat_g','carbohydrate_g']) {
   assert.ok(Number.isFinite(p[key]) && p[key]>=0);
   assert.ok(Math.abs(p[key]-p.source_nutrition[key]*100/p.source_basis.weight_g)<1e-8);
  }
 }
});

test('Lawson source servings retain original amounts without treating them as package weights',()=>{
 const sausage=catalog.products.find(p=>p.name==='香肠面包');
 assert.equal(sausage.source_basis.weight_g,95);
 assert.equal(sausage.source_nutrition.energy_kcal,253);
 assert.ok(Math.abs(sausage.energy_kcal*95/100-253)<1e-8);
 assert.equal(sausage.package_net_weight_g,undefined);
 assert.ok(catalog.products.some(p=>p.source_basis.weight_g===30));
 assert.ok(catalog.products.some(p=>p.source_basis.weight_g===40));
});
