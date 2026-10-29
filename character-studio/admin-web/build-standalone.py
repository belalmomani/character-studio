#!/usr/bin/env python3
"""يدمج صفحة الإدارة (index.html + config.js + slice-core.js + admin.js + slicer.js) في ملف واحد dist/admin-standalone.html
يسهّل الرفع من الهاتف على أي استضافة ملفات ثابتة. شغّله: python3 build-standalone.py"""
import os, re
here = os.path.dirname(os.path.abspath(__file__))
read = lambda n: open(os.path.join(here, n), encoding="utf-8").read()
html = read("index.html")
for name in ["config.js", "slice-core.js", "admin.js", "slicer.js"]:
    js = read(name)
    assert "</script" not in js.lower(), name + " contains </script"
    tag = '<script src="%s"></script>' % name
    assert tag in html, "missing " + tag
    html = html.replace(tag, "<script>\n" + js + "\n</script>")
os.makedirs(os.path.join(here, "dist"), exist_ok=True)
out = os.path.join(here, "dist", "admin-standalone.html")
open(out, "w", encoding="utf-8").write(html)
print("wrote", out, len(html), "bytes")
