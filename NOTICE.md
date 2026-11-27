# ScamSense — licences and attributions

ScamSense is free software, licensed under the **GNU General Public License v3.0**
(see [LICENSE](LICENSE)), including the additional Section 7 terms that apply to the
code derived from ShizuCallRecorder.

## Derived code

The live call-audio helper in `app/src/main/java/com/scamsense/app/livecall/shell/`
is adapted from **CallVault** (https://github.com/madkongo/CallVault), which is a fork of
**ShizuCallRecorder** (https://github.com/kitsumed/ShizuCallRecorder),
Copyright (C) kitsumed (Med). This is a modified version, not endorsed by or affiliated
with either project.

> This software is a fork of ShizuCallRecorder, available at https://github.com/kitsumed/ShizuCallRecorder.

ScamSense uses its own name, package id (`com.scamsense.app`) and branding, and does not
use the names or logos of those projects for publicity.

## Third-party components

| Component | Licence | Use |
|---|---|---|
| [whisper.cpp](https://github.com/ggml-org/whisper.cpp) v1.9.3 (`third_party/whisper.cpp`) | MIT | On-device speech-to-text |
| Whisper model weights (downloaded at runtime from Hugging Face) | MIT (OpenAI Whisper) | Thai speech recognition |
| [Shizuku API](https://github.com/RikkaApps/Shizuku-API) | Apache-2.0 | Shell-privilege helper process |
| [IBM Plex Sans Thai](https://github.com/IBM/plex) | SIL OFL 1.1 (`licenses/`) | App font |
| AndroidX / Jetpack Compose, Kotlin, Ktor, kotlinx.serialization | Apache-2.0 | App & server |
| sqlite-jdbc | Apache-2.0 | Server storage |
