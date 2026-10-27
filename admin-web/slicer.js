"use strict";
// أداة تقطيع صورة إلى أجزاء وإنشاء أصل مستقل لكل جزء. تعتمد على دوال admin.js (h, msg, show, field, sel, sb ...) وعلى SliceCore.

async function uploadBlobToCloudinary(blob, w, ht) {
  const res = await uploadToCloudinary(blob, "slice.png");
  if (!["png", "webp"].includes(res.format) || res.bytes > MAX_BYTES || res.width !== w || res.height !== ht) {
    throw new Error("نتيجة الرفع لا تطابق الجزء (الصيغة/الحجم/الأبعاد).");
  }
  return res;
}

function sliceView(cats, lics) {
  const core = window.SliceCore;
  const MAX_SIDE = 4096, MIN_SIDE = 16;
  const st = { canvas: null, ctx: null, w: 0, h: 0, alpha: null, rects: [], drag: null };

  const out = h("div");
  const file = h("input", { type: "file", id: "sfile", accept: "image/png,image/webp,image/jpeg" });
  const img = h("img", { alt: "الصورة المصدر", draggable: "false" });
  const stage = h("div", { class: "stage" }, img);
  const mode = sel("smode", [["grid", "شبكة منتظمة (صفوف × أعمدة)"], ["auto", "كشف تلقائي للأجزاء المنفصلة (PNG شفاف)"], ["manual", "رسم يدوي بالإصبع"]]);
  const num = (id, v, min, max) => h("input", { type: "number", id, value: String(v), min: String(min), max: String(max), inputmode: "numeric" });
  const rows = num("srows", 2, 1, 50), cols = num("scols", 2, 1, 50), margin = num("smargin", 0, 0, 2000), gap = num("sgap", 0, 0, 2000);
  const thr = num("sthr", 8, 0, 254), minArea = num("sarea", 200, 1, 10000000), merge = num("smerge", 6, 0, 500);
  const trim = h("input", { type: "checkbox", id: "strim", checked: true });
  const pad = num("spad", 0, 0, 200);
  const emptyCb = h("input", { type: "checkbox", id: "sempty", checked: true });
  const minPx = num("sminpx", 10, 1, 1000000);
  const gridBox = h("div", {}, ...field("عدد الصفوف", rows), ...field("عدد الأعمدة", cols), ...field("هامش الصورة (بكسل)", margin), ...field("مسافة بين الخلايا (بكسل)", gap));
  const autoBox = h("div", {}, ...field("حد الشفافية (0-254): ما فوقه يعدّ جزءًا من الشكل", thr),
    ...field("أقل مساحة للجزء (بكسل) — تُهمل الشوائب الأصغر", minArea),
    ...field("دمج الأجزاء المتقاربة (بكسل) — 0 لعدم الدمج", merge));
  const manualBox = h("p", { class: "hint" }, "ارسم مستطيلًا بالسحب فوق الصورة. كل رسم يضيف جزءًا. الصفحة لا تتحرك أثناء السحب على الصورة.");
  const modeBoxes = h("div", {}, gridBox, autoBox, manualBox);
  const listBox = h("div");
  const batchBox = h("div", { hidden: true });
  const prog = h("div");

  const getAlpha = () => {
    if (st.alpha) return st.alpha;
    const d = st.ctx.getImageData(0, 0, st.w, st.h).data;
    const a = new Uint8Array(st.w * st.h);
    for (let i = 0; i < a.length; i++) a[i] = d[i * 4 + 3];
    return (st.alpha = a);
  };
  const toImg = (ev) => {
    const r = img.getBoundingClientRect();
    return { x: Math.min(Math.max((ev.clientX - r.left) / r.width * st.w, 0), st.w), y: Math.min(Math.max((ev.clientY - r.top) / r.height * st.h, 0), st.h) };
  };

  const syncMode = () => {
    gridBox.hidden = mode.value !== "grid"; autoBox.hidden = mode.value !== "auto"; manualBox.hidden = mode.value !== "manual";
    stage.style.touchAction = mode.value === "manual" ? "none" : "manipulation";
    applyBtn.hidden = mode.value === "manual";
  };
  mode.addEventListener("change", syncMode);

  file.addEventListener("change", () => {
    const f = file.files[0]; if (!f) return;
    const url = URL.createObjectURL(f);
    const probe = new Image();
    probe.onload = () => {
      if (probe.naturalWidth > MAX_SIDE || probe.naturalHeight > MAX_SIDE) {
        out.replaceChildren(msg(`أكبر بُعد مسموح للتقطيع ${MAX_SIDE} بكسل. صغّر الصورة أولًا.`, "err")); return;
      }
      st.w = probe.naturalWidth; st.h = probe.naturalHeight; st.alpha = null; st.rects = [];
      st.canvas = document.createElement("canvas"); st.canvas.width = st.w; st.canvas.height = st.h;
      st.ctx = st.canvas.getContext("2d", { willReadFrequently: true }); st.ctx.drawImage(probe, 0, 0);
      img.src = url; out.replaceChildren(); refresh();
    };
    probe.onerror = () => out.replaceChildren(msg("تعذّر قراءة الصورة.", "err"));
    probe.src = url;
  });

  const applyBtn = h("button", { type: "button", onclick: () => {
    if (!st.ctx) return out.replaceChildren(msg("اختر صورة أولًا.", "err"));
    try {
      let r = [];
      if (mode.value === "grid") {
        r = core.gridRects(st.w, st.h, +rows.value, +cols.value, +margin.value, +gap.value);
        if (!r.length) return out.replaceChildren(msg("الصفوف والأعمدة والهوامش لا تتسع لأبعاد الصورة.", "err"));
      } else {
        const boxes = core.findComponents(getAlpha(), st.w, st.h, +thr.value).filter(b => b.area >= +minArea.value);
        r = +merge.value > 0 ? core.mergeBoxes(boxes, +merge.value) : boxes;
        if (!r.length) return out.replaceChildren(msg("لم أجد أجزاء. إن كانت الصورة بلا شفافية (مثل JPEG) فاستخدم الشبكة أو الرسم اليدوي.", "warn"));
      }
      r = r.map(({ x, y, w, h }) => ({ x, y, w, h }));
      // الخطوة الأولى: حذف الخلايا الشفافة الفارغة، ثم تكمل بقية الخطوات على ما تبقى
      let removed = 0;
      if (emptyCb.checked) {
        const f = core.filterEmpty(getAlpha(), st.w, st.h, r, +thr.value, +minPx.value);
        r = f.kept; removed = f.removed;
        if (!r.length) { st.rects = []; refresh(); return out.replaceChildren(msg(`كل الخلايا (${removed}) شفافة فارغة، فلم يبقَ ما يُقطَّع.`, "warn")); }
      }
      if (trim.checked) r = r.map(q => core.trimRect(getAlpha(), st.w, st.h, q, +thr.value)).filter(Boolean);
      if (+pad.value > 0) r = r.map(q => core.padRect(q, +pad.value, st.w, st.h));
      st.rects = core.sortReadingOrder(r).map(q => ({ ...q, on: q.w >= MIN_SIDE && q.h >= MIN_SIDE, name: "" }));
      out.replaceChildren(msg(`تم استخراج ${st.rects.length} جزءًا` + (emptyCb.checked ? `، وحُذفت ${removed} خلية شفافة فارغة` : "") + ". راجعها واحذف ما لا تريده.", "ok"));
      refresh();
    } catch (e) { out.replaceChildren(msg("تعذّر التقطيع: " + (e.message || e), "err")); }
  } }, "تقطيع");

  // الرسم اليدوي
  stage.addEventListener("pointerdown", (ev) => {
    if (mode.value !== "manual" || !st.ctx) return;
    ev.preventDefault(); stage.setPointerCapture(ev.pointerId);
    const p = toImg(ev); st.drag = { x0: p.x, y0: p.y, x1: p.x, y1: p.y }; drawOverlay();
  });
  stage.addEventListener("pointermove", (ev) => {
    if (!st.drag) return; const p = toImg(ev); st.drag.x1 = p.x; st.drag.y1 = p.y; drawOverlay();
  });
  stage.addEventListener("pointerup", () => {
    const d = st.drag; st.drag = null; if (!d) return;
    const r = core.clampRect({ x: Math.min(d.x0, d.x1), y: Math.min(d.y0, d.y1), w: Math.abs(d.x1 - d.x0), h: Math.abs(d.y1 - d.y0) }, st.w, st.h);
    if (r.w >= 8 && r.h >= 8 && emptyCb.checked && core.countOpaque(getAlpha(), st.w, st.h, r, +thr.value) < +minPx.value) {
      out.replaceChildren(msg("المنطقة المرسومة شفافة فارغة فلم تُضَف.", "warn"));
    } else if (r.w >= 8 && r.h >= 8) {
      let q = r;
      if (trim.checked) { const t = core.trimRect(getAlpha(), st.w, st.h, r, +thr.value); if (t) q = t; }
      st.rects.push({ ...q, on: q.w >= MIN_SIDE && q.h >= MIN_SIDE, name: "" });
    }
    refresh();
  });

  function drawOverlay() {
    stage.querySelectorAll(".box").forEach(b => b.remove());
    const place = (r, cls, label) => {
      const b = h("div", { class: "box " + cls, style: `left:${r.x / st.w * 100}%;top:${r.y / st.h * 100}%;width:${r.w / st.w * 100}%;height:${r.h / st.h * 100}%` });
      if (label) b.append(h("span", { class: "lbl" }, label)); stage.append(b);
    };
    st.rects.forEach((r, i) => place(r, r.on ? "" : "off", String(i + 1)));
    if (st.drag) place({ x: Math.min(st.drag.x0, st.drag.x1), y: Math.min(st.drag.y0, st.drag.y1), w: Math.abs(st.drag.x1 - st.drag.x0), h: Math.abs(st.drag.y1 - st.drag.y0) }, "live", "");
  }

  const base = h("input", { type: "text", id: "sbase", maxlength: "70", value: "جزء" });
  const cat = sel("scat", cats.filter(x => x.active).map(x => [x.id, x.name_ar]));
  const style = sel("sstyle", Object.entries(STYLES)), angle = sel("sangle", Object.entries(ANGLES));
  const access = sel("sacc", [["free", "مجاني"], ["paid", "مدفوع"]]);
  const pts = h("input", { type: "number", id: "spts", min: "0", max: "100000", value: "0" });
  const lic = sel("slic", [["", "— بلا ترخيص (لن يمكن نشرها) —"], ...lics.map(y => [y.id, `${y.name} (${LICENSE_AR[y.status]})`])]);
  const go = h("button", { type: "button" }, "رفع وإنشاء الأصول المحددة");
  batchBox.append(h("h2", {}, "بيانات الأصول"), ...field("الاسم الأساسي (يُرقَّم تلقائيًا، ويمكن تعديل كل اسم أعلاه)", base),
    ...field("الفئة", cat), ...field("النمط الفني", style), ...field("الزاوية", angle), ...field("الوصول", access),
    ...field("التكلفة بالنقاط (للمدفوع)", pts), ...field("الترخيص", lic),
    h("div", { class: "actions" }, go), prog);

  function refresh() {
    drawOverlay();
    if (!st.rects.length) { listBox.replaceChildren(); batchBox.hidden = true; return; }
    batchBox.hidden = false;
    listBox.replaceChildren(h("h2", {}, `الأجزاء (${st.rects.filter(r => r.on).length} محدّد من ${st.rects.length})`),
      ...st.rects.map((r, i) => {
        const c = document.createElement("canvas"); const k = Math.min(1, 56 / Math.max(r.w, r.h));
        c.width = Math.max(1, Math.round(r.w * k)); c.height = Math.max(1, Math.round(r.h * k)); c.className = "thumb"; c.style.width = c.style.height = "56px";
        c.getContext("2d").drawImage(st.canvas, r.x, r.y, r.w, r.h, 0, 0, c.width, c.height);
        const small = r.w < MIN_SIDE || r.h < MIN_SIDE;
        const cb = h("input", { type: "checkbox", id: "on" + i, checked: r.on, disabled: small, onchange: (e) => { r.on = e.target.checked; refresh(); } });
        const nm = h("input", { type: "text", id: "nm" + i, maxlength: "80", placeholder: `${base.value || "جزء"} ${i + 1}`, value: r.name,
          oninput: (e) => { r.name = e.target.value; } });
        return h("div", { class: "card" }, c, h("div", { class: "body" },
          h("div", { class: "check" }, cb, h("label", { for: "on" + i, style: "margin:0" }, `#${i + 1} — ${r.w}×${r.h}`),
            small ? h("span", { class: "badge rejected" }, "أصغر من 16 بكسل") : ""),
          nm, h("div", { class: "actions" }, h("button", { type: "button", class: "small danger", onclick: () => { st.rects.splice(i, 1); refresh(); } }, "حذف"))));
      }));
  }

  go.addEventListener("click", async () => {
    const picked = st.rects.map((r, i) => ({ r, i })).filter(({ r }) => r.on);
    if (!picked.length) return prog.replaceChildren(msg("لم تحدد أي جزء.", "err"));
    if (!cat.value) return prog.replaceChildren(msg("لا توجد فئة مفعّلة. أنشئ الفئات أولًا.", "err"));
    if (access.value === "paid" && !(parseInt(pts.value, 10) > 0)) return prog.replaceChildren(msg("الأصل المدفوع يحتاج تكلفة أكبر من 0.", "err"));
    if (!confirm(`سيُنشأ ${picked.length} أصلًا كمسودة. متابعة؟`)) return;
    go.disabled = true;
    const lines = picked.map(({ r, i }) => h("div", { class: "log" }, `#${i + 1}: بانتظار الدور`));
    prog.replaceChildren(h("div", { class: "panel" }, lines));
    let ok = 0;
    for (let n = 0; n < picked.length; n++) {
      const { r, i } = picked[n];
      const set = (t) => { lines[n].textContent = `#${i + 1}: ${t}`; };
      try {
        set("جارٍ التجهيز…");
        const c = document.createElement("canvas"); c.width = r.w; c.height = r.h;
        c.getContext("2d").drawImage(st.canvas, r.x, r.y, r.w, r.h, 0, 0, r.w, r.h);
        const blob = await new Promise(res => c.toBlob(res, "image/png"));
        if (!blob) throw new Error("تعذّر إنشاء الصورة");
        if (blob.size > MAX_BYTES) throw new Error("الحجم يتجاوز 5 ميغابايت");
        set("جارٍ الرفع…");
        const res = await uploadBlobToCloudinary(blob, r.w, r.h);
        const version = { file_type: res.format, file_public_id: res.public_id, thumb_public_id: res.public_id, width: res.width,
          height: res.height, file_bytes: res.bytes, sha256: await sha256Hex(blob), anchors: [], compatibility: {}, min_app_version: 1 };
        const name = (r.name.trim() || `${base.value.trim() || "جزء"} ${i + 1}`).slice(0, 80);
        const { error } = await sb.rpc("admin_create_asset", { p_category: cat.value, p_name: name, p_style: style.value, p_angle: angle.value,
          p_access: access.value, p_points: access.value === "free" ? 0 : parseInt(pts.value, 10), p_license: lic.value || null, p_version: version });
        if (error) throw error;
        ok++; set("تم ✓");
      } catch (e) { set("فشل: " + (e instanceof Error && !("code" in e) ? e.message : explain(e))); }
    }
    go.disabled = false;
    prog.append(msg(`اكتمل: ${ok} نجح من ${picked.length}. الأصول الناجحة مسودات؛ أضف نقاط التثبيت بـ«نسخة جديدة» إن لزم، واعتمد الترخيص ثم انشر.`, ok === picked.length ? "ok" : "warn"),
      h("div", { class: "actions" }, h("button", { type: "button", onclick: () => assetsView() }, "العودة إلى الأصول")));
  });

  syncMode();
  show(h("h1", {}, "تقطيع صورة إلى أجزاء"),
    h("div", { class: "panel" },
      h("p", { class: "hint" }, "ارفع صورة كبيرة (لوحة أجزاء أو شخصية كاملة) وقسّمها إلى أجزاء، فيصير كل جزء أصلًا مستقلًا. الأصل يُحفظ PNG بشفافيته. تأكد أنك تملك حقوق الصورة."),
      ...field("الصورة (PNG أو WebP أو JPEG، حتى 4096×4096)", file), stage, ...field("طريقة التقطيع", mode), modeBoxes,
      h("div", { class: "check" }, emptyCb, h("label", { for: "sempty", style: "margin:0" }, "الخطوة 1: حذف الخلايا الشفافة الفارغة قبل التقطيع")),
      ...field("أقل عدد بكسلات مرئية لتُعدّ الخلية غير فارغة (يتجاهل الشوائب)", minPx),
      h("div", { class: "check" }, trim, h("label", { for: "strim", style: "margin:0" }, "الخطوة 2: قص الحواف الشفافة حول كل جزء")),
      ...field("حشوة حول كل جزء (بكسل)", pad), h("div", { class: "actions" }, applyBtn,
        h("button", { type: "button", class: "secondary", onclick: () => { st.rects = []; refresh(); } }, "مسح الأجزاء"),
        h("button", { type: "button", class: "secondary", onclick: () => assetsView() }, "رجوع")), out),
    h("div", { class: "panel" }, listBox, batchBox));
}
