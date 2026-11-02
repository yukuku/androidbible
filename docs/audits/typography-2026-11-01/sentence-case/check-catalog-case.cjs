const fs=require('fs'),{chromium}=require('playwright');
(async()=>{const source=fs.readFileSync(process.argv[2],'utf8');const template=source.match(/"""([\s\S]*?)"""\.trimIndent/)[1];const browser=await chromium.launch({headless:true,channel:'chrome'}),page=await browser.newPage();
await page.setContent('<style>.tab,button{text-transform:uppercase}</style><div class="tab">PILIHAN</div><div class="tab">CARI ID</div><button class="upload"><span>UPLOAD JSON</span><i>↑</i></button><div class="book_name">BIMK</div>');
await page.evaluate(()=>{window.presses=0;document.querySelector('button').onclick=()=>window.presses++});
await page.evaluate(template.replace('$encoded',JSON.stringify('*{text-transform:none!important}')).replace('$locale',JSON.stringify('id')));
const r=await page.evaluate(()=>({tabs:[...document.querySelectorAll('.tab')].map(e=>e.textContent),button:document.querySelector('button').textContent,book:document.querySelector('.book_name').textContent,transform:getComputedStyle(document.querySelector('.tab')).textTransform,icon:!!document.querySelector('button i')}));await page.locator('button').click();r.handler=await page.evaluate(()=>window.presses);
if(JSON.stringify(r.tabs)!==JSON.stringify(['Pilihan','Cari ID'])||r.book!=='BIMK'||!r.button.includes('Upload JSON')||r.transform!=='none'||!r.icon||r.handler!==1)throw Error(JSON.stringify(r));
console.log(JSON.stringify(r));await browser.close()})().catch(e=>{console.error(e);process.exit(1)});
