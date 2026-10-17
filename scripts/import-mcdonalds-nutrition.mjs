import { createHash } from 'node:crypto';
import { pathToFileURL } from 'node:url';

export const SOURCE_URL = 'https://www.mcdonalds.com.cn/nutrition_calculator';

function check(condition, message) {
  if (!condition) throw new Error(message);
}

function nutrient(value) {
  if (value == null || value === '') return null;
  check((typeof value === 'string' && /^\d+(?:\.\d+)?$/.test(value)) || typeof value === 'number', 'Invalid nutrient value');
  const number = Number(value);
  check(Number.isFinite(number) && number >= 0, 'Invalid nutrient value');
  return number;
}

/** Parse data as JSON only; never evaluate any JavaScript supplied by the site. */
export function extractCatalog(html, collectedAt = new Date().toISOString()) {
  const match = html.match(/var raw_data\s*=\s*(\{[\s\S]*?\});\s*var raw_category_list/);
  check(match, 'Nutrition data structure not found');
  const raw = JSON.parse(match[1]);
  const update = html.match(/营养成分数据更新时间为\s*(\d{4})年\s*(\d{1,2})月/);
  check(update && Number(update[2]) >= 1 && Number(update[2]) <= 12, 'Nutrition data update date not found');
  check(Number.isFinite(Date.parse(collectedAt)), 'Invalid collection date');
  check(Array.isArray(raw.category_list) && raw.category_list.length > 0 && raw.Product && raw.ProductType, 'Nutrition data is empty');
  const products = Object.entries(raw.Product).map(([id, product]) => {
    check(product.id === id && typeof product.title === 'string' && product.title.trim() && Array.isArray(product.ProductType) && product.ProductType.length, 'Invalid product reference');
    check(new Set(product.ProductType).size === product.ProductType.length, 'Duplicate size reference');
    return {
      id, name: product.title, note: product.nutrition_desc ?? '',
      variants: product.ProductType.map(variantId => {
        const variant = raw.ProductType[variantId];
        check(variant && variant.id === variantId && typeof variant.title === 'string' && variant.title.trim() && Array.isArray(variant.nutrition) && variant.nutrition.length >= 14, 'Invalid size reference or nutrition structure');
        const n = variant.nutrition;
        return { id: variantId, name: variant.title, note: variant.nutrition_desc ?? '', perServing: {
          energyKj: nutrient(n[1]), proteinG: nutrient(n[2]), fatG: nutrient(n[3]),
          carbohydrateG: nutrient(n[4]), sodiumMg: nutrient(n[5]), calciumMg: nutrient(n[13]),
        } };
      }),
    };
  });
  check(products.length > 0, 'Product data is empty');
  const categories = raw.category_list.map(category => {
    const item = category.ProductCategory;
    check(item && typeof item.id === 'string' && typeof item.title === 'string' && item.title.trim() && Array.isArray(category.Product) && category.Product.length > 0, 'Category data is empty');
    check(new Set(category.Product).size === category.Product.length && category.Product.every(id => Object.hasOwn(raw.Product, id)), 'Invalid category product reference');
    return { id: item.id, name: item.title, productIds: category.Product };
  });
  check(new Set(categories.map(x => x.id)).size === categories.length, 'Duplicate category reference');
  const categorized = new Set(categories.flatMap(x => x.productIds));
  check(products.every(product => categorized.has(product.id)), 'Uncategorized product reference');
  return {
    schemaVersion: 1, brandName: '麦当劳', sourceUrl: SOURCE_URL,
    sourceUpdated: `${update[1]}-${update[2].padStart(2, '0')}`, collectedAt,
    sourceSha256: createHash('sha256').update(match[1], 'utf8').digest('hex'),
    disclaimer: '营养数据以官网单份餐品计量，仅供参考；实际份量、原料及配方可能变化。不代表实时在售菜单。',
    categories, products,
  };
}

// Print the validated snapshot to stdout. This command never overwrites an existing asset.
if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) {
  const response = await fetch(SOURCE_URL, { signal: AbortSignal.timeout(25_000) });
  check(response.ok, `Official page returned HTTP ${response.status}`);
  console.log(JSON.stringify(extractCatalog(await response.text())));
}
