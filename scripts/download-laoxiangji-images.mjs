import { readFile, writeFile, mkdir } from 'node:fs/promises';
import { createHash } from 'node:crypto';

const root = new URL('../app/src/main/assets/', import.meta.url);
const catalog = JSON.parse(await readFile(new URL('nutrition/laoxiangji-cn.json', root), 'utf8'));
const website = 'https://www.lxjchina.com.cn/';
const page = await fetch(website, { signal: AbortSignal.timeout(30000) });
if (!page.ok) throw Error(`Official site returned ${page.status}`);
const html = await page.text();
const official = [...html.matchAll(/<img\s+src="(upload\/[^"]+\.png)"\s+alt="([^"]+)"/g)];
const images = [];
await mkdir(new URL('nutrition/laoxiangji-images/', root), { recursive: true });
for (const product of catalog.products) {
  if (catalog.products.filter(p => p.name === product.name).length !== 1) continue;
  const match = official.find(m => m[2] === product.name);
  if (!match) continue;
  const sourceUrl = new URL(match[1], website);
  if (sourceUrl.hostname !== 'www.lxjchina.com.cn') throw Error('Unexpected image host');
  const response = await fetch(sourceUrl, { headers: { Referer: website, 'User-Agent': 'Mozilla/5.0' }, signal: AbortSignal.timeout(30000) });
  if (!response.ok || new URL(response.url).hostname !== sourceUrl.hostname) throw Error(`Image unavailable: ${product.name}`);
  const bytes = Buffer.from(await response.arrayBuffer());
  if (bytes.length > 5_000_000 || !bytes.subarray(0,8).equals(Buffer.from([137,80,78,71,13,10,26,10]))) throw Error(`Invalid PNG: ${product.name}`);
  const assetPath = `nutrition/laoxiangji-images/${product.id}.png`;
  await writeFile(new URL(assetPath, root), bytes);
  images.push({ productId: product.id, productName: product.name, sourceUrl: sourceUrl.href, assetPath, sha256: createHash('sha256').update(bytes).digest('hex') });
}
await writeFile(new URL('nutrition/laoxiangji-images.json', root), JSON.stringify({ sourceUrl: website, collectedAt: new Date().toISOString(), images }, null, 2) + '\n');
console.log(`Downloaded ${images.length} exact-name official images`);
