"use strict";
const STORE_KEY = "cs_admin_cfg";
const CFG_KEYS = ["SUPABASE_URL", "SUPABASE_ANON_KEY", "CLOUDINARY_CLOUD_NAME", "CLOUDINARY_UNSIGNED_PRESET"];
function loadStored() { try { return JSON.parse(localStorage.getItem(STORE_KEY) || "{}"); } catch (e) { return {}; } }
// القيم من config.js إن وُجدت، وإلا ما أدخلته في صفحة الإعداد (محفوظ في هذا المتصفح فقط)
const cfg = (() => { const f = window.APP_CONFIG || {}, st = loadStored(), o = {}; for (const k of CFG_KEYS) o[k] = f[k] || st[k] || ""; return o; })();
const root = document.getElementById("root");
const nav = document.getElementById("nav");
const logoutBtn = document.getElementById("logout");
let sb = null, tab = "dashboard";

// ---------- أدوات ----------
function h(tag, props, ...kids) {
  const e = document.createElement(tag);
  for (const [k, v] of Object.entries(props || {})) {
    if (k === "class") e.className = v;
    else if (k.startsWith("on")) e.addEventListener(k.slice(2), v);
    else if (k === "value") e.value = v;
    else if (v === true) e.setAttribute(k, "");
    else if (v !== false && v != null) e.setAttribute(k, v);
  }
  for (const kid of kids.flat()) e.append(kid instanceof Node ? kid : document.createTextNode(kid ?? ""));
  return e;
}
const msg = (text, kind) => h("div", { class: "msg " + (kind || ""), role: kind === "err" ? "alert" : "status" }, text);
const show = (...nodes) => root.replaceChildren(...nodes);
const loading = (title) => show(h("h1", {}, title), h("p", { class: "hint" }, "جارٍ التحميل…"));
function sel(id, options, value) {
  const s = h("select", { id }, options.map(([v, l]) => h("option", { value: v }, l)));
  if (value != null) s.value = value;
  return s;
}
function field(label, input) { return [h("label", { for: input.id }, label), input]; }

const STYLES = { realistic_2d: "واقعي ثنائي الأبعاد", cartoon: "كرتوني", anime: "أنمي", simple_game: "ألعاب مبسّط" };
const ANGLES = { front: "أمامي", side: "جانبي", back: "خلفي", three_quarter: "ثلاثة أرباع" };
const STATUS_AR = { draft: "مسودة", published: "منشور", hidden: "مخفي", archived: "مؤرشف" };
const LICENSE_AR = { pending: "قيد المراجعة", approved: "معتمد", rejected: "مرفوض" };
const ANCHORS = [
  ["head_center", "مركز الرأس"], ["head_base", "قاعدة الرأس"], ["neck", "الرقبة"], ["torso_top", "أعلى الجذع"],
  ["torso_bottom", "أسفل الجذع"], ["shoulder_r", "الكتف الأيمن"], ["shoulder_l", "الكتف الأيسر"],
  ["elbow_r", "المرفق الأيمن"], ["elbow_l", "المرفق الأيسر"], ["wrist_r", "الرسغ الأيمن"], ["wrist_l", "الرسغ الأيسر"],
  ["hand_r", "اليد اليمنى"], ["hand_l", "اليد اليسرى"], ["hip_r", "الورك الأيمن"], ["hip_l", "الورك الأيسر"],
  ["knee_r", "الركبة اليمنى"], ["knee_l", "الركبة اليسرى"], ["ankle_r", "الكاحل الأيمن"], ["ankle_l", "الكاحل الأيسر"],
  ["weapon_r", "تثبيت سلاح (يمين)"], ["weapon_l", "تثبيت سلاح (يسار)"], ["accessory_head", "تثبيت إكسسوار الرأس"],
  ["accessory_back", "تثبيت إكسسوار الظهر"], ["accessory_waist", "تثبيت إكسسوار الخصر"],
];
const MAIN_CATEGORIES = [
  ["human_bodies", "أجسام بشرية"], ["heads", "رؤوس"], ["face_shapes", "أشكال وجوه"], ["eyes", "عيون"], ["eyebrows", "حواجب"],
  ["noses", "أنوف"], ["mouths", "أفواه"], ["ears", "آذان"], ["hairstyles", "تسريحات شعر"], ["beards_mustaches", "لحى وشوارب"],
  ["skin_tones", "ألوان بشرة"], ["tops", "ملابس علوية"], ["bottoms", "ملابس سفلية"], ["dresses_outfits", "فساتين وأزياء متنوعة"],
  ["shoes", "أحذية"], ["gloves", "قفازات"], ["hats", "قبعات"], ["helmets", "خوذ"], ["armor", "دروع"],
  ["accessories", "إكسسوارات"], ["weapons", "أسلحة"], ["bags", "حقائب"], ["wings_fantasy", "أجنحة وعناصر خيالية"],
  ["poses", "أوضاع جسدية"], ["backgrounds", "خلفيات"], ["decorative", "عناصر زخرفية"],
];
const MAX_BYTES = 5 * 1024 * 1024;

const ERRORS = {
  forbidden: "هذا الإجراء للمسؤولين فقط.",
  invalid_points_per_ad: "نقاط الإعلان يجب أن تكون بين 0 و1000.",
  invalid_daily_limit: "الحد اليومي يجب أن يكون بين 0 و100 (0 = بلا حد).",
  invalid_min_watch_seconds: "أقل زمن مشاهدة يجب أن يكون بين 5 و120 ثانية.",
  invalid_ticket_ttl_seconds: "صلاحية التذكرة يجب أن تكون بين 60 و3600 ثانية.",
  publish_requires_version: "لا يمكن النشر: لا توجد نسخة للأصل.",
  publish_requires_approved_commercial_license: "لا يمكن النشر: يلزم ترخيص معتمد يسمح بالاستخدام التجاري.",
  invalid_anchors: "نقاط التثبيت غير صالحة (يجب أن تقع داخل أبعاد الصورة).",
  assets_check: "تحقق من التسعير: الأصل المجاني تكلفته 0، والمدفوع تكلفته أكبر من 0.",
  duplicate: "القيمة موجودة من قبل.",
};
function explain(err) {
  const t = (err && (err.message || err.details)) || "";
  for (const k of Object.keys(ERRORS)) if (t.includes(k)) return ERRORS[k];
  if (/duplicate key/i.test(t)) return ERRORS.duplicate;
  return "تعذّر تنفيذ العملية: " + (t || "خطأ غير معروف");
}
const thumb = (publicId, size = 160) => cfg.CLOUDINARY_CLOUD_NAME && publicId
  ? `https://res.cloudinary.com/${encodeURIComponent(cfg.CLOUDINARY_CLOUD_NAME)}/image/upload/c_limit,w_${size},h_${size}/${publicId}` : "";

// ---------- الدخول ----------
function keyProblem(key) {
  const k = (key || "").trim();
  if (!k) return "أدخل المفتاح العام.";
  if (k.startsWith("sb_secret_")) return "هذا مفتاح سري (secret). لا تستخدمه هنا أبدًا؛ استخدم المفتاح العام (publishable / anon).";
  if (k.startsWith("sb_publishable_")) return null;
  const parts = k.split(".");
  if (parts.length === 3) {
    try {
      const p = JSON.parse(atob(parts[1].replace(/-/g, "+").replace(/_/g, "/")));
      if (p.role === "service_role") return "هذا مفتاح service_role السري. لا تضعه هنا أبدًا.";
      if (p.role === "anon") return null;
    } catch (e) { /* يسقط إلى الرسالة العامة */ }
  }
  return "صيغة المفتاح غير معروفة. انسخ المفتاح العام (anon / publishable) من Project Settings → API.";
}
const urlOk = (u) => /^https:\/\/[a-z0-9-]+\.supabase\.co\/?$/i.test((u || "").trim());

function connectView(error) {
  const url = h("input", { type: "url", id: "cu", dir: "ltr", placeholder: "https://xxxx.supabase.co", value: cfg.SUPABASE_URL });
  const key = h("input", { type: "text", id: "ck", dir: "ltr", placeholder: "sb_publishable_… أو eyJ…", value: cfg.SUPABASE_ANON_KEY, autocomplete: "off" });
  const cloud = h("input", { type: "text", id: "cc", dir: "ltr", placeholder: "اسم سحابة Cloudinary", value: cfg.CLOUDINARY_CLOUD_NAME });
  const preset = h("input", { type: "text", id: "cp", dir: "ltr", placeholder: "اتركه فارغًا (الأكثر أمانًا)", value: cfg.CLOUDINARY_UNSIGNED_PRESET });
  const box = h("div");
  const form = h("form", { class: "panel", onsubmit: (ev) => {
    ev.preventDefault();
    if (!urlOk(url.value)) return box.replaceChildren(msg("الرابط يجب أن يكون بالشكل https://xxxx.supabase.co", "err"));
    const kp = keyProblem(key.value);
    if (kp) return box.replaceChildren(msg(kp, "err"));
    localStorage.setItem(STORE_KEY, JSON.stringify({ SUPABASE_URL: url.value.trim().replace(/\/$/, ""), SUPABASE_ANON_KEY: key.value.trim(),
      CLOUDINARY_CLOUD_NAME: cloud.value.trim(), CLOUDINARY_UNSIGNED_PRESET: preset.value.trim() }));
    location.reload();
  } }, field("Project URL (من Supabase ← Project Settings ← API)", url), field("المفتاح العام (anon / publishable فقط)", key),
    field("اسم سحابة Cloudinary (لازم لرفع الأصول وعرض صورها)", cloud),
    field("Cloudinary unsigned preset (اختياري — بديل أقل أمانًا عن الدالة sign-upload)", preset), box,
    h("p", {}, h("button", { type: "submit" }, "حفظ والمتابعة")));
  nav.hidden = true; logoutBtn.hidden = true;
  show(h("h1", {}, "إعداد الاتصال"), form,
    msg("لا تضع مفتاح service_role أو أي مفتاح سري هنا. تُحفظ هذه القيم في هذا المتصفح فقط.", "warn"));
  if (error) box.replaceChildren(msg(error, "err"));
}

async function start() {
  if (!cfg.SUPABASE_URL || !cfg.SUPABASE_ANON_KEY) return connectView();
  const kp = keyProblem(cfg.SUPABASE_ANON_KEY);
  if (kp || !urlOk(cfg.SUPABASE_URL)) return connectView(kp || "رابط Supabase غير صحيح.");
  if (!window.supabase) return show(msg("تعذّر تحميل مكتبة Supabase. تحقق من الاتصال بالإنترنت.", "err"));
  sb = window.supabase.createClient(cfg.SUPABASE_URL, cfg.SUPABASE_ANON_KEY);
  const { data } = await sb.auth.getSession();
  return data.session ? enter() : loginView();
}

/** رفع صورة إلى Cloudinary: بتوقيع من الدالة sign-upload (الأفضل)، أو بـ unsigned preset إن حددته. */
async function uploadToCloudinary(blob, filename) {
  const fd = new FormData();
  fd.append("file", blob, filename || "image");
  let cloud;
  if (cfg.CLOUDINARY_UNSIGNED_PRESET) {
    cloud = cfg.CLOUDINARY_CLOUD_NAME;
    if (!cloud) throw new Error("أدخل اسم سحابة Cloudinary في إعداد الاتصال.");
    const id = (crypto.randomUUID ? crypto.randomUUID() : [...crypto.getRandomValues(new Uint8Array(16))].map(b => b.toString(16).padStart(2, "0")).join(""));
    fd.append("upload_preset", cfg.CLOUDINARY_UNSIGNED_PRESET);
    fd.append("public_id", "characterstudio/assets/" + id);
  } else {
    const { data: sig, error: se } = await sb.functions.invoke("sign-upload", { body: {} });
    if (se || !sig || !sig.signature) throw new Error("تعذّر الحصول على توقيع الرفع. انشر الدالة sign-upload وأضف أسرار Cloudinary، أو حدّد unsigned preset في إعداد الاتصال.");
    cloud = sig.cloud_name;
    fd.append("api_key", sig.api_key); fd.append("timestamp", sig.timestamp); fd.append("signature", sig.signature);
    fd.append("public_id", sig.public_id); fd.append("allowed_formats", sig.allowed_formats);
  }
  const up = await fetch(`https://api.cloudinary.com/v1_1/${encodeURIComponent(cloud)}/image/upload`, { method: "POST", body: fd });
  const res = await up.json().catch(() => ({}));
  if (!up.ok) throw new Error("فشل الرفع: " + ((res.error && res.error.message) || up.status));
  return res;
}

function loginView(error) {
  const email = h("input", { type: "email", id: "email", autocomplete: "username", required: true });
  const pass = h("input", { type: "password", id: "pass", autocomplete: "current-password", required: true });
  const btn = h("button", { type: "submit" }, "تسجيل الدخول");
  const box = h("div");
  const form = h("form", { class: "panel", onsubmit: async (ev) => {
    ev.preventDefault(); btn.disabled = true;
    const { error: e } = await sb.auth.signInWithPassword({ email: email.value.trim(), password: pass.value });
    btn.disabled = false;
    if (e) { box.replaceChildren(msg("تعذّر تسجيل الدخول. تحقق من البريد وكلمة المرور.", "err")); return; }
    enter();
  } }, field("البريد الإلكتروني", email), field("كلمة المرور", pass), box, h("p", {}, btn));
  nav.hidden = true; logoutBtn.hidden = true;
  show(h("h1", {}, "تسجيل الدخول"), form, h("p", { class: "hint" }, "الدخول للمسؤولين فقط. تُمنح الصلاحية من الخادم وليس من هذه الصفحة."),
    h("button", { type: "button", class: "secondary small", onclick: () => { localStorage.removeItem(STORE_KEY); location.reload(); } }, "تغيير إعدادات الاتصال"));
  if (error) box.replaceChildren(msg(error, "err"));
}

async function enter() {
  const { data: u } = await sb.auth.getUser();
  if (!u || !u.user) return loginView();
  const { data: roles, error } = await sb.from("user_roles").select("role").eq("user_id", u.user.id);
  if (error || !roles || !roles.some(r => r.role === "administrator")) {
    await sb.auth.signOut();
    return loginView("هذا الحساب ليس مسؤولًا.");
  }
  nav.hidden = false; logoutBtn.hidden = false;
  render();
}

function render() {
  nav.querySelectorAll("[data-tab]").forEach(b => {
    if (b.dataset.tab === tab) b.setAttribute("aria-current", "page"); else b.removeAttribute("aria-current");
  });
  ({ dashboard: dashboardView, assets: assetsView, setup: setupView, ads: adsView, audit: auditView })[tab]();
}

// ---------- لوحة المعلومات ----------
async function dashboardView() {
  loading("لوحة المعلومات");
  const { data, error } = await sb.rpc("admin_stats");
  if (error) return show(h("h1", {}, "لوحة المعلومات"), msg(explain(error), "err"));
  const rows = [
    ["المستخدمون", data.users], ["الحسابات المعطّلة", data.disabled_users], ["المشاريع المتزامنة", data.projects],
    ["الأصول (الكل)", data.assets_total], ["الأصول المنشورة", data.assets_published],
    ["النقاط الممنوحة", data.points_granted], ["النقاط المصروفة", data.points_spent],
    ["إعلانات مكتملة اليوم", data.ads_granted_today], ["إعلانات مكتملة (الكل)", data.ads_granted_total],
    ["مكافآت إعلانات مرفوضة", data.ads_rejected_total],
  ];
  show(h("h1", {}, "لوحة المعلومات"),
    h("div", { class: "panel" }, h("dl", {}, rows.map(([k, v]) =>
      h("div", { class: "row" }, h("dt", {}, k), h("dd", {}, Number(v).toLocaleString("ar-EG")))))),
    msg("المدفوعات والاشتراكات غير معروضة لأنها لم تُنفَّذ بعد، ولن تُعرض أرقام لا مصدر لها.", "warn"),
    h("button", { class: "secondary", onclick: dashboardView }, "تحديث"));
}

// ---------- الفئات والتراخيص ----------
async function setupView(notice) {
  loading("الفئات والتراخيص");
  const [c, l] = await Promise.all([
    sb.from("asset_categories").select("*").order("sort_order").order("name_ar"),
    sb.from("asset_licenses").select("*").order("name"),
  ]);
  if (c.error || l.error) return show(h("h1", {}, "الفئات والتراخيص"), msg(explain(c.error || l.error), "err"));
  const cats = c.data, lics = l.data;
  const out = h("div");
  if (notice) out.append(notice);

  // الفئات
  const slug = h("input", { type: "text", id: "slug", placeholder: "hair_long", dir: "ltr", pattern: "[a-z0-9_]+", required: true });
  const name = h("input", { type: "text", id: "cname", required: true });
  const parent = sel("parent", [["", "— فئة رئيسية —"], ...cats.map(x => [x.id, x.name_ar])]);
  const addCat = h("form", { onsubmit: async (ev) => {
    ev.preventDefault();
    const { error } = await sb.from("asset_categories").insert({ slug: slug.value.trim(), name_ar: name.value.trim(), parent_id: parent.value || null });
    setupView(error ? msg(explain(error), "err") : msg("تمت إضافة الفئة.", "ok"));
  } }, field("المعرّف (إنجليزي صغير وأرقام وشرطة سفلية)", slug), field("الاسم بالعربية", name), field("الفئة الأم", parent),
    h("div", { class: "actions" }, h("button", { type: "submit" }, "إضافة فئة"),
      h("button", { type: "button", class: "secondary", onclick: async () => {
        const rows = MAIN_CATEGORIES.map(([s, n], i) => ({ slug: s, name_ar: n, sort_order: i + 1 }));
        const { error } = await sb.from("asset_categories").upsert(rows, { onConflict: "slug", ignoreDuplicates: true });
        setupView(error ? msg(explain(error), "err") : msg("أُضيفت الفئات الأساسية الناقصة.", "ok"));
      } }, "إضافة الفئات الأساسية (26)")));
  const catList = cats.length ? cats.map(x => h("div", { class: "row" },
    h("dt", {}, x.name_ar + (x.parent_id ? " (فرعية)" : ""), h("small", { class: "hint" }, " " + x.slug)),
    h("dd", {}, h("button", { class: "small secondary", onclick: async () => {
      const { error } = await sb.from("asset_categories").update({ active: !x.active }).eq("id", x.id);
      setupView(error ? msg(explain(error), "err") : null);
    } }, x.active ? "إيقاف" : "تفعيل")))) : [h("p", { class: "hint" }, "لا توجد فئات بعد.")];

  // التراخيص
  const ln = h("input", { type: "text", id: "ln", required: true });
  const lu = h("input", { type: "url", id: "lu", dir: "ltr", placeholder: "https://…" });
  const la = h("input", { type: "text", id: "la" });
  const lc = h("input", { type: "checkbox", id: "lc" });
  const lr = h("input", { type: "checkbox", id: "lr" });
  const lnote = h("textarea", { id: "lnote", rows: "2" });
  const addLic = h("form", { onsubmit: async (ev) => {
    ev.preventDefault();
    const { error } = await sb.from("asset_licenses").insert({
      name: ln.value.trim(), source_url: lu.value.trim() || null, author: la.value.trim() || null,
      commercial_use: lc.checked, attribution_required: lr.checked, notes: lnote.value.trim() || null });
    setupView(error ? msg(explain(error), "err") : msg("أُضيف الترخيص بحالة «قيد المراجعة».", "ok"));
  } }, field("اسم الترخيص / المصدر", ln), field("رابط المصدر", lu), field("المؤلف", la),
    h("div", { class: "check" }, lc, h("label", { for: "lc", style: "margin:0" }, "يسمح بالاستخدام التجاري")),
    h("div", { class: "check" }, lr, h("label", { for: "lr", style: "margin:0" }, "يتطلب نسب العمل لصاحبه")),
    field("ملاحظات", lnote), h("div", { class: "actions" }, h("button", { type: "submit" }, "إضافة ترخيص")));
  const review = async (id, ok) => {
    let note = null;
    if (!ok) { note = prompt("سبب الرفض (سيُخفي الأصول المنشورة المرتبطة بهذا الترخيص):"); if (note === null) return; }
    const { error } = await sb.rpc("admin_review_license", { p_id: id, p_approve: ok, p_note: note });
    setupView(error ? msg(explain(error), "err") : msg(ok ? "اعتُمد الترخيص." : "رُفض الترخيص.", "ok"));
  };
  const licList = lics.length ? lics.map(x => h("div", { class: "card" }, h("div", { class: "body" },
    h("div", { class: "title" }, x.name, " ", h("span", { class: "badge " + x.status }, LICENSE_AR[x.status])),
    h("div", { class: "hint" }, (x.commercial_use ? "تجاري مسموح" : "غير مسموح تجاريًا") + (x.attribution_required ? " • يتطلب نسبًا" : "")
      + (x.author ? " • " + x.author : "")),
    x.source_url ? h("div", { class: "hint", dir: "ltr" }, x.source_url) : "",
    h("div", { class: "actions" },
      x.status !== "approved" ? h("button", { class: "small", onclick: () => review(x.id, true) }, "اعتماد") : "",
      x.status !== "rejected" ? h("button", { class: "small danger", onclick: () => review(x.id, false) }, "رفض") : "")))) :
    [h("p", { class: "hint" }, "لا توجد تراخيص بعد. لا يُنشر أي أصل بلا ترخيص معتمد للاستخدام التجاري.")];

  show(h("h1", {}, "الفئات والتراخيص"), out,
    h("div", { class: "panel" }, h("h2", {}, "الفئات"), h("dl", {}, catList), addCat),
    h("div", { class: "panel" }, h("h2", {}, "التراخيص"), licList, h("h2", { style: "margin-top:16px" }, "إضافة ترخيص"), addLic));
}

// ---------- الأصول ----------
async function assetsView(notice) {
  loading("الأصول");
  const [a, c, l] = await Promise.all([
    sb.from("assets").select("*").order("created_at", { ascending: false }).limit(100),
    sb.from("asset_categories").select("*").order("sort_order"),
    sb.from("asset_licenses").select("*").order("name"),
  ]);
  if (a.error || c.error || l.error) return show(h("h1", {}, "الأصول"), msg(explain(a.error || c.error || l.error), "err"));
  const ids = a.data.map(x => x.current_version_id).filter(Boolean);
  const vq = ids.length ? await sb.from("asset_versions").select("id,version,file_public_id,width,height,file_bytes").in("id", ids) : { data: [] };
  const ver = Object.fromEntries((vq.data || []).map(v => [v.id, v]));
  const catName = Object.fromEntries(c.data.map(x => [x.id, x.name_ar]));
  const licById = Object.fromEntries(l.data.map(x => [x.id, x]));

  const setStatus = async (id, status) => {
    const { error } = await sb.from("assets").update({ status }).eq("id", id);
    assetsView(error ? msg(explain(error), "err") : msg("تم تحديث حالة الأصل.", "ok"));
  };

  const cards = a.data.map(x => {
    const v = ver[x.current_version_id];
    const lic = licById[x.license_id];
    const access = sel("acc-" + x.id, [["free", "مجاني"], ["paid", "مدفوع"]], x.access_level);
    const pts = h("input", { type: "number", id: "pts-" + x.id, min: "0", max: "100000", value: String(x.points_cost) });
    const licSel = sel("lic-" + x.id, [["", "— بلا ترخيص —"], ...l.data.map(y => [y.id, `${y.name} (${LICENSE_AR[y.status]})`])], x.license_id || "");
    const save = h("button", { class: "small", onclick: async () => {
      const { error } = await sb.from("assets").update({
        access_level: access.value, points_cost: access.value === "free" ? 0 : parseInt(pts.value, 10) || 0, license_id: licSel.value || null,
      }).eq("id", x.id);
      assetsView(error ? msg(explain(error), "err") : msg("حُفظت التعديلات.", "ok"));
    } }, "حفظ");
    return h("div", { class: "card" },
      v && thumb(v.file_public_id) ? h("img", { class: "thumb", src: thumb(v.file_public_id), alt: x.name_ar, loading: "lazy" }) : h("div", { class: "thumb" }),
      h("div", { class: "body" },
        h("div", { class: "title" }, x.name_ar, " ", h("span", { class: "badge " + x.status }, STATUS_AR[x.status])),
        h("div", { class: "hint" }, [catName[x.category_id], STYLES[x.art_style], ANGLES[x.angle],
          x.access_level === "paid" ? `مدفوع ${x.points_cost} نقطة` : "مجاني", v ? `نسخة ${v.version} • ${v.width}×${v.height}` : "بلا نسخة"].join(" • ")),
        h("div", { class: "hint" }, "الترخيص: " + (lic ? `${lic.name} (${LICENSE_AR[lic.status]})` : "غير محدد")),
        h("div", { class: "actions" },
          (x.status === "draft" || x.status === "hidden") ? h("button", { class: "small", onclick: () => setStatus(x.id, "published") }, "نشر") : "",
          x.status === "published" ? h("button", { class: "small secondary", onclick: () => setStatus(x.id, "hidden") }, "إخفاء") : "",
          x.status !== "archived" ? h("button", { class: "small danger", onclick: () => setStatus(x.id, "archived") }, "أرشفة")
            : h("button", { class: "small secondary", onclick: () => setStatus(x.id, "draft") }, "استعادة كمسودة"),
          h("button", { class: "small secondary", onclick: () => assetForm(x, c.data, l.data) }, "نسخة جديدة")),
        h("details", {}, h("summary", {}, "تعديل التسعير والترخيص"),
          ...field("الوصول", access), ...field("التكلفة بالنقاط", pts), ...field("الترخيص", licSel), h("div", { class: "actions" }, save))));
  });

  show(h("h1", {}, "الأصول"), notice || "",
    c.data.filter(x => x.active).length === 0 ? msg("أنشئ الفئات أولًا من تبويب «الفئات»، ثم أضف ترخيصًا معتمدًا.", "warn") : "",
    h("div", { class: "actions" }, h("button", { onclick: () => assetForm(null, c.data, l.data) }, "إضافة أصل جديد"),
      h("button", { class: "secondary", onclick: () => sliceView(c.data, l.data) }, "تقطيع صورة إلى أجزاء")),
    h("div", { class: "panel", style: "margin-top:16px" }, cards.length ? cards : [h("p", { class: "hint" }, "لا توجد أصول بعد. المكتبة تبدأ فارغة وتُملأ بأصول حقيقية مرخّصة فقط.")]));
}

function anchorPlacer() {
  const anchors = new Map();
  const img = h("img", { alt: "معاينة الأصل", draggable: "false" });
  const stage = h("div", { class: "stage" }, img);
  const pick = sel("apick", ANCHORS);
  const list = h("div");
  const size = { w: 0, h: 0 };
  const redraw = () => {
    stage.querySelectorAll(".dot").forEach(d => d.remove());
    list.replaceChildren(...[...anchors].map(([id, p]) => {
      stage.append(h("div", { class: "dot", style: `left:${p.x / size.w * 100}%;top:${p.y / size.h * 100}%` }));
      const label = (ANCHORS.find(a => a[0] === id) || [id, id])[1];
      return h("div", { class: "row" }, h("dt", {}, `${label} (${p.x}, ${p.y})`),
        h("dd", {}, h("button", { type: "button", class: "small danger", onclick: () => { anchors.delete(id); redraw(); } }, "حذف")));
    }));
  };
  stage.addEventListener("click", (ev) => {
    if (!size.w) return;
    const r = img.getBoundingClientRect();
    const x = Math.round(Math.min(Math.max((ev.clientX - r.left) / r.width * size.w, 0), size.w));
    const y = Math.round(Math.min(Math.max((ev.clientY - r.top) / r.height * size.h, 0), size.h));
    anchors.set(pick.value, { x, y }); redraw();
  });
  img.addEventListener("load", () => { size.w = img.naturalWidth; size.h = img.naturalHeight; anchors.clear(); redraw(); });
  return {
    node: h("div", {}, h("p", { class: "hint" }, "اختر النقطة ثم المس موضعها على الصورة. اختياري، ويمكن تركه فارغًا."),
      ...field("نقطة التثبيت", pick), stage, h("dl", {}, list)),
    setFile: (file) => { img.src = URL.createObjectURL(file); },
    size, anchors: () => [...anchors].map(([id, p]) => ({ id, x: p.x, y: p.y })),
  };
}

async function sha256Hex(file) {
  const d = await crypto.subtle.digest("SHA-256", await file.arrayBuffer());
  return [...new Uint8Array(d)].map(b => b.toString(16).padStart(2, "0")).join("");
}

function assetForm(existing, cats, lics) {
  const isNew = !existing;
  const out = h("div");
  const file = h("input", { type: "file", id: "file", accept: "image/png,image/webp", required: true });
  const placer = anchorPlacer();
  file.addEventListener("change", () => { if (file.files[0]) placer.setFile(file.files[0]); });
  const name = h("input", { type: "text", id: "an", maxlength: "80", required: true });
  const cat = sel("ac", cats.filter(x => x.active).map(x => [x.id, x.name_ar]));
  const style = sel("as", Object.entries(STYLES));
  const angle = sel("aa", Object.entries(ANGLES));
  const access = sel("aacc", [["free", "مجاني"], ["paid", "مدفوع"]]);
  const pts = h("input", { type: "number", id: "apts", min: "0", max: "100000", value: "0" });
  const lic = sel("al", [["", "— بلا ترخيص (لن يمكن نشره) —"], ...lics.map(y => [y.id, `${y.name} (${LICENSE_AR[y.status]})`])]);
  const btn = h("button", { type: "submit" }, isNew ? "رفع وإنشاء الأصل" : "رفع النسخة الجديدة");

  const form = h("form", { class: "panel", onsubmit: async (ev) => {
    ev.preventDefault();
    const f = file.files[0];
    const say = (m, k) => out.replaceChildren(msg(m, k));
    try {
      btn.disabled = true;
      if (isNew && !cat.value) throw new Error("لا توجد فئة مفعّلة. أنشئ الفئات من تبويب «الفئات» أولًا.");
      if (isNew && !name.value.trim()) throw new Error("أدخل اسم الأصل.");
      if (!f) throw new Error("اختر ملف الصورة.");
      if (!["image/png", "image/webp"].includes(f.type)) throw new Error("الصيغ المسموحة: PNG أو WebP فقط.");
      if (f.size > MAX_BYTES) throw new Error("حجم الملف يتجاوز 5 ميغابايت.");
      const w = placer.size.w, ht = placer.size.h;
      if (!w || w < 16 || ht < 16 || w > 4096 || ht > 4096) throw new Error("أبعاد الصورة يجب أن تكون بين 16 و4096 بكسل.");
      if (isNew && access.value === "paid" && !(parseInt(pts.value, 10) > 0)) throw new Error("الأصل المدفوع يحتاج تكلفة بالنقاط أكبر من 0.");

      say("جارٍ رفع الصورة إلى Cloudinary…");
      const res = await uploadToCloudinary(f, f.name);
      if (!["png", "webp"].includes(res.format) || res.bytes > MAX_BYTES || res.width !== w || res.height !== ht) {
        throw new Error("نتيجة الرفع لا تطابق الملف (الصيغة/الحجم/الأبعاد). لم يُنشأ الأصل.");
      }

      say("جارٍ حفظ الأصل…");
      const version = { file_type: res.format, file_public_id: res.public_id, thumb_public_id: res.public_id, width: res.width,
        height: res.height, file_bytes: res.bytes, sha256: await sha256Hex(f), anchors: placer.anchors(), compatibility: {}, min_app_version: 1 };
      const { error } = isNew
        ? await sb.rpc("admin_create_asset", { p_category: cat.value, p_name: name.value.trim(), p_style: style.value, p_angle: angle.value,
            p_access: access.value, p_points: access.value === "free" ? 0 : parseInt(pts.value, 10), p_license: lic.value || null, p_version: version })
        : await sb.rpc("admin_add_asset_version", { p_asset: existing.id, p_version: version });
      if (error) throw error;
      assetsView(msg(isNew ? "أُنشئ الأصل كمسودة. اعتمد ترخيصه ثم انشره." : "أُضيفت النسخة الجديدة.", "ok"));
    } catch (e) {
      say(e instanceof Error && !("code" in e) ? e.message : explain(e), "err");
      btn.disabled = false;
    }
  } },
    isNew ? [...field("اسم الأصل", name), ...field("الفئة", cat), ...field("النمط الفني", style), ...field("الزاوية", angle),
      ...field("الوصول", access), ...field("التكلفة بالنقاط (للمدفوع)", pts), ...field("الترخيص", lic)]
      : h("p", { class: "hint" }, `نسخة جديدة للأصل: ${existing.name_ar}`),
    ...field("ملف الصورة (PNG أو WebP، حتى 5 ميغابايت، 16-4096 بكسل)", file), placer.node, out,
    h("div", { class: "actions" }, btn, h("button", { type: "button", class: "secondary", onclick: () => assetsView() }, "إلغاء")));
  show(h("h1", {}, isNew ? "إضافة أصل" : "نسخة جديدة"), form);
}

// ---------- الإعلانات ----------
async function adsView() {
  loading("الإعلانات المكافأة");
  const { data, error } = await sb.from("app_settings").select("value").eq("key", "rewarded_ads").maybeSingle();
  if (error) return show(h("h1", {}, "الإعلانات المكافأة"), msg(explain(error), "err"));
  const v = (data && data.value) || { enabled: false, points_per_ad: 0, daily_limit: 0, min_watch_seconds: 15, ticket_ttl_seconds: 600 };
  const num = (id, val, min, max) => h("input", { type: "number", id, value: String(val), min, max, inputmode: "numeric" });
  const enabled = h("input", { type: "checkbox", id: "enabled", checked: !!v.enabled });
  const points = num("points", v.points_per_ad, 0, 1000), daily = num("daily", v.daily_limit, 0, 100);
  const minw = num("minw", v.min_watch_seconds, 5, 120), ttl = num("ttl", v.ticket_ttl_seconds, 60, 3600);
  const out = h("div");
  const save = h("button", { type: "submit" }, "حفظ الإعدادات");
  const form = h("form", { class: "panel", onsubmit: async (ev) => {
    ev.preventDefault(); save.disabled = true;
    const value = { enabled: enabled.checked, points_per_ad: parseInt(points.value, 10), daily_limit: parseInt(daily.value, 10),
      min_watch_seconds: parseInt(minw.value, 10), ticket_ttl_seconds: parseInt(ttl.value, 10) };
    const { error: e } = await sb.from("app_settings").upsert({ key: "rewarded_ads", value });
    save.disabled = false;
    out.replaceChildren(e ? msg(explain(e), "err") : msg("تم حفظ الإعدادات.", "ok"));
  } },
    h("div", { class: "check" }, enabled, h("label", { for: "enabled", style: "margin:0" }, "تفعيل الإعلانات المكافأة")),
    ...field("النقاط لكل إعلان", points), ...field("الحد اليومي لكل مستخدم (0 = بلا حد)", daily),
    ...field("أقل زمن مشاهدة قبل قبول المطالبة (ثانية)", minw), ...field("صلاحية التذكرة (ثانية)", ttl), out, h("p", {}, save));
  show(h("h1", {}, "الإعلانات المكافأة"), form,
    msg("هذه الإعدادات لا تثبت أن المستخدم شاهد الإعلان فعلًا؛ هي تحدّ من التكرار والتجاوز فقط. التحقق الخادمي من المزود لم يُثبَّت بعد.", "warn"));
}

// ---------- سجل التدقيق ----------
async function auditView() {
  loading("سجل التدقيق");
  const { data, error } = await sb.from("admin_audit_logs").select("*").order("created_at", { ascending: false }).limit(50);
  if (error) return show(h("h1", {}, "سجل التدقيق"), msg(explain(error), "err"));
  if (!data.length) return show(h("h1", {}, "سجل التدقيق"), h("div", { class: "panel" }, "لا توجد عمليات إدارية مسجّلة بعد."));
  show(h("h1", {}, "سجل التدقيق"), h("div", { class: "panel" }, data.map(r =>
    h("div", { class: "log" }, `${r.action} — ${r.target ?? ""}`,
      h("small", {}, new Date(r.created_at).toLocaleString("ar-EG") + " • المسؤول " + String(r.admin_id).slice(0, 8))))));
}

nav.addEventListener("click", (ev) => {
  const b = ev.target.closest("button[data-tab]"); if (!b) return;
  tab = b.dataset.tab; render();
});
logoutBtn.addEventListener("click", async () => { await sb.auth.signOut(); loginView(); });
start();
