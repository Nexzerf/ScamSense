# Security review

| Area | Control |
|---|---|
| Permissions | **None** requested. No `INTERNET`, `READ_SMS`, `RECORD_AUDIO`, `SEND_SMS`, `CALL_PHONE`. |
| Data at rest | History & settings encrypted with AES-256-GCM; key generated in **Android Keystore** (hardware-backed where available, non-exportable). Stored in `noBackupFilesDir`, written atomically (`AtomicFile`). Corrupt/undecryptable data is discarded, never crashes the app. |
| Backup / transfer | `allowBackup=false`, `fullBackupContent` + `dataExtractionRules` exclude every domain (cloud + device-to-device). |
| Data minimisation | Stored/shared previews mask digit runs ≥ 6 (account/ID/phone numbers keep only the last 3 digits). History can be disabled or wiped. |
| Exported components | Only `MainActivity` (launcher, `SEND text/plain`, `PROCESS_TEXT`). Incoming extras are read defensively (`RuntimeException` caught), type-checked, control chars stripped, capped at 5,000 chars. Tested with 1.2M-char and malformed intents. |
| Task hijacking (StrandHogg) | `taskAffinity=""` on the activity. |
| Links in checked content | Never rendered as clickable; never fetched or opened. |
| Outbound actions | SMS/dial/share open the user's own apps with pre-filled content; the user confirms. |
| Network | `usesCleartextTraffic=false` (defence in depth; the app has no network permission at all). |
| DoS / ReDoS | All parsing is linear-time hand-written scanning; fuzzy matching window bounded; input capped. Pathological-input test asserts < 2 s. |
| Release hardening | R8 obfuscation/minification, `debuggable=false`, verbose `Log` calls stripped, source file names hidden, no dependency-info blob, signing keys git-ignored (`*.jks`, `keystore.properties`). |
| Supply chain | Only AndroidX/Jetpack + Kotlin stdlib from Google Maven / Maven Central. Font: IBM Plex Sans Thai (SIL OFL, licence in `licenses/`). |

## Known limitations / next steps
- Detection is heuristic; new scam scripts need knowledge-base updates (ship via app update, or add an opt-in signed rule feed).
- No real-time SMS interception (would require `RECEIVE_SMS`/notification access, which Google Play restricts); the share/process-text flow covers this without permissions.
- Consider a TFLite multilingual sentence encoder behind `ScamAnalyzer` for deeper semantic matching.
