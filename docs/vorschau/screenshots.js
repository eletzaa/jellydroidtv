// Rendert die Ansichten aus index.html als JPG (1920 x 1080) nach docs/bilder/.
// Aufruf: node docs/vorschau/screenshots.js   (braucht Playwright mit Chromium)
const path = require("path"), fs = require("fs");
const { chromium } = require("playwright");
const ids = ["entdecken", "startseite", "anfragen", "sidebar", "homeassistant", "klingel"];
(async () => {
  const ziel = path.join(__dirname, "..", "bilder");
  fs.mkdirSync(ziel, { recursive: true });
  const b = await chromium.launch();
  const pg = await b.newPage({ viewport: { width: 960, height: 540 }, deviceScaleFactor: 2 });
  for (const id of ids) {
    await pg.goto("file://" + path.join(__dirname, "index.html") + "?shot=" + id);
    await pg.evaluate(() => document.fonts.ready);
    await pg.waitForTimeout(300);
    await pg.locator(".tv").screenshot({ path: path.join(ziel, id + ".jpg"), type: "jpeg", quality: 90 });
    console.log(id);
  }
  await b.close();
})();
