// خوارزميات تقطيع الصور (بلا DOM) — قابلة للاختبار في Node ومستخدمة في المتصفح عبر window.SliceCore
(function (root) {
  "use strict";

  /** تقسيم شبكي. margin = هامش الصورة، gap = المسافة بين الخلايا. */
  function gridRects(w, h, rows, cols, margin, gap) {
    rows = Math.max(1, Math.floor(rows)); cols = Math.max(1, Math.floor(cols));
    margin = Math.max(0, Math.floor(margin || 0)); gap = Math.max(0, Math.floor(gap || 0));
    const cw = Math.floor((w - 2 * margin - gap * (cols - 1)) / cols);
    const ch = Math.floor((h - 2 * margin - gap * (rows - 1)) / rows);
    if (cw < 1 || ch < 1) return [];
    const out = [];
    for (let r = 0; r < rows; r++) for (let c = 0; c < cols; c++)
      out.push({ x: margin + c * (cw + gap), y: margin + r * (ch + gap), w: cw, h: ch });
    return out;
  }

  /** كشف الأجزاء المنفصلة بقناة الشفافية (اتصال 8 جهات). يرجع مستطيلًا محيطًا لكل جزء. */
  function findComponents(alpha, w, h, threshold) {
    const t = threshold == null ? 8 : threshold;
    const visited = new Uint8Array(w * h);
    const stack = new Int32Array(w * h); // كل بكسل يُدفع مرة واحدة فقط
    const boxes = [];
    for (let start = 0; start < w * h; start++) {
      if (visited[start] || alpha[start] <= t) continue;
      let sp = 0; stack[sp++] = start; visited[start] = 1;
      let minX = w, minY = h, maxX = -1, maxY = -1, area = 0;
      while (sp > 0) {
        const p = stack[--sp]; const x = p % w; const y = (p - x) / w;
        area++;
        if (x < minX) minX = x; if (x > maxX) maxX = x; if (y < minY) minY = y; if (y > maxY) maxY = y;
        for (let dy = -1; dy <= 1; dy++) {
          const ny = y + dy; if (ny < 0 || ny >= h) continue;
          for (let dx = -1; dx <= 1; dx++) {
            if (!dx && !dy) continue;
            const nx = x + dx; if (nx < 0 || nx >= w) continue;
            const q = ny * w + nx;
            if (!visited[q] && alpha[q] > t) { visited[q] = 1; stack[sp++] = q; }
          }
        }
      }
      boxes.push({ x: minX, y: minY, w: maxX - minX + 1, h: maxY - minY + 1, area });
    }
    return boxes;
  }

  function near(a, b, gap) {
    return a.x - gap <= b.x + b.w && b.x - gap <= a.x + a.w && a.y - gap <= b.y + b.h && b.y - gap <= a.y + a.h;
  }

  /** دمج المستطيلات المتقاربة (مسافة ≤ gap) — لجمع أجزاء تنتمي لعنصر واحد (مثل شعر متقطع). */
  function mergeBoxes(boxes, gap) {
    const list = boxes.map((b) => Object.assign({}, b));
    let changed = true;
    while (changed) {
      changed = false;
      outer: for (let i = 0; i < list.length; i++) {
        for (let j = i + 1; j < list.length; j++) {
          if (near(list[i], list[j], gap)) {
            const a = list[i], b = list[j];
            const x = Math.min(a.x, b.x), y = Math.min(a.y, b.y);
            list[i] = { x, y, w: Math.max(a.x + a.w, b.x + b.w) - x, h: Math.max(a.y + a.h, b.y + b.h) - y, area: (a.area || 0) + (b.area || 0) };
            list.splice(j, 1); changed = true; break outer;
          }
        }
      }
    }
    return list;
  }

  /** أضيق مستطيل يحيط بالبكسلات غير الشفافة داخل r، أو null إن كان فارغًا. */
  function trimRect(alpha, w, h, r, threshold) {
    const t = threshold == null ? 8 : threshold;
    let minX = r.x + r.w, minY = r.y + r.h, maxX = -1, maxY = -1;
    const x1 = Math.min(r.x + r.w, w), y1 = Math.min(r.y + r.h, h);
    for (let y = Math.max(0, r.y); y < y1; y++) for (let x = Math.max(0, r.x); x < x1; x++) {
      if (alpha[y * w + x] > t) {
        if (x < minX) minX = x; if (x > maxX) maxX = x; if (y < minY) minY = y; if (y > maxY) maxY = y;
      }
    }
    return maxX < 0 ? null : { x: minX, y: minY, w: maxX - minX + 1, h: maxY - minY + 1 };
  }

  /** عدد البكسلات المرئية (شفافية > العتبة) داخل المستطيل. */
  function countOpaque(alpha, w, h, r, threshold) {
    const t = threshold == null ? 8 : threshold;
    let n = 0;
    const x1 = Math.min(r.x + r.w, w), y1 = Math.min(r.y + r.h, h);
    for (let y = Math.max(0, r.y); y < y1; y++) for (let x = Math.max(0, r.x); x < x1; x++) if (alpha[y * w + x] > t) n++;
    return n;
  }

  /** الخطوة الأولى قبل التقطيع: حذف الخلايا الشفافة (التي فيها أقل من minPixels بكسل مرئي). */
  function filterEmpty(alpha, w, h, rects, threshold, minPixels) {
    const need = Math.max(1, Math.floor(minPixels == null ? 1 : minPixels));
    const kept = rects.filter((r) => countOpaque(alpha, w, h, r, threshold) >= need);
    return { kept, removed: rects.length - kept.length };
  }

  function padRect(r, pad, w, h) {
    const x = Math.max(0, r.x - pad), y = Math.max(0, r.y - pad);
    return { x, y, w: Math.min(w, r.x + r.w + pad) - x, h: Math.min(h, r.y + r.h + pad) - y };
  }

  function clampRect(r, w, h) {
    const x = Math.min(Math.max(0, Math.round(r.x)), w - 1), y = Math.min(Math.max(0, Math.round(r.y)), h - 1);
    return { x, y, w: Math.max(1, Math.min(Math.round(r.w), w - x)), h: Math.max(1, Math.min(Math.round(r.h), h - y)) };
  }

  /** ترتيب القراءة (صفوف من الأعلى للأسفل ثم من اليسار لليمين) لتسمية متوقعة. */
  function sortReadingOrder(rects) {
    return rects.slice().sort((a, b) =>
      Math.abs(a.y - b.y) > Math.min(a.h, b.h) / 2 ? a.y - b.y : a.x - b.x);
  }

  const api = { gridRects, findComponents, mergeBoxes, trimRect, countOpaque, filterEmpty, padRect, clampRect, sortReadingOrder };
  if (typeof module !== "undefined" && module.exports) module.exports = api; else root.SliceCore = api;
})(typeof window !== "undefined" ? window : globalThis);
