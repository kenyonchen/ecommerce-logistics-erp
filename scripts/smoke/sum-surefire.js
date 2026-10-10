const fs = require('fs');
const dir = process.argv[2] || 'target/surefire-reports';
const only = process.argv[3] || '';
const attr = (s, k) => { const m = s.match(new RegExp(k + '="([0-9]+)"')); return m ? Number(m[1]) : 0; };
let files = fs.readdirSync(dir).filter((f) => f.endsWith('.xml'));
if (only) files = files.filter((f) => f.includes(only));
let t = 0, f2 = 0, e = 0, sk = 0;
const bad = [];
for (const f of files) {
  const head = fs.readFileSync(dir + '/' + f, 'utf8').split('\n').find((l) => l.includes('<testsuite')) || '';
  const tests = attr(head, 'tests'), failures = attr(head, 'failures'), errors = attr(head, 'errors'), skipped = attr(head, 'skipped');
  t += tests; f2 += failures; e += errors; sk += skipped;
  if (failures + errors > 0) bad.push(`${f}: failures=${failures} errors=${errors}`);
}
console.log(`classes=${files.length} tests=${t} failures=${f2} errors=${e} skipped=${sk}`);
bad.forEach((b) => console.log('  FAIL ' + b));
