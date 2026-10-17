// Letters from Space - browser smoke test of the dashboard (developer tool, not part of the lab).
// Start the dashboard first (./run_dashboard.sh 18765), then:
//   PLAYWRIGHT_CORE=/path/to/node_modules/playwright-core node tools/ui_smoke_test.js http://127.0.0.1:18765/ /tmp/shots
// It needs playwright-core and a Chromium it can launch (npx playwright install chromium). It clicks
// through the four steps (train D, the letter the page opens on, scan, untick a candidate, train and
// scan all letters, simulate filling in, add and remove a sample), checks the phone width, saves
// screenshots in the output folder and lists any console error or failed request. It changes the
// server's state (trained detectors), so do not run it against a class in progress.
const { chromium } = require(process.env.PLAYWRIGHT_CORE || 'playwright-core');
const fs = require('fs');
const BASE = process.argv[2] || 'http://127.0.0.1:8080/';
const OUT = process.argv[3] || require('os').tmpdir() + '/lettersfromspace-shots';
fs.mkdirSync(OUT, { recursive: true });

(async () => {
  const browser = await chromium.launch({ headless: true });
  const context = await browser.newContext({ viewport: { width: 1280, height: 900 }, deviceScaleFactor: 1 });
  const page = await context.newPage();
  const problems = [];
  page.on('console', (m) => { if (m.type() === 'error' || m.type() === 'warning') problems.push('console ' + m.type() + ': ' + m.text()); });
  page.on('pageerror', (e) => problems.push('pageerror: ' + e.message));
  page.on('requestfailed', (r) => problems.push('requestfailed: ' + r.url()));
  page.on('response', (r) => { if (r.status() >= 400) problems.push('HTTP ' + r.status() + ' ' + r.url()); });

  await page.goto(BASE);
  await page.waitForSelector('.letter-tile');
  console.log('engine:', (await page.textContent('#chip-server')).trim());
  await page.waitForSelector('#pos-frames .tile');
  console.log('letters:', await page.locator('.letter-tile').count(), 'positives:', await page.locator('#pos-frames .tile').count(),
    'negatives:', await page.locator('#neg-letter-frames .tile').count(), '+', await page.locator('#neg-sky-frames .tile').count());
  await page.screenshot({ path: OUT + '/01_initial.png', fullPage: true });

  // train the letter the page opens on (D)
  await page.click('#btn-train');
  await page.waitForSelector('#train-status button');          // trained: the replay plays, the figures wait for its end
  await page.waitForTimeout(1500);
  console.log('replay:', await page.textContent('#train-status'), '|', await page.textContent('#anim-readout'),
    '| figures held back:', await page.locator('#train-result.replaying').count() === 1);
  await page.click('#train-status button');                     // "show the result now"
  await page.waitForTimeout(700);
  console.log('train status:', await page.textContent('#train-status'));
  console.log('stats:', (await page.locator('#train-stats .stat').allInnerTexts()).map((t) => t.replace(/\n/g, ' | ')));
  // hover the curve
  await page.locator('#curve').scrollIntoViewIfNeeded();
  const curve = await page.locator('#curve').boundingBox();
  await page.mouse.move(curve.x + curve.width * 0.3, curve.y + curve.height * 0.5);
  await page.waitForTimeout(150);
  console.log('curve tooltip:', (await page.textContent('#tooltip')).trim(), '| hidden:', await page.locator('#tooltip').evaluate((e) => e.classList.contains('hidden')));
  await page.locator('#network').screenshot({ path: OUT + '/02_network.png' });
  // hover a weight cell
  await page.locator('#weights canvas').first().scrollIntoViewIfNeeded();
  const unit = await page.locator('#weights canvas').first().boundingBox();
  await page.mouse.move(unit.x + 20, unit.y + 30);
  await page.waitForTimeout(100);
  console.log('weight tooltip:', (await page.textContent('#tooltip')).trim());
  await page.locator('#weights canvas').nth(2).click();
  console.log('unit table rows:', await page.locator('#unit-table tbody tr').count());

  // test a frame: the editor starts with the master glyph
  await page.waitForTimeout(400);
  console.log('test score:', await page.textContent('#test-score'), '|', await page.textContent('#test-verdict'));
  await page.click('#btn-test-video');
  await page.waitForTimeout(500);
  console.log('random frame score:', await page.textContent('#test-score'));

  // scan
  await page.click('#btn-scan');
  await page.waitForSelector('#scan-result:not(.hidden) .hit');
  await page.waitForTimeout(300);
  await page.locator('#scan').screenshot({ path: OUT + '/03a_scan_first.png' });
  console.log('scan status:', await page.textContent('#scan-status'), '| cards:', await page.locator('.hit').count());
  // untick the last candidate
  await page.locator('.hit input[type=checkbox]').last().uncheck();
  await page.waitForTimeout(400);
  console.log('after untick:', await page.textContent('#scan-status'));
  await page.locator('#scan').screenshot({ path: OUT + '/03_scan.png' });

  // threshold slider -> rescan
  await page.locator('#f-threshold').fill('0.6');
  await page.waitForTimeout(900);
  console.log('threshold 0.6:', await page.textContent('#scan-status'));
  await page.click('#btn-defaults-set');          // back to the default settings (threshold included)
  await page.waitForTimeout(900);

  // train all
  const t0 = Date.now();
  await page.click('#btn-all');
  await page.waitForFunction(() => /Trained and scanned|Stopped/.test(document.getElementById('all-status').textContent), null, { timeout: 180000 });
  await page.waitForTimeout(800);
  console.log('train all:', await page.textContent('#all-status'), '(' + (Date.now() - t0) + ' ms wall)');
  console.log('message:', (await page.textContent('#message-text')).trim());
  console.log('note:', await page.textContent('#message-note'));
  // inspect a character
  await page.locator('#message-text .c').nth(3).click();
  await page.waitForSelector('#inspect .inspect');
  await page.locator('#message').screenshot({ path: OUT + '/04_message.png' });
  await page.locator('#data').screenshot({ path: OUT + '/04b_data_scored.png' });

  // simulate filling in (letter B)
  await page.locator('.letter-tile[data-letter="B"]').click();
  await page.waitForTimeout(500);
  await page.click('#btn-simulate');
  await page.waitForTimeout(700);
  await page.locator('#data').screenshot({ path: OUT + '/05_simulate_mid.png' });
  await page.waitForFunction(() => document.getElementById('btn-simulate').textContent === 'Simulate filling in', null, { timeout: 30000 });
  console.log('datfile lines:', (await page.textContent('#datfile')).split('\n').length);

  // draw a sample and add it
  const cells = page.locator('#draw-editor button');
  for (const i of [3, 4, 10, 13, 17, 22, 24, 25, 26, 27, 28, 29, 30, 31, 32, 39]) await cells.nth(i).click();
  await page.click('#btn-add-sample');
  await page.waitForTimeout(600);
  console.log('user samples:', await page.textContent('#user-sample-count'), '| positives now:', await page.locator('#pos-frames .tile').count());
  await page.click('#btn-clear-samples');
  await page.waitForTimeout(400);

  // the whole page
  await page.locator('.letter-tile[data-letter="A"]').click();
  await page.waitForTimeout(700);
  await page.screenshot({ path: OUT + '/06_full_page.png', fullPage: true });

  // phone width: no horizontal page scroll
  await page.setViewportSize({ width: 390, height: 844 });
  await page.waitForTimeout(500);
  const overflow = await page.evaluate(() => [document.documentElement.scrollWidth, window.innerWidth]);
  console.log('phone width scrollWidth/innerWidth:', overflow);
  await page.screenshot({ path: OUT + '/07_phone.png', fullPage: false });

  console.log('screenshots in ' + OUT);
  console.log(problems.length ? 'PROBLEMS:\n  ' + problems.join('\n  ') : 'no console errors, page errors or failed requests');
  await browser.close();
  if (problems.length) process.exitCode = 1;
})().catch((e) => { console.error('TEST FAILED:', e); process.exit(1); });
