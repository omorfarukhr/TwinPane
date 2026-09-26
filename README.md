# 📱 TwinPane IDE — Mobile Web Development Hub

[![Platform](https://img.shields.io/badge/Platform-Android-green.svg)](https://developer.android.com)
[![Language](https://img.shields.io/badge/Language-Kotlin-blue.svg)](https://kotlinlang.org)
[![UI](https://img.shields.io/badge/Design-Material%203-purple.svg)](https://m3.material.io)
[![Font](https://img.shields.io/badge/Font-JetBrains%20Mono-orange.svg)](https://www.jetbrains.com/lp/mono/)
[![Security](https://img.shields.io/badge/Security-Sandboxed%20WebView-red.svg)](#-security--cyber-attack-defense-model)

**TwinPane** is a modern, full-featured HTML, CSS, and JavaScript IDE and web development playground engineered for Android. Built with a developer-first mindset, it combines the official **JetBrains Mono typography**, a dual-theme engine (*Royal Dark IDE* & *Clean Light Studio*), real-time sandboxed preview, Chrome-style DevTools, responsive viewports, local Wi-Fi web server, and visual CSS tools into a sleek mobile workspace.

---

## ✨ Key Features & Architecture

### 🔤 1. JetBrains Mono Developer Typography
* **Official JetBrains Mono Font**: Integrated across the entire app UI, code editor canvas, line numbers, headers, cards, and dialogs for maximum code legibility.
* **Pro Code Editor Canvas**: Gutter line numbers, active cursor line highlight, left accent bar, and line gutter separators with `0.02f` letter spacing.

### 🎨 2. Dual Theme Engine (Royal Dark & Clean Light)
* **Royal Dark IDE Theme**: Deep Royal Neon Glassmorphism palette (`#0B0726` indigo, `#00E5FF` cyan, `#EC4899` pink) matched directly with TwinPane's official 3D logo.
* **Clean Light Studio Theme**: High-contrast, crisp white & slate theme (`#F8FAFC` bg, `#FFFFFF` surface, `#0F172A` text) designed for high visibility in direct sunlight.
* **Material 3 DayNight Harmonization**: Dynamic color harmonization across all MaterialAlertDialogs, inputs, menus, and bottom sheets with zero dark/light mix artifacts.

### 🚀 3. Bootstrap Action Hub & Touch Bounce Animations
* **Minimal Action Hub**: Redesigned Bootstrap-style action cards (`+ New`, `</> Sandbox`, `📁 Import`) with equal heights and subtle borders.
* **Touch Scale Bounce Animations**: Tactile press-down (`0.95f`) and spring-release animation on buttons, cards, and quick web tools for high-end UI responsiveness.
* **100% VectorDrawables**: Pure vector UI icons across all menus and dialogs (zero emojis) for a clean, pro-coder aesthetic.

### ⚡ 4. Dynamic Play / Pause (`▶` / `⏸`) Live Auto-Run Toggle
* **Live Status Indicator**: When Auto-Run is active, the top action bar toggles dynamically to a **Neon Cyan Pause (`⏸`)** button. Tapping it pauses live preview execution.
* **Manual Preview Mode**: When Auto-Run is paused, the action icon switches to **Play (`▶`)** for manual preview execution.
* **Pulse Animation**: Real-time scale pulse animation on preview updates to confirm code execution.

### 💻 5. Code Editor & Smart Development Tools
* **Smart Auto-Closing**: Automatic closing for HTML tags (`<div>` ➔ `</div>`) and auto-pairing brackets (`{`, `(`, `[`, `"`, `'`).
* **High-Contrast Syntax Highlighting**: Colorized HTML, CSS, and JS keywords adapting dynamically to Light and Dark modes.
* **Emmet Code Expansion**: Instant expansion of Emmet abbreviations (e.g., `div.card>h2.title+p.desc`, `ul>li*3`).
* **Code Formatter / Beautifier**: One-tap auto-indentation for HTML, CSS, and JS.
* **Find & Replace**: Search keywords or regex and replace text across editor tabs.

### 👁️ 6. Live Preview & Responsive Viewports
* **Responsive Viewport Switcher**: Toggle preview widths dynamically:
  * **Fluid Desktop** (`100%`)
  * **Mobile Viewport** (`375px`)
  * **Tablet Viewport** (`600px`)
* **Interactive Drag Splitter**: Resizable handle between editor and preview panes.
* **Fullscreen Mode**: Expand preview output to fill the entire screen.

### 🛠️ 7. Mobile DevTools & Web Debugging
* **Live DOM Element Inspector**: Tap elements in the live preview to inspect markup, classes, and computed styles.
* **Interactive JS Console Prompt**: Live REPL prompt (`> execute js...`) to run JavaScript directly against the active web page.
* **CodeLinter & Problems Finder**: Static syntax checker with line-specific error indicators and tab badges.
* **Web Storage & Cookies Inspector**: View and clear `localStorage`, `sessionStorage`, and cookies.
* **Web API Permissions Control**: Configurable sandbox toggles for Geolocation, Camera/Mic, and JS popups.

### 🎨 8. Visual Design & Asset Helpers
* **Visual CSS Generator**: Visual builders for Flexbox, Grid, Box Shadows, Glassmorphism, and Gradients.
* **Material Color Picker**: Visual color palette picker inserting Hex color codes at cursor.
* **CDN Library Injector**: One-tap injection of popular libraries (*Bootstrap 5, Tailwind CSS, Font Awesome 6, Animate.css, Google Fonts, jQuery 3.7, Vue.js 3, Chart.js, SweetAlert2*).
* **Image Asset Manager**: Convert gallery images directly to Base64 `data:image/...` URI tags.

### 🌐 9. Local Server & Sharing
* **Live Wi-Fi Web Server**: Embedded HTTP server running on port `8080` for local network testing.
* **QR Code Sharing Generator**: Automatically generates a QR code to test web pages on other devices on the same Wi-Fi.

---

## 🛡️ Security & Cyber Attack Defense Model

TwinPane is engineered with **strict multi-layer security controls** to protect device data and defend against web-based attacks:

1. **Sandboxed WebView Origin Isolation**:
   * Uses an isolated, non-resolvable origin (`https://sandbox.twinpane.invalid/`).
2. **File System & Content Access Blocked**:
   * `allowFileAccess = false` and `allowContentAccess = false` prevent arbitrary JavaScript from reading local phone storage or internal ContentProviders.
3. **Mixed Content Policy**:
   * `mixedContentMode = MIXED_CONTENT_NEVER_ALLOW` blocks insecure HTTP scripts from running inside secure contexts.
4. **JavaScript Interface Protection**:
   * `addJavascriptInterface` is strictly avoided to eliminate Java Reflection exploit vectors.
5. **URL Navigation Guard**:
   * `shouldOverrideUrlLoading` blocks untrusted external navigations and `intent://` scheme redirects.
6. **No Secret Keys in Repository**:
   * Strict `.gitignore` rules prevent private keys, signing keystores (`*.jks`), and local configuration files (`local.properties`) from leaking to version control.

---

## 🛠️ Tech Stack & Architecture

* **Language**: Kotlin 1.9+
* **Font**: JetBrains Mono (`@font/jetbrains_mono`)
* **UI Framework**: AndroidX, Material Design 3 (`com.google.android.material`)
* **Components**: Custom `AppCompatEditText` (Code Editor), `WebView` (Sandbox Container), `HttpServer` (Wi-Fi Server)
* **Architecture Pattern**: Component-based modular design with custom dialog stack navigation
* **Min SDK**: API 24 (Android 7.0+)
* **Target SDK**: API 34+ (Android 14+)

---

## 🚀 Building & Running

1. Clone this repository:
   ```bash
   git clone https://github.com/omorfarukhr/TwinPane.git
   ```
2. Open the project in **Android Studio (Ladybug or newer)**.
3. Allow Gradle to sync dependencies.
4. Connect an Android device or emulator (API 24+) and click **Run (Shift + F10)**.

---

## 📜 License

Distributed under the MIT License. See `LICENSE` for more information.
