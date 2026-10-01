const path = require('node:path');
const { pathToFileURL } = require('node:url');
const { chromium } = require('playwright');

async function render() {
  const browser = await chromium.launch();
  try {
    const page = await browser.newPage();
    const source = path.join(__dirname, 'canton-credentials-did-presentation-en.html');
    const output = path.join(__dirname, 'canton-credentials-did-presentation-en.pdf');
    await page.goto(pathToFileURL(source).href, { waitUntil: 'load' });
    await page.pdf({
      path: output,
      preferCSSPageSize: true,
      printBackground: true,
      margin: { top: '0', right: '0', bottom: '0', left: '0' },
    });
  } finally {
    await browser.close();
  }
}

render().catch(error => {
  console.error(error);
  process.exitCode = 1;
});
