# Architecture

```
┌──────────────────────────── app (single module, single Activity) ────────────────────────────┐
│                                                                                              │
│  MainActivity ── core-splashscreen ──► ScamSenseRoot (Compose)                               │
│     │  SEND / PROCESS_TEXT intents        ├─ LoadingScreen  (while startup runs)             │
│     ▼  (sanitised, 5,000-char cap)        └─ NavHost: Home · Check · Transaction · Result    │
│  MainViewModel  (StateFlow)                            History · Settings                     │
│     │  analyze() on Dispatchers.Default                                                      │
│     ▼                                                                                        │
│  ┌──────────── engine/ (pure Kotlin, no Android deps) ────────────┐   data/                  │
│  │ TextNormalizer → TextContext (normalized / compact / tokens)   │   SettingsRepository     │
│  │ KeywordEngine   19 rule groups, exact + 1-edit fuzzy (Sellers) │   HistoryRepository      │
│  │ SimilarityEngine char 2–3-gram TF-IDF, cosine vs 35 templates  │        │                 │
│  │ UrlAnalyzer     brand spoof, shortener, IDN, .apk, IP, TLD …   │   SecureFileStore        │
│  │ PhoneAnalyzer   intl / +697 / premium / official / reported    │   AES-256-GCM, key in    │
│  │ ScamAnalyzer    weights + combos + block rules → RiskResult    │   AndroidKeyStore        │
│  └────────────────────────────────────────────────────────────────┘                          │
└──────────────────────────────────────────────────────────────────────────────────────────────┘
```

## Key decisions

| Decision | Why |
|---|---|
| **On-device only, no INTERNET permission** | Messages elders receive are highly sensitive (bank info, IDs). Zero network = zero data-leak surface, works offline, no server cost. |
| **Character n-gram TF-IDF instead of Word2Vec/Sentence2Vec** | Thai has no spaces, so word embeddings need a segmenter + tens of MB of model. Char n-grams capture paraphrase/typos, need no model file, and score a message in < 5 ms. The engine is behind `ScamAnalyzer`, so a TFLite sentence-encoder can be swapped in later. |
| **Rules + similarity + combinations** | Rules give explainable reasons ("พบคำว่า OTP"); similarity catches re-worded scripts; combinations (e.g. OTP + urgency) drive the "block" verdict with few false positives. |
| **Pure-Kotlin engine** | 44 engine tests run on the JVM in seconds; no emulator needed. |
| **Manual DI (`AppContainer`)** | Tiny object graph; Hilt would add build time, APK size and startup cost. |
| **Single ViewModel + Navigation-Compose** | Results are passed via `StateFlow`, not nav arguments (no size limits, nothing sensitive in the back stack). |
| **Intents instead of permissions** | SMS to caregiver (`ACTION_SENDTO`), dialing (`ACTION_DIAL`), speech (`RecognizerIntent`) → the user always confirms, and the app needs no SEND_SMS/CALL_PHONE/RECORD_AUDIO. |
| **minSdk 23** | ~99% of active Android devices; required for Keystore AES-GCM. |

## Scoring

`score = clamp(Σ signal weights, 0, 100)`; levels: 0–29 low, 30–59 suspicious, 60–100 dangerous.
"Block" (score forced ≥ 85, red STOP banner) when: credentials request + (urgency | link | authority | money | threat | install),
"safe account" transfer, remote-control apps, `.apk` links, brand-spoof or homograph domains, +697/+698 numbers,
user-reported numbers, or user-defined block words. A genuine bank OTP SMS ("ห้ามเปิดเผยรหัสนี้" + code, no link) is recognised and not flagged.

## Performance

- Startup: settings + history decrypted on IO, engine warmed (index built, JIT) on Default while the branded loading screen shows (~1.1 s minimum for a smooth hand-off from the system splash).
- Analysis: typically 1–5 ms; input capped at 5,000 chars; fuzzy matching bounded to 2,000 chars; all parsing linear-time (no backtracking regex → no ReDoS).
- Release: R8 full mode + resource shrinking, locale filter (th/en), no dependency-metadata blob.

## Testing

| Suite | What |
|---|---|
| `ScamAnalyzerTest` (36) | scams vs benign, obfuscation, fuzzy, links, phones, transactions, huge/pathological input timing, masking |
| `TextNormalizerTest` (8) | normalisation primitives |
| `ScreenRenderTest` (18) | renders every screen (light/dark/XL text) with native graphics; writes screenshots to `app/build/screenshots/` |
| `AppFlowTest` (4) | real `MainActivity`: startup → check → result → history; input errors; share-intent auto-check; hostile intents |
