/**
 * Renderiza uma animacao de canvas (o HTML que o dono mandou) em quadros PNG.
 *
 *   node arte/render_canvas.js <entrada.html> <pasta-saida> [--fps 24] [--segundos 7.5]
 *
 * - roda o <script> da pagina num canvas 1280x720 (troca W/H e o tamanho do canvas)
 * - NAO toca no texto: chama draw(t, "", false) — a placa e as letras ficam para
 *   o Java do mod desenhar (a mensagem e digitada pelo admin)
 * - salva NNNN.png com TRANSPARENCIA (o canvas e um veu; o fundo vem depois)
 */
const fs = require('fs');
const path = require('path');
const puppeteer = require('puppeteer');

const LARG = 1280, ALT = 720;

function args() {
  const a = process.argv.slice(2);
  const o = { entrada: a[0], saida: a[1], fps: 24, segundos: 7.5 };
  for (let i = 2; i < a.length; i++) {
    if (a[i] === '--fps') o.fps = parseFloat(a[++i]);
    else if (a[i] === '--segundos') o.segundos = parseFloat(a[++i]);
  }
  return o;
}

function scriptDaPagina(html) {
  const m = html.match(/<script>([\s\S]*?)<\/script>/);
  if (!m) throw new Error('nao achei <script> na pagina');
  let js = m[1];

  // 1) render em 1280x720 com o MESMO fator k (k = min(W,H)/240)
  // escala fiel: k e 2.25 em 960x540; em 1280x720 (mesmo 16:9) vira 3.0
  js = js.replace('W=960,H=540', `W=${LARG},H=${ALT}`);
  js = js.replace('const k=2.25', 'const k=3');

  // 2) sem placa (o Java desenha a placa do tamanho da mensagem)
  js = js.replace('const prog=eo(cl((t-1.6)/1.1));if(prog>.001){',
                  'const prog=eo(cl((t-1.6)/1.1));if(!window.__SEM_PLACA&&prog>.001){');
  // 3) sem o brilho radial da placa (o Java faz o dele)
  js = js.replace('const pg=eo(cl((t-1.6)/1));if(pg>.01){',
                  'const pg=eo(cl((t-1.6)/1));if(!window.__SEM_PLACA&&pg>.01){');

  // 4) troca o loop por um renderizador sob demanda
  js = js.replace(/let t0=performance\.now\(\);[\s\S]*$/,
    `window.__DUR = DUR;
window.__render = function (t, msg, red) { scene(); draw(t, msg, red); };`);

  if (!js.includes('window.__render')) throw new Error('nao consegui montar o __render');
  return js;
}

(async () => {
  const o = args();
  if (!o.entrada || !o.saida) {
    console.error('uso: node arte/render_canvas.js <entrada.html> <pasta-saida> [--fps 24] [--segundos 7.5]');
    process.exit(1);
  }
  const js = scriptDaPagina(fs.readFileSync(o.entrada, 'utf8'));
  fs.mkdirSync(o.saida, { recursive: true });
  for (const f of fs.readdirSync(o.saida)) if (f.endsWith('.png')) fs.unlinkSync(path.join(o.saida, f));

  const browser = await puppeteer.launch({
    args: ['--no-sandbox', '--disable-dev-shm-usage', '--force-device-scale-factor=1'],
  });
  const page = await browser.newPage();
  await page.setViewport({ width: LARG, height: ALT, deviceScaleFactor: 1 });
  await page.setContent(
    `<!DOCTYPE html><html><body style="margin:0;background:transparent">` +
    `<canvas id="c" width="${LARG}" height="${ALT}"></canvas><script>${js}</script></body></html>`,
    { waitUntil: 'load' });
  await page.evaluate(() => { window.__SEM_PLACA = true; });

  const dur = await page.evaluate(() => window.__DUR);
  const seg = o.segundos || dur;
  const n = Math.round(seg * o.fps);
  console.log(`duracao ${dur}s -> ${n} quadros a ${o.fps} fps (${LARG}x${ALT})`);

  for (let i = 0; i < n; i++) {
    const t = i / o.fps;
    const dataUrl = await page.evaluate(
      (tt) => { window.__render(tt, ' ', false); return document.getElementById('c').toDataURL('image/png'); },
      t);
    fs.writeFileSync(path.join(o.saida, String(i).padStart(4, '0') + '.png'),
      Buffer.from(dataUrl.split(',')[1], 'base64'));
    if (i % 24 === 0) process.stdout.write(`  ${i}/${n}\n`);
  }
  await browser.close();
  console.log('pronto:', o.saida);
})();
