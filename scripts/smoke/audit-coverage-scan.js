// 审计覆盖扫描：列出没有 @Auditable 的写端点（POST/PUT/PATCH/DELETE）
//
// 口径（docs/tech/security-design.md §9 第 8 项 + ISS-114/ISS-123）：写端点一律留痕。
// 豁免必须写明理由，否则视为缺口；豁免之外的缺口会让 --strict 退出码为 1。
const EXEMPT = [
  // 导入预览只做解析与校验、不落库，逐次点击都记一条会把审计表冲满，反而看不清真正的批量写入
  ['ImportController.java', ' POST /preview  preview', 'POST 但不改状态：解析文件返回试算结果'],
  // 登录/刷新的留痕在 LoginSecurityService.recordAuthAttempt：失败路径必须在控制器 return 之前落库，
  // 且登录失败锁定统计（AUTH_FAILURE_ACTIONS）依赖同一份记录，注解切面覆盖不到这个口径
  ['AuthController.java', ' POST /login  login', '改由 LoginSecurityService 记录（含失败锁定）'],
  ['AuthController.java', ' POST /refresh  refreshToken', '同上'],
];
const fs = require('fs');
const path = require('path');
// 默认根目录必须相对本脚本所在仓库解析，不能相对 cwd：否则从 scripts/smoke 里跑就 ENOENT 假红。
const DEFAULT_ROOT = path.resolve(__dirname,
  '../../backend/src/main/java/site/hexaarch/ecommerce/logistics/interfaces/controller');
const root = process.argv.slice(2).find(a => !a.startsWith('--')) || DEFAULT_ROOT;
const VERB = { Post: 'POST', Put: 'PUT', Delete: 'DELETE', Patch: 'PATCH' };

function walk(dir) {
  return fs.readdirSync(dir, { withFileTypes: true })
    .flatMap(e => e.isDirectory() ? walk(path.join(dir, e.name)) : [path.join(dir, e.name)]);
}

const missing = [];
const exempted = [];
const covered = [];
for (const file of walk(root)) {
  if (!file.endsWith('.java')) continue;
  const lines = fs.readFileSync(file, 'utf8').split('\n');
  const base = path.basename(file);
  const isSig = l => /^\s*(public|private|protected)\s/.test(l);
  for (let i = 0; i < lines.length; i++) {
    const m = lines[i].match(/^\s*@(Get|Post|Put|Delete|Patch)Mapping(?:\((.*)\))?\s*$/);
    if (!m || m[1] === 'Get') continue;
    const mappingPath = (m[2] || '').replace(/"/g, '');
    // 注解块 = 上一个方法体结束（空行或 }）到方法签名之间的所有行；@Auditable 可写在 @*Mapping 前或后
    let start = i;
    while (start > 0 && lines[start - 1].trim() !== '' && !/^\}/.test(lines[start - 1].trim())) start--;
    let end = i;
    while (end < lines.length - 1 && !isSig(lines[end + 1])) end++;
    const block = lines.slice(start, end + 1).join('\n');
    const audited = /@Auditable/.test(block);
    const nameMatch = lines.slice(i, end + 2).join('\n').match(/(?:public|private|protected)\s+\S+(?:<[^>]*>)?\s+(\w+)\s*\(/);
    const name = nameMatch ? nameMatch[1] : '?';
    const line = `${base}  ${VERB[m[1]]} ${mappingPath || '""'}  ${name}`;
    if (audited) { covered.push(line); continue; }
    const ex = EXEMPT.find(([f, l]) => line.includes(f) && line.includes(l));
    if (ex) exempted.push(`${line}  ← ${ex[2]}`);
    else missing.push(line);
  }
}
const total = covered.length + missing.length + exempted.length;
console.log(`写端点留痕覆盖：${covered.length}/${total}，豁免 ${exempted.length}，缺口 ${missing.length}。`);
if (process.argv.includes('--list-exempt')) {
  console.log('\n豁免清单（含理由）：');
  exempted.forEach(c => console.log('  ⏸ ' + c));
}
console.log(`\n未标记 @Auditable 的写端点 ${missing.length} 个：`);
missing.forEach(c => console.log('  ❌ ' + c));
if (process.argv.includes('--list-covered')) {
  console.log('\n已标记：');
  covered.forEach(c => console.log('  ✅ ' + c));
}
process.exit(missing.length > 0 && process.argv.includes('--strict') ? 1 : 0);
