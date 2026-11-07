"use strict";
const assert = require("node:assert/strict");
const fs = require("node:fs");
const path = require("node:path");
// نستخرج الدوال النقية من payments.js دون تشغيل الواجهة
const src = fs.readFileSync(path.join(__dirname, "..", "payments.js"), "utf8");
const a = src.indexOf("const money"), b = src.indexOf("const statusBadge");
const { toCents, ibanValid, money } = new Function(src.slice(a, b) + ";return {toCents,ibanValid,money};")();
let n = 0; const t = (name, fn) => { fn(); n++; console.log("ok -", name); };

t("toCents أرقام صحيحة وعشرية وفاصلة", () => { assert.equal(toCents("15"), 1500); assert.equal(toCents("4.99"), 499); assert.equal(toCents("0,5"), 50); });
t("toCents يرفض الصفر والسالب والنص", () => { assert.equal(toCents("0"), null); assert.equal(toCents("-3"), null); assert.equal(toCents("abc"), null); assert.equal(toCents(""), null); });
t("IBAN صحيح (أمثلة عامة)", () => {
  assert.equal(ibanValid("GB82 WEST 1234 5698 7654 32"), true);
  assert.equal(ibanValid("DE89370400440532013000"), true);
  assert.equal(ibanValid("JO94CBJO0010000000000131000302"), true);
});
t("IBAN خاطئ", () => {
  assert.equal(ibanValid("GB82 WEST 1234 5698 7654 33"), false);
  assert.equal(ibanValid("JO94"), false); assert.equal(ibanValid(""), false); assert.equal(ibanValid(null), false);
});
t("IBAN يتجاهل المسافات والحالة", () => assert.equal(ibanValid("gb82 west 1234 5698 7654 32"), true));
t("money", () => assert.equal(money(1500, "USD"), "15.00 USD"));
console.log(`\n${n} اختبارات نجحت`);
