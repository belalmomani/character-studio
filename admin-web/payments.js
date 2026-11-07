"use strict";
// تبويب «المدفوعات»: الخطط، باقات النقاط، التحويل البنكي، مراجعة التحويلات، آخر الطلبات. يعتمد على دوال admin.js.

const PAY_STATUS = { created: "تم الإنشاء", pending: "بانتظار", paid: "مدفوع", failed: "مرفوض/فشل", canceled: "ملغى", expired: "منتهي", refunded: "مسترد", needs_review: "يحتاج مراجعة" };
const money = (cents, cur) => (cents / 100).toFixed(2) + " " + cur;
function toCents(text) {
  const v = Math.round(parseFloat(String(text).replace(",", ".")) * 100);
  return Number.isFinite(v) && v > 0 ? v : null;
}
/** MOD-97 مطابق لدالة iban_valid في قاعدة البيانات. */
function ibanValid(p) {
  const s0 = String(p || "").replace(/\s/g, "").toUpperCase();
  if (!/^[A-Z]{2}[0-9]{2}[A-Z0-9]{11,30}$/.test(s0)) return false;
  const s = s0.slice(4) + s0.slice(0, 4);
  let r = 0;
  for (const ch of s) r = /[0-9]/.test(ch) ? (r * 10 + Number(ch)) % 97 : (r * 100 + (ch.charCodeAt(0) - 55)) % 97;
  return r === 1;
}
const statusBadge = (st) => h("span", { class: "badge " + (st === "paid" ? "published" : st === "pending" || st === "needs_review" ? "pending" : st === "failed" ? "rejected" : "") }, PAY_STATUS[st] || st);

async function paymentsView(notice) {
  loading("المدفوعات");
  const [plans, pkgs, bank, subs, orders] = await Promise.all([
    sb.from("subscription_plans").select("*").order("sort_order"),
    sb.from("point_packages").select("*").order("sort_order"),
    sb.from("bank_transfer_settings").select("*").eq("id", 1).maybeSingle(),
    sb.from("bank_transfer_submissions").select("id,order_id,user_id,note,created_at").eq("status", "awaiting_review").order("created_at"),
    sb.from("payment_orders").select("id,reference,product_type,snapshot,amount_cents,currency,method,status,created_at").order("created_at", { ascending: false }).limit(30),
  ]);
  const failed = [plans, pkgs, bank, subs, orders].find(x => x.error);
  if (failed) return show(h("h1", {}, "المدفوعات"), msg(explain(failed.error), "err"));

  // --- تحويلات بانتظار المراجعة
  const ids = subs.data.map(x => x.order_id);
  const ordMap = {};
  if (ids.length) {
    const o = await sb.from("payment_orders").select("id,reference,snapshot,amount_cents,currency,status").in("id", ids);
    for (const r of (o.data || [])) ordMap[r.id] = r;
  }
  const review = async (orderId, ok) => {
    let reason = null;
    if (ok) { if (!confirm("هل تأكدت من وصول المبلغ فعلًا إلى حسابك البنكي؟ سيُفعَّل الاشتراك/تُضاف النقاط فور الاعتماد.")) return; }
    else { reason = prompt("سبب الرفض (مطلوب):"); if (reason === null) return; }
    const { error } = await sb.rpc("admin_review_bank_transfer", { p_order: orderId, p_approve: ok, p_reason: reason });
    paymentsView(error ? msg(explain(error), "err") : msg(ok ? "اعتُمد التحويل وفُعِّل الطلب." : "رُفض التحويل.", "ok"));
  };
  const transfers = subs.data.length ? subs.data.map(s => {
    const o = ordMap[s.order_id] || {};
    return h("div", { class: "card" }, h("div", { class: "body" },
      h("div", { class: "title" }, `${o.reference || s.order_id.slice(0, 8)} — ${(o.snapshot && o.snapshot.name) || ""}`),
      h("div", { class: "hint" }, `${o.amount_cents ? money(o.amount_cents, o.currency) : ""} • ${new Date(s.created_at).toLocaleString("ar-EG")} • مستخدم ${String(s.user_id).slice(0, 8)}`),
      s.note ? h("div", {}, "ملاحظة المستخدم: " + s.note) : "",
      h("div", { class: "actions" },
        h("button", { class: "small", onclick: () => review(s.order_id, true) }, "اعتماد (وصل المبلغ)"),
        h("button", { class: "small danger", onclick: () => review(s.order_id, false) }, "رفض"))));
  }) : [h("p", { class: "hint" }, "لا توجد تحويلات بانتظار المراجعة.")];

  // --- الخطط
  const planCards = plans.data.map(p => h("div", { class: "card" }, h("div", { class: "body" },
    h("div", { class: "title" }, p.name_ar, " ", h("span", { class: "badge " + (p.published ? "published" : "") }, p.published ? "منشورة" : "غير منشورة")),
    h("div", { class: "hint" }, `${money(p.price_cents, p.currency)} • ${p.duration_days} يومًا` + (p.discount_percent ? ` • خصم ${p.discount_percent}%` : "")
      + ` • ${(p.features || []).length} ميزة`),
    h("div", { class: "actions" }, h("button", { class: "small secondary", onclick: () => planForm(p) }, "تعديل")))));
  const pkgCards = pkgs.data.length ? pkgs.data.map(k => h("div", { class: "card" }, h("div", { class: "body" },
    h("div", { class: "title" }, k.name_ar, " ", h("span", { class: "badge " + (k.active ? "published" : "") }, k.active ? "مفعّلة" : "غير مفعّلة")),
    h("div", { class: "hint" }, `${k.points} نقطة • ${money(k.price_cents, k.currency)}`),
    h("div", { class: "actions" }, h("button", { class: "small secondary", onclick: () => pkgForm(k) }, "تعديل"))))) :
    [h("p", { class: "hint" }, "لا توجد باقات نقاط. أنشئ باقاتك أنت؛ لا توجد أسعار افتراضية.")];

  // --- التحويل البنكي
  const b = bank.data || {};
  const en = h("input", { type: "checkbox", id: "bken", checked: !!b.enabled });
  const bn = h("input", { type: "text", id: "bkn", value: b.beneficiary_name || "" });
  const bb = h("input", { type: "text", id: "bkb", value: b.bank_name || "" });
  const bi = h("input", { type: "text", id: "bki", dir: "ltr", value: b.iban || "", placeholder: "JO.." });
  const bt = h("textarea", { id: "bkt", rows: "3" }); bt.value = b.instructions_ar || "";
  const bout = h("div");
  const bankForm = h("form", { onsubmit: async (ev) => {
    ev.preventDefault();
    const iban = bi.value.replace(/\s/g, "").toUpperCase();
    if (en.checked && (!bn.value.trim() || !bb.value.trim() || !ibanValid(iban))) {
      return bout.replaceChildren(msg("لتفعيل التحويل البنكي أدخل اسم المستفيد والبنك وIBAN صحيحًا (يُتحقق من مجموع الاختبار).", "err"));
    }
    if (iban && !ibanValid(iban) && !confirm("الـ IBAN لا يجتاز التحقق. هل تريد حفظه كما هو دون تفعيل؟")) return;
    const { error } = await sb.from("bank_transfer_settings").update({
      enabled: en.checked, beneficiary_name: bn.value.trim(), bank_name: bb.value.trim(), iban, instructions_ar: bt.value.trim() }).eq("id", 1);
    paymentsView(error ? msg(explain(error), "err") : msg("حُفظت إعدادات التحويل البنكي.", "ok"));
  } },
    h("div", { class: "check" }, en, h("label", { for: "bken", style: "margin:0" }, "تفعيل التحويل البنكي للمستخدمين")),
    ...field("اسم المستفيد", bn), ...field("اسم البنك", bb), ...field("IBAN", bi), ...field("تعليمات التحويل للمستخدم", bt), bout,
    h("div", { class: "actions" }, h("button", { type: "submit" }, "حفظ")));

  // --- الطلبات
  const orderRows = orders.data.length ? orders.data.map(o => h("div", { class: "row" },
    h("dt", {}, `${o.reference} — ${(o.snapshot && o.snapshot.name) || ""}`, h("small", { class: "hint" }, ` ${o.method === "card" ? "بطاقة" : "تحويل"} • ${new Date(o.created_at).toLocaleDateString("ar-EG")}`)),
    h("dd", {}, money(o.amount_cents, o.currency), " ", statusBadge(o.status)))) : [h("p", { class: "hint" }, "لا توجد طلبات بعد.")];

  show(h("h1", {}, "المدفوعات"), notice || "",
    h("div", { class: "panel" }, h("h2", {}, `تحويلات بانتظار المراجعة (${subs.data.length})`), transfers),
    h("div", { class: "panel" }, h("h2", {}, "خطط الاشتراك"), planCards,
      h("div", { class: "actions" }, h("button", { onclick: () => planForm(null) }, "إضافة خطة"))),
    h("div", { class: "panel" }, h("h2", {}, "باقات النقاط"), pkgCards,
      h("div", { class: "actions" }, h("button", { onclick: () => pkgForm(null) }, "إضافة باقة"))),
    h("div", { class: "panel" }, h("h2", {}, "التحويل البنكي (IBAN)"), bankForm),
    h("div", { class: "panel" }, h("h2", {}, "الدفع بالبطاقة"),
      msg("غير مُفعَّل: لم يُختر مزود دفع ولم يُنفَّذ التكامل بعد. لن يظهر للمستخدمين حتى يكتمل (مزود + Webhook موقَّع + اختبار Sandbox).", "warn")),
    h("div", { class: "panel" }, h("h2", {}, "آخر الطلبات"), h("dl", {}, orderRows)));
}

function planForm(p) {
  const out = h("div");
  const name = h("input", { type: "text", id: "pn", required: true, value: p ? p.name_ar : "" });
  const desc = h("textarea", { id: "pd", rows: "2" }); desc.value = p ? p.description_ar : "";
  const price = h("input", { type: "text", id: "pp", inputmode: "decimal", required: true, value: p ? (p.price_cents / 100).toFixed(2) : "" });
  const cur = h("input", { type: "text", id: "pc", maxlength: "3", dir: "ltr", value: p ? p.currency : "USD" });
  const days = h("input", { type: "number", id: "pdays", min: "1", required: true, value: p ? String(p.duration_days) : "30" });
  const disc = h("input", { type: "number", id: "pdisc", min: "0", max: "100", value: p ? String(p.discount_percent) : "0" });
  const feats = h("textarea", { id: "pf", rows: "5", placeholder: "ميزة في كل سطر. اكتب فقط ما هو منفَّذ فعلًا في التطبيق." }); feats.value = p ? (p.features || []).join("\n") : "";
  const terms = h("textarea", { id: "pt", rows: "2" }); terms.value = p ? p.renewal_terms_ar : "";
  const canc = h("input", { type: "checkbox", id: "pcan", checked: p ? !!p.cancellable : false });
  const pub = h("input", { type: "checkbox", id: "ppub", checked: p ? !!p.published : false });
  const sort = h("input", { type: "number", id: "ps", value: p ? String(p.sort_order) : "0" });
  const form = h("form", { class: "panel", onsubmit: async (ev) => {
    ev.preventDefault();
    const cents = toCents(price.value);
    if (!cents) return out.replaceChildren(msg("السعر يجب أن يكون رقمًا أكبر من صفر.", "err"));
    const features = feats.value.split("\n").map(x => x.trim()).filter(Boolean);
    if (pub.checked && !features.length && !confirm("الخطة بلا أي مزايا معلنة وستُعرض للمستخدمين. هل تريد نشرها هكذا؟")) return;
    const row = { name_ar: name.value.trim(), description_ar: desc.value.trim(), price_cents: cents, currency: cur.value.trim().toUpperCase(),
      duration_days: parseInt(days.value, 10), discount_percent: parseInt(disc.value, 10) || 0, features, renewal_terms_ar: terms.value.trim(),
      cancellable: canc.checked, published: pub.checked, sort_order: parseInt(sort.value, 10) || 0 };
    const { error } = p ? await sb.from("subscription_plans").update(row).eq("id", p.id) : await sb.from("subscription_plans").insert(row);
    if (error) return out.replaceChildren(msg(explain(error), "err"));
    paymentsView(msg("حُفظت الخطة. تغيير السعر لا يمس الطلبات القائمة (السعر مجمَّد في كل طلب).", "ok"));
  } }, ...field("اسم الخطة", name), ...field("الوصف", desc), ...field("السعر (بالعملة الأساسية، مثل 15.00)", price), ...field("العملة (3 أحرف)", cur),
    ...field("المدة بالأيام", days), ...field("خصم %", disc), ...field("المزايا (سطر لكل ميزة)", feats), ...field("شروط التجديد", terms),
    h("div", { class: "check" }, canc, h("label", { for: "pcan", style: "margin:0" }, "قابلة للإلغاء")),
    h("div", { class: "check" }, pub, h("label", { for: "ppub", style: "margin:0" }, "منشورة للمستخدمين")),
    ...field("الترتيب", sort), out,
    h("div", { class: "actions" }, h("button", { type: "submit" }, "حفظ"), h("button", { type: "button", class: "secondary", onclick: () => paymentsView() }, "إلغاء")));
  show(h("h1", {}, p ? "تعديل خطة" : "إضافة خطة"), form);
}

function pkgForm(k) {
  const out = h("div");
  const name = h("input", { type: "text", id: "kn", required: true, value: k ? k.name_ar : "" });
  const desc = h("textarea", { id: "kd", rows: "2" }); desc.value = k ? k.description_ar : "";
  const pts = h("input", { type: "number", id: "kp", min: "1", required: true, value: k ? String(k.points) : "" });
  const price = h("input", { type: "text", id: "kpr", inputmode: "decimal", required: true, value: k ? (k.price_cents / 100).toFixed(2) : "" });
  const cur = h("input", { type: "text", id: "kc", maxlength: "3", dir: "ltr", value: k ? k.currency : "USD" });
  const bonus = h("input", { type: "text", id: "kb", value: k ? k.bonus_note_ar : "" });
  const act = h("input", { type: "checkbox", id: "kact", checked: k ? !!k.active : false });
  const sort = h("input", { type: "number", id: "ks", value: k ? String(k.sort_order) : "0" });
  const form = h("form", { class: "panel", onsubmit: async (ev) => {
    ev.preventDefault();
    const cents = toCents(price.value);
    if (!cents) return out.replaceChildren(msg("السعر يجب أن يكون رقمًا أكبر من صفر.", "err"));
    const row = { name_ar: name.value.trim(), description_ar: desc.value.trim(), points: parseInt(pts.value, 10), price_cents: cents,
      currency: cur.value.trim().toUpperCase(), bonus_note_ar: bonus.value.trim(), active: act.checked, sort_order: parseInt(sort.value, 10) || 0 };
    const { error } = k ? await sb.from("point_packages").update(row).eq("id", k.id) : await sb.from("point_packages").insert(row);
    if (error) return out.replaceChildren(msg(explain(error), "err"));
    paymentsView(msg("حُفظت الباقة.", "ok"));
  } }, ...field("اسم الباقة", name), ...field("الوصف", desc), ...field("عدد النقاط", pts), ...field("السعر (مثل 5.00)", price), ...field("العملة", cur),
    ...field("ملاحظة عرض/مكافأة (نص فقط)", bonus), h("div", { class: "check" }, act, h("label", { for: "kact", style: "margin:0" }, "مفعّلة")),
    ...field("الترتيب", sort), out,
    h("div", { class: "actions" }, h("button", { type: "submit" }, "حفظ"), h("button", { type: "button", class: "secondary", onclick: () => paymentsView() }, "إلغاء")));
  show(h("h1", {}, k ? "تعديل باقة" : "إضافة باقة"), form);
}
