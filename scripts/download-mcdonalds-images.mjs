import { readFile, writeFile, mkdir } from 'node:fs/promises';
import { createHash } from 'node:crypto';
import { pathToFileURL } from 'node:url';
import { SOURCE_URL } from './import-mcdonalds-nutrition.mjs';

const IMAGE_BASE = 'https://officiaweb-afd.mcdonalds.com.cn/public/';

export function extractImageManifest(html, products) {
  const match = html.match(/var raw_data\s*=\s*(\{[\s\S]*?\});\s*var raw_category_list/);
  if (!match) throw Error('Official image data structure not found');
  const raw = JSON.parse(match[1]);
  if (!raw.Product) throw Error('Official products missing');
  const images = [], missing = [];
  for (const product of products) {
    const official = raw.Product[product.id];
    if (!official || official.id !== product.id || official.title !== product.name || !official.media_url) {
      missing.push({ productId: product.id, productName: product.name });
      continue;
    }
    if (!/^\d+$/.test(product.id) || !/^upload\/attachment\/[\w/-]+\.png$/.test(official.media_url)) throw Error('Invalid official image path');
    images.push({ productId: product.id, productName: product.name, assetPath: `nutrition/images/${product.id}.png`, sourceUrl: new URL(official.media_url, IMAGE_BASE).href });
  }
  return { sourceUrl: SOURCE_URL, images, missing };
}

if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) {
  const root = new URL('../app/src/main/assets/', import.meta.url);
  const catalog = JSON.parse(await readFile(new URL('nutrition/mcdonalds-cn.json', root), 'utf8'));
  const page = await fetch(SOURCE_URL, { signal: AbortSignal.timeout(30000) });
  if (!page.ok) throw Error(`Official page HTTP ${page.status}`);
  const html = await page.text();
  const manifest = extractImageManifest(html, catalog.products);
  manifest.collectedAt = new Date().toISOString();
  manifest.pageSha256 = createHash('sha256').update(html).digest('hex');
  await mkdir(new URL('nutrition/images/', root), { recursive: true });
  for (const item of manifest.images) {
    const response = await fetch(item.sourceUrl, {
      headers: { Referer: SOURCE_URL, 'User-Agent': 'Mozilla/5.0' },
      signal: AbortSignal.timeout(30000),
    });
    if (!response.ok || new URL(response.url).hostname !== new URL(IMAGE_BASE).hostname) throw Error(`Official image HTTP ${response.status}: ${item.productId}`);
    const bytes = Buffer.from(await response.arrayBuffer());
    if (bytes.length > 5_000_000 || !bytes.subarray(0, 8).equals(Buffer.from([137,80,78,71,13,10,26,10]))) throw Error(`Invalid PNG: ${item.productId}`);
    item.sha256 = createHash('sha256').update(bytes).digest('hex');
    await writeFile(new URL(item.assetPath, root), bytes);
  }
  await writeFile(new URL('nutrition/mcdonalds-images.json', root), JSON.stringify(manifest, null, 2) + '\n');
  console.log(`Downloaded ${manifest.images.length} official images; ${manifest.missing.length} missing`);
}
