"use strict";
const assert = require("node:assert/strict");
const C = require("../slice-core.js");

function alphaFrom(rows) {
  const h = rows.length, w = rows[0].length, a = new Uint8Array(w * h);
  rows.forEach((r, y) => [...r].forEach((ch, x) => { a[y * w + x] = ch === "#" ? 255 : 0; }));
  return { a, w, h };
}
let n = 0;
const t = (name, fn) => { fn(); n++; console.log("ok -", name); };

t("grid 2x2 قسمة متساوية", () => {
  const r = C.gridRects(100, 100, 2, 2, 0, 0);
  assert.equal(r.length, 4);
  assert.deepEqual(r[3], { x: 50, y: 50, w: 50, h: 50 });
});
t("grid مع هامش وفراغ", () => {
  const r = C.gridRects(100, 60, 2, 3, 5, 2);
  assert.equal(r.length, 6);
  assert.deepEqual(r[0], { x: 5, y: 5, w: 28, h: 24 });
  assert.deepEqual(r[1], { x: 35, y: 5, w: 28, h: 24 });
  assert.deepEqual(r[3], { x: 5, y: 31, w: 28, h: 24 });
});
t("grid مستحيل يرجع فارغًا", () => assert.deepEqual(C.gridRects(10, 10, 20, 20, 0, 0), []));
t("grid لا يخرج عن حدود الصورة", () => {
  for (const r of C.gridRects(103, 77, 4, 5, 3, 1)) {
    assert.ok(r.x >= 0 && r.y >= 0 && r.x + r.w <= 103 && r.y + r.h <= 77);
  }
});

const sample = alphaFrom([
  "..........",
  ".##.......",
  ".##...#...",
  "......##..",
  "....#.....",
  "....#.....",
  "..........",
]);
t("كشف جزأين منفصلين مع اتصال قطري", () => {
  const boxes = C.findComponents(sample.a, sample.w, sample.h, 8);
  assert.equal(boxes.length, 3); // المربع 2x2 + (6,2)-(7,3) متصلان قطريًا + العمود (4,4)-(4,5)
  const big = boxes.find((b) => b.area === 4);
  assert.deepEqual({ x: big.x, y: big.y, w: big.w, h: big.h }, { x: 1, y: 1, w: 2, h: 2 });
  const diag = boxes.find((b) => b.x === 6);
  assert.deepEqual({ w: diag.w, h: diag.h, area: diag.area }, { w: 2, h: 2, area: 3 });
});
t("العتبة تتجاهل الشفافية المنخفضة", () => {
  const a = new Uint8Array(9).fill(5); a[4] = 200;
  assert.equal(C.findComponents(a, 3, 3, 8).length, 1);
  assert.equal(C.findComponents(new Uint8Array(9).fill(5), 3, 3, 8).length, 0);
});
t("دمج المتقاربة حسب المسافة", () => {
  const b = [{ x: 0, y: 0, w: 10, h: 10, area: 50 }, { x: 13, y: 0, w: 10, h: 10, area: 40 }];
  assert.equal(C.mergeBoxes(b, 2).length, 2);
  const m = C.mergeBoxes(b, 3);
  assert.equal(m.length, 1);
  assert.deepEqual(m[0], { x: 0, y: 0, w: 23, h: 10, area: 90 });
});
t("دمج متسلسل (سلسلة من ثلاثة)", () => {
  const b = [0, 12, 24].map((x) => ({ x, y: 0, w: 10, h: 10, area: 1 }));
  assert.equal(C.mergeBoxes(b, 2).length, 1);
});
t("trimRect يقص الأطراف الشفافة", () => {
  const r = C.trimRect(sample.a, sample.w, sample.h, { x: 0, y: 0, w: 5, h: 4 }, 8);
  assert.deepEqual(r, { x: 1, y: 1, w: 2, h: 2 });
  assert.equal(C.trimRect(sample.a, sample.w, sample.h, { x: 8, y: 0, w: 2, h: 2 }, 8), null);
});
t("padRect يتقيد بحدود الصورة", () => {
  assert.deepEqual(C.padRect({ x: 1, y: 1, w: 2, h: 2 }, 5, 10, 7), { x: 0, y: 0, w: 8, h: 7 });
});
t("clampRect", () => {
  assert.deepEqual(C.clampRect({ x: -5, y: 3.4, w: 500, h: 2 }, 100, 50), { x: 0, y: 3, w: 100, h: 2 });
});
t("ترتيب القراءة", () => {
  const r = C.sortReadingOrder([{ x: 50, y: 2, w: 10, h: 10 }, { x: 0, y: 40, w: 10, h: 10 }, { x: 0, y: 0, w: 10, h: 10 }]);
  assert.deepEqual(r.map((q) => [q.x, q.y]), [[0, 0], [50, 2], [0, 40]]);
});
t("countOpaque يعدّ البكسلات المرئية فقط", () => {
  assert.equal(C.countOpaque(sample.a, sample.w, sample.h, { x: 0, y: 0, w: 5, h: 4 }, 8), 4);
  assert.equal(C.countOpaque(sample.a, sample.w, sample.h, { x: 8, y: 0, w: 2, h: 2 }, 8), 0);
});
t("filterEmpty يحذف الخلايا الشفافة ويبقي الباقي", () => {
  const grid = C.gridRects(10, 6, 2, 2, 0, 0); // أربع خلايا 5x3 فيها 4 و1 و2 و2 بكسل مرئي
  assert.deepEqual(grid.map((g) => C.countOpaque(sample.a, sample.w, sample.h, g, 8)), [4, 1, 2, 2]);
  const r = C.filterEmpty(sample.a, sample.w, sample.h, grid, 8, 3);
  assert.equal(r.removed, 3);
  assert.deepEqual(r.kept, [grid[0]]);
});
t("filterEmpty: كل الخلايا الشفافة تُحذف", () => {
  const blank = new Uint8Array(100);
  const r = C.filterEmpty(blank, 10, 10, C.gridRects(10, 10, 2, 2, 0, 0), 8, 1);
  assert.equal(r.kept.length, 0); assert.equal(r.removed, 4);
});
t("filterEmpty يتجاهل الشوائب بحسب minPixels", () => {
  const a = new Uint8Array(100); a[0] = 255; // بكسل واحد شارد في الخلية الأولى
  const grid = C.gridRects(10, 10, 2, 2, 0, 0);
  assert.equal(C.filterEmpty(a, 10, 10, grid, 8, 1).kept.length, 1);
  assert.equal(C.filterEmpty(a, 10, 10, grid, 8, 5).kept.length, 0);
});
console.log(`\n${n} اختبارات نجحت`);
