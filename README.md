# 📱 TwinPane IDE — Mobile Web Development Hub

[![Platform](https://img.shields.io/badge/Platform-Android-green.svg)](https://developer.android.com)
[![Language](https://img.shields.io/badge/Language-Kotlin-blue.svg)](https://kotlinlang.org)
[![UI](https://img.shields.io/badge/Design-Material%203-purple.svg)](https://m3.material.io)
[![Font](https://img.shields.io/badge/Font-JetBrains%20Mono-orange.svg)](https://www.jetbrains.com)
[![License](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)

**TwinPane** is a full-featured HTML, CSS, and JavaScript IDE for Android. Write code on your phone in a real editor, preview it live with responsive viewports, debug it with built-in DevTools, and share it over Wi-Fi with a QR code. No laptop required.

## 📸 Screenshots

| Editor | Live Preview | DevTools |
|---|---|---|
| ![Editor](docs/screenshots/editor.png) | ![Preview](docs/screenshots/preview.png) | ![DevTools](docs/screenshots/devtools.png) |

## ⬇️ Download

Get the latest APK from the [Releases](../../releases) page.

---

## ✨ Features

### 🔤 Code Editor
- **JetBrains Mono** typography across the UI, editor canvas, line numbers, and dialogs
- Smart auto-closing for HTML tags and brackets, high-contrast syntax highlighting
- **Emmet** abbreviation expansion (e.g. `div.card>h2.title+p.desc`)
- One-tap code formatter, find & replace with regex support
- Tabbed editing with gutter line numbers and active-line highlight

### 👁️ Live Preview
- Sandboxed live preview with auto-run toggle (play / pause)
- **Responsive viewport switcher**: Desktop (100%), Mobile (375px), Tablet (600px)
- Draggable editor/preview splitter and fullscreen preview mode

### 🛠️ Built-in DevTools
- Live DOM inspector: tap any element to see markup, classes, and computed styles
- Interactive JavaScript console (REPL) running against the active page
- Code linter with line-specific error indicators
- Web storage & cookies inspector, Web API permission toggles

### 🎨 Design Helpers
- Visual CSS builders: Flexbox, Grid, Box Shadows, Glassmorphism, Gradients
- Material color picker that inserts hex codes at the cursor
- One-tap CDN injection: Bootstrap 5, Tailwind CSS, Font Awesome 6, Vue.js 3, Chart.js, and more
- Image asset manager: convert gallery images to Base64 data URIs

### 🌐 Share & Test
- Built-in Wi-Fi web server (port 8080) for local network testing
- **QR code generator**: scan from any device on the same Wi-Fi to open your page

### 🎨 Interface
- Dual themes: **Royal Dark IDE** and **Clean Light Studio**, with Material 3 DayNight harmonization
- Bootstrap-style action hub and touch bounce animations, 100% vector icons

---

## 🛡️ Security

TwinPane runs all previews in a hardened sandbox:

- Isolated, non-resolvable WebView origin with file and content access blocked
- Mixed content never allowed; no `addJavascriptInterface` (no reflection exploits)
- Navigation guard blocks untrusted external URLs and `intent://` redirects
- No secrets in the repo: keystores and local configs are git-ignored

---

## 🛠️ Tech Stack

- **Language**: Kotlin 1.9+
- **UI**: AndroidX, Material Design 3
- **Architecture**: Component-based modular design
- **Min SDK**: 24 (Android 7.0+) · **Target SDK**: 34+ (Android 14+)

---

## 🚀 Building & Running

```bash
git clone https://github.com/omorfarukhr/TwinPane.git
```

1. Open the project in **Android Studio** (Ladybug or newer)
2. Let Gradle sync dependencies
3. Connect a device or emulator (API 24+) and press **Run** (Shift + F10)

---

## 📜 License

Distributed under the MIT License. See [LICENSE](LICENSE) for details.
