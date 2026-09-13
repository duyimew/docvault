// Generates docs/guides/HUONG_DAN_SU_DUNG_WEB.pdf from the markdown guide.
// Uses `marked` for MD->HTML and Playwright Chromium for HTML->PDF.
// Local images (images/web/*.png) are inlined as base64 so the PDF is self-contained.
// Usage: node scripts/md-to-pdf.mjs

import { fileURLToPath } from 'node:url';
import { dirname, join } from 'node:path';
import { readFile, writeFile, readdir } from 'node:fs/promises';
import { createRequire } from 'node:module';

const __dirname = dirname(fileURLToPath(import.meta.url));
const repoRoot = join(__dirname, '..');
const require = createRequire(join(repoRoot, 'package.json'));

const { marked } = require(
  join(repoRoot, 'node_modules/.pnpm/marked@15.0.12/node_modules/marked/lib/marked.cjs'),
);
const { chromium } = require(
  join(repoRoot, 'node_modules/.pnpm/playwright@1.60.0/node_modules/playwright/index.js'),
);

const MD = join(repoRoot, 'docs/guides/HUONG_DAN_SU_DUNG_WEB.md');
const OUT = join(repoRoot, 'docs/guides/HUONG_DAN_SU_DUNG_WEB.pdf');
const IMG_DIR = join(repoRoot, 'docs/images/web');

async function inlineImages(md) {
  const files = await readdir(IMG_DIR).catch(() => []);
  let out = md;
  for (const f of files) {
    if (!f.endsWith('.png')) continue;
    const buf = await readFile(join(IMG_DIR, f));
    const dataUri = `data:image/png;base64,${buf.toString('base64')}`;
    out = out.split(`images/web/${f}`).join(dataUri);
  }
  return out;
}

const CSS = `
  body { font-family: 'Segoe UI', Arial, sans-serif; line-height: 1.6; color: #1a1a1a; max-width: 880px; margin: 0 auto; padding: 24px; }
  h1 { font-size: 26px; border-bottom: 3px solid #2563eb; padding-bottom: 8px; }
  h2 { font-size: 21px; margin-top: 28px; border-bottom: 1px solid #ddd; padding-bottom: 4px; }
  h3 { font-size: 17px; margin-top: 20px; color: #1e40af; }
  table { border-collapse: collapse; width: 100%; margin: 12px 0; font-size: 14px; }
  th, td { border: 1px solid #cbd5e1; padding: 6px 10px; text-align: left; }
  th { background: #f1f5f9; }
  code { background: #f1f5f9; padding: 1px 5px; border-radius: 4px; font-size: 13px; }
  pre { background: #f8fafc; border: 1px solid #e2e8f0; border-radius: 6px; padding: 12px; overflow-x: auto; }
  pre code { background: none; padding: 0; }
  img { max-width: 100%; height: auto; border: 1px solid #e2e8f0; border-radius: 8px; margin: 12px 0; box-shadow: 0 2px 8px rgba(0,0,0,0.08); }
  blockquote { border-left: 4px solid #2563eb; margin: 12px 0; padding: 4px 16px; background: #eff6ff; color: #334155; }
  hr { border: none; border-top: 1px solid #e2e8f0; margin: 24px 0; }
`;

async function main() {
  const raw = await readFile(MD, 'utf8');
  const withImages = await inlineImages(raw);
  const bodyHtml = marked.parse(withImages);
  const html = `<!DOCTYPE html><html lang="vi"><head><meta charset="utf-8"><style>${CSS}</style></head><body>${bodyHtml}</body></html>`;

  const browser = await chromium.launch();
  const page = await browser.newPage();
  await page.setContent(html, { waitUntil: 'networkidle' });
  await page.pdf({
    path: OUT,
    format: 'A4',
    printBackground: true,
    margin: { top: '18mm', bottom: '18mm', left: '14mm', right: '14mm' },
  });
  await browser.close();
  console.log(`PDF generated -> ${OUT}`);
}

main().catch((e) => {
  console.error(e);
  process.exit(1);
});
