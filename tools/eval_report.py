#!/usr/bin/env python3
"""Builds docs/evaluation/report.html from core/build/eval/results.json (run :core:test first)."""
import json, html, pathlib, sys

root = pathlib.Path(__file__).resolve().parent.parent
r = json.loads((root / "core/build/eval/results.json").read_text(encoding="utf-8"))
items = {}
for line in (root / "core/src/test/resources/eval/thai_scam_benchmark.tsv").read_text(encoding="utf-8").splitlines():
    if line and not line.startswith("#"):
        i, label, cat, text = line.split("\t")
        items[i] = text

CAT = {"police_callcenter": "แก๊งคอลเซ็นเตอร์/ตำรวจปลอม", "bank_phishing": "ลิงก์ธนาคารปลอม", "otp": "หลอกขอ OTP/ข้อมูลบัตร",
       "parcel": "พัสดุ", "gov_refund": "อ้างหน่วยงานรัฐ/คืนเงิน", "utility": "ค่าน้ำค่าไฟ", "prize": "หลอกว่าได้รางวัล",
       "loan": "เงินกู้ปลอม", "investment": "หลอกลงทุน", "job": "หลอกทำงานออนไลน์", "relative": "แอบอ้างเป็นญาติ",
       "romance": "หลอกให้รัก", "remote_app": "แอปควบคุมเครื่อง", "shopping": "ร้านค้าปลอม", "gambling": "พนันออนไลน์",
       "insurance": "ประกันปลอม", "family": "ครอบครัว", "health": "สุขภาพ/โรงพยาบาล", "bank_legit": "ธนาคาร (ของจริง)",
       "delivery_legit": "ขนส่ง (ของจริง)", "utility_legit": "ค่าน้ำค่าไฟ (ของจริง)", "gov_legit": "หน่วยงานรัฐ (ของจริง)",
       "community": "ชุมชน", "shopping_legit": "ร้านค้า (ของจริง)", "work": "งาน", "friends": "เพื่อน", "scam_awareness": "ข่าวเตือนภัย"}

s, d = r["suspiciousThreshold"], r["dangerousThreshold"]
pct = lambda x: f"{x*100:.1f}%"
preds = r["predictions"]
orig = [p for p in preds if p["variant"] == "original"]
misses = [p for p in orig if (p["level"] != "SAFE") != (p["label"] == "scam")]
obf_miss = [p for p in preds if p["variant"] == "obfuscated" and p["level"] == "SAFE"]

WHY = {
    "s012": "ขอ \"รหัส\" อย่างสุภาพโดยไม่มีคำว่า OTP หรือการเร่งรัด — ต้องเข้าใจบริบทมากกว่าคำ",
    "s035": "งานรีวิวที่ให้จ่ายค่าสมัคร ใช้คำที่ไม่อยู่ในกฎ (\"ค่าสมัครสมาชิก\")",
    "s040": "หลอกให้รักแบบอ้างเป็นทหาร คะแนน 25 ต่ำกว่าเกณฑ์เพียงเล็กน้อย",
    "s056": "งานแพ็คของที่ให้จ่ายค่าอุปกรณ์ คะแนน 28 ต่ำกว่าเกณฑ์เพียงเล็กน้อย",
    "b015": "ข้อความเตือนจากธนาคารจริงที่พูดถึง \"ข้อมูลส่วนตัว\" และ \"ลิงก์\" — ระบบไม่เข้าใจการปฏิเสธ (\"ไม่มีนโยบาย\")",
    "b021": "บิลค่าไฟจริง: \"การไฟฟ้า\" + \"ภายใน\" ถูกนับเป็นอ้างหน่วยงาน + เร่งรัด",
    "b041": "ข่าวเตือนภัยใช้คำเดียวกับมิจฉาชีพ (\"ตำรวจ\", \"โอนเงิน\")",
    "b042": "ข่าวเตือนภัยที่บอกว่า \"อย่าติดตั้งแอป\" — ระบบไม่เข้าใจคำปฏิเสธ",
    "b056": "\"สมุดบัญชีมา\" ถูกการจับคำแบบสะกดผิดได้ 1 ตัว มองเป็น \"บัญชีม้า\"",
}

def bars(d, scam=True):
    rows = []
    for k, v in sorted(d.items(), key=lambda kv: (kv[1] if scam else -kv[1], kv[0])):
        if scam:
            rows.append(f'<div class="bar"><span>{html.escape(CAT.get(k,k))}</span><div class="track"><i style="width:{v*100:.0f}%" class="{"ok" if v>=0.999 else "warn"}"></i></div><b>{v*100:.0f}%</b></div>')
    return "\n".join(rows)

fp_rows = "".join(f'<tr><td>{html.escape(CAT.get(k,k))}</td><td class="num">{v}</td></tr>' for k, v in r["perCategoryFalsePositive"].items())
miss_rows = "".join(
    f'<tr><td>{p["id"]}</td><td>{"มิจฉาชีพที่หลุด" if p["label"]=="scam" else "เตือนผิด"}</td><td class="num">{p["score"]}</td>'
    f'<td>{html.escape(items[p["id"]])}</td><td>{html.escape(WHY.get(p["id"],""))}</td></tr>' for p in misses)

page = f"""<!DOCTYPE html><html lang="th"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
<title>ScamSense — ผลประเมินความแม่นยำ</title>
<link href="https://fonts.googleapis.com/css2?family=IBM+Plex+Sans+Thai:wght@400;600;700&display=swap" rel="stylesheet">
<style>
/* VOICE: research report, calm and exact. IDEA: a lab test certificate. PICTURE: the confusion matrix + headline metrics.
   COLOUR: white, ink, brand blue #1E4FD8 dominant, green/amber for pass/partial. TYPE: IBM Plex Sans Thai. LAYOUT: one column, 980px. */
:root{{color-scheme:light dark;--bg:light-dark(#fff,#0b1220);--ink:light-dark(#0f172a,#e8edf7);--mut:light-dark(#475569,#a7b2c7);--line:light-dark(#e2e8f0,#22304d);--brand:#1E4FD8;--ok:#15803D;--warn:#B45309;--bad:#C81E1E;--soft:light-dark(#f4f6fb,#131c31)}}
:root[data-theme=light]{{color-scheme:light}}:root[data-theme=dark]{{color-scheme:dark}}
*{{box-sizing:border-box;min-width:0}}body{{margin:0;background:var(--bg);color:var(--ink);font:16px/1.65 "IBM Plex Sans Thai",system-ui,sans-serif;overflow-x:clip}}
header{{background:var(--brand);color:#fff;padding:clamp(28px,5vw,56px) max(5vw,16px)}}header h1{{font-size:clamp(28px,4.5vw,44px);line-height:1.15;margin:0 0 8px;text-wrap:balance}}header p{{margin:0;opacity:.9;max-width:760px}}
main{{max-width:980px;margin:0 auto;padding:0 max(4vw,16px) 48px}}h2{{font-size:24px;margin:40px 0 10px}}
.tiles{{display:grid;grid-template-columns:repeat(auto-fit,minmax(150px,1fr));gap:12px;margin-top:-28px}}
.tile{{background:var(--bg);border:1px solid var(--line);border-radius:16px;padding:16px}}.tile b{{display:block;font-size:30px;line-height:1.1;color:var(--brand)}}.tile span{{color:var(--mut);font-size:14px}}
table{{width:100%;border-collapse:collapse;font-size:15px}}th,td{{border-bottom:1px solid var(--line);padding:8px;text-align:left;vertical-align:top}}.num{{text-align:right;white-space:nowrap}}
.cm{{display:grid;grid-template-columns:auto 1fr 1fr;gap:6px;max-width:520px}}.cm div{{padding:14px;border-radius:12px;text-align:center}}.cm .h{{color:var(--mut);font-size:14px;padding:6px}}
.cm .tp,.cm .tn{{background:color-mix(in srgb,var(--ok) 16%,transparent)}}.cm .fp,.cm .fn{{background:color-mix(in srgb,var(--bad) 14%,transparent)}}.cm strong{{display:block;font-size:28px}}
.bar{{display:grid;grid-template-columns:minmax(120px,220px) 1fr 52px;gap:10px;align-items:center;margin:6px 0}}.track{{height:12px;background:var(--soft);border-radius:8px;overflow:hidden}}.track i{{display:block;height:100%}}.track .ok{{background:var(--ok)}}.track .warn{{background:var(--warn)}}.bar b{{text-align:right}}
.note{{background:var(--soft);border-radius:14px;padding:14px 16px;color:var(--ink)}}.scroll{{overflow-x:auto}}
@media(max-width:600px){{.bar{{grid-template-columns:1fr 46px}}.bar .track{{grid-column:1/3;grid-row:2}}}}
</style></head><body>
<header><h1>ScamSense จับข้อความมิจฉาชีพได้ {pct(s['recall'])} และเตือนผิดเพียง {pct(s['falsePositiveRate'])}</h1>
<p>ผลประเมินบนชุดทดสอบภาษาไทยที่แยกจากข้อมูลที่ใช้สร้างระบบ ({r['datasetSize']} ข้อความ: มิจฉาชีพ {r['scams']} • ข้อความปกติ {r['benign']}) ตรวจในเครื่องเฉลี่ย {r['latencyMicrosMean']/1000:.2f} มิลลิวินาทีต่อข้อความ</p></header>
<main>
<div class="tiles">
<div class="tile"><b>{pct(s['accuracy'])}</b><span>ความถูกต้องโดยรวม (Accuracy)</span></div>
<div class="tile"><b>{pct(s['recall'])}</b><span>จับมิจฉาชีพได้ (Recall)</span></div>
<div class="tile"><b>{pct(s['precision'])}</b><span>เตือนแล้วถูก (Precision)</span></div>
<div class="tile"><b>{s['f1']:.3f}</b><span>F1-score</span></div>
<div class="tile"><b>{pct(r['obfuscatedRecall'])}</b><span>จับได้แม้มิจฉาชีพเลี่ยงคำ</span></div>
<div class="tile"><b>{r['latencyMicrosP95']/1000:.2f} ms</b><span>เวลาตรวจ (P95)</span></div>
</div>

<h2>ผลการจำแนก (เกณฑ์ "น่าสงสัย" ขึ้นไป = เตือน)</h2>
<div class="cm"><div class="h"></div><div class="h">ระบบเตือน</div><div class="h">ระบบไม่เตือน</div>
<div class="h">มิจฉาชีพจริง</div><div class="tp"><strong>{s['tp']}</strong>จับได้</div><div class="fn"><strong>{s['fn']}</strong>หลุด</div>
<div class="h">ข้อความปกติ</div><div class="fp"><strong>{s['fp']}</strong>เตือนผิด</div><div class="tn"><strong>{s['tn']}</strong>ผ่านถูกต้อง</div></div>
<p class="note">ถ้าใช้เกณฑ์ "อันตราย" (ระดับที่แอปขึ้นป้ายแดง/วางสายได้) ความแม่นของการเตือนสูงถึง <b>{pct(d['precision'])}</b> (เตือนผิด {d['fp']} จาก {r['benign']}) แต่จับได้ {pct(d['recall'])} — จึงออกแบบให้ระดับ "น่าสงสัย" เป็นการเตือนเบา และ "อันตราย/บล็อก" เป็นการตัดสินที่เข้มงวด</p>

<h2>จับได้แยกตามประเภทกลโกง</h2>
{bars(r['perCategoryRecall'])}

<h2>ความทนต่อการเลี่ยงคำ</h2>
<p>นำข้อความมิจฉาชีพทั้ง {r['obfuscatedCount']} ข้อมาแปลงแบบที่พบใน SMS จริง: ใส่จุด/ขีดกลางคำ (บั.ญ.ชี, โ-อ-น), เว้นวรรคตัวอังกฤษ (O T P), ใช้เลขไทย, ใช้ "เเ" แทน "แ" และแทรกตัวอักษรล่องหน (zero-width) ระบบยังจับได้ <b>{pct(r['obfuscatedRecall'])}</b> (เทียบกับ {pct(s['recall'])} ก่อนแปลง)</p>

<h2>เตือนผิดแยกตามประเภทข้อความปกติ</h2>
<div class="scroll"><table><tr><th>ประเภท</th><th class="num">จำนวนที่เตือนผิด</th></tr>{fp_rows}</table></div>

<h2>วิเคราะห์ข้อผิดพลาดทุกข้อ</h2>
<div class="scroll"><table><tr><th>รหัส</th><th>ชนิด</th><th class="num">คะแนน</th><th>ข้อความ</th><th>สาเหตุ</th></tr>{miss_rows}</table></div>
<p class="note">ข้อสังเกต: ข้อผิดพลาดส่วนใหญ่มาจาก <b>การไม่เข้าใจคำปฏิเสธ</b> (ข่าวเตือนภัย "อย่าติดตั้งแอป") และ <b>คำทั่วไปที่ไม่อยู่ในกฎ</b> — เป็นโจทย์วิจัยต่อยอดด้วยโมเดลความหมาย (semantic model) ขนาดเล็กที่ทำงานในเครื่อง</p>

<h2>ระเบียบวิธี</h2>
<p>ชุดทดสอบเขียนแยกจากแม่แบบกลโกงที่ใช้สร้างระบบ ครอบคลุม {len(r['perCategoryRecall'])} ประเภทกลโกง และข้อความปกติ {len(r['perCategoryFalsePositive'])} ประเภท รวมข้อความยาก (SMS OTP จริง, บิลค่าไฟจริง, ลูกขอให้โอนค่าเทอม, ข่าวเตือนภัย) ไม่มีการปรับกฎหลังเห็นผล การประเมินรันอัตโนมัติทุกครั้งที่ build (<code>BenchmarkEvaluation</code>) และตั้งเกณฑ์ขั้นต่ำกันประสิทธิภาพถดถอย (Recall ≥ 90%, FPR ≤ 10%, ทนการเลี่ยงคำ ≥ 85%) เวลาตรวจวัดบน JVM ของเครื่องพัฒนา</p>
<p class="note"><b>ข้อจำกัด:</b> ข้อความทดสอบแต่งขึ้นโดยผู้พัฒนาตามรูปแบบกลโกงที่มีการเผยแพร่ต่อสาธารณะ ยังไม่ใช่ข้อความจากผู้เสียหายจริง และมีขนาด {r['datasetSize']} ข้อความ ขั้นต่อไปควรเก็บข้อมูลจริงร่วมกับหน่วยงาน (เช่น ศูนย์ AOC 1441) และประเมินโดยผู้ประเมินอิสระ ผลของการฟังระหว่างคุย (แปลงเสียงเป็นข้อความ) ยังไม่ได้ประเมินกับสายจริง</p>
</main></body></html>"""
out = root / "docs/evaluation/report.html"
out.write_text(page, encoding="utf-8")
print("wrote", out)
