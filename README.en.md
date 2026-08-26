<p align="center">
  <img src="assets/logo.png" width="120" height="120" alt="Aovo Control Logo" />
</p>

<h1 align="center">Aovo Control</h1>

<p align="center">
  <b>Modern, lightweight and open-source Android app for managing and tuning Aovo, ViCont, Samik, Benben, and ZYD electric scooters.</b>
</p>

<p align="center">
  <a href="https://github.com/YouRooni/AovoControl/releases"><img src="https://img.shields.io/github/v/release/YouRooni/AovoControl?style=for-the-badge&color=2979FF&label=Release" alt="Release" /></a>
  <a href="https://4pda.to/forum/index.php?showtopic=1125489"><img src="https://img.shields.io/badge/4PDA-Topic-brightgreen?style=for-the-badge&logo=android" alt="4PDA" /></a>
  <a href="https://t.me/YouRooni"><img src="https://img.shields.io/badge/Telegram-@YouRooni-2CA5E0?style=for-the-badge&logo=telegram" alt="Telegram" /></a>
  <a href="https://github.com/YouRooni/AovoControl/blob/master/LICENSE"><img src="https://img.shields.io/badge/License-GPL--3.0-blue?style=for-the-badge" alt="License" /></a>
</p>

<p align="center">
  <b><a href="README.md">Русский</a></b> | <b><a href="README.en.md">English</a></b>
</p>

---

## 🚀 Overview

**Aovo Control** is an open-source companion app for electric scooters, engineered as a clean, fast, and feature-rich replacement for standard OEM apps (*AovoPro*, *ViCont*, *Benben*, etc.).

It communicates **directly via Bluetooth Low Energy (BLE)** without forced logins, tracking, bloated ads, or cloud dependency.

---

## ✨ Features

### 📊 Dashboard & Live Telemetry
* **Smooth speedometer** with real-time speed, power, and acceleration dynamics.
* **Battery diagnostics:** State of charge (%), precise voltage ($V$), and live current draw ($A$).
* **Odometer & Trip:** Accurate per-ride trip counters and lifetime distance tracking.
* **One-tap controls:** Headlight toggle, motor lock, cruise control, and gear switching.

### ⚙️ Ride Configuration & Tuning
* **Individual gear speed limits:** Customize top speed thresholds for Eco, Drive, Sport, Walk mode, and 5th gear.
* **Zero-Start toggle:** Enable/disable instant throttle without kick-pushing.
* **RGB Deck Lighting (ViCont):** Full color picker and 5 ambient lighting modes (cycling, breathing, static).
* **Module management:** Voice prompt volume, Bluetooth device renaming, and PIN code protection.

### 🧪 Expert Mode & Calibration
* **Direct controller register editing:** Max discharge current, regenerative braking current, low-voltage cutoff, wheel diameter, motor pole pairs, and PWM frequency.
* **ViCont Engineering Menu:** Deep EEPROM access for display and motor controller settings.
* **Motor Calibration:** Hall sensor coefficients and phase order adjustment.
* **Raw HEX Command Terminal:** Send arbitrary custom byte payloads for protocol inspection.

### 🔄 Firmware Management
* **OTA Online Update Checker:** Verify and fetch official display and motor controller binaries from Vicont Cloud.
* **Local Flashing:** Flash custom or stock `.bin` firmware files straight from device storage.
* **Riding Profiles:** Export, import, and hot-swap scooter parameter profiles.

### 🎨 Modern Material 3 Expressive UI
* Fluid spring physics and seamless expanding/collapsing container animations.
* Full dynamic theming (**Material You / Monet**) and deep **AMOLED Dark Theme**.
* Rich haptic feedback engine and interactive Easter Egg.

---

## 🛴 Supported Scooters & Controllers

* **Aovo / AovoPro** (ES80, Pro, and compatible M365 clones)
* **ViCont** (BD-MAX, 032＆039ZQ, BD1 boards)
* **Samik / Benben**
* **ZYD / YF** BLE controllers

---

## 📥 Installation

1. Download the latest APK from **[GitHub Releases](https://github.com/YouRooni/AovoControl/releases)** or **[4PDA](https://4pda.to/forum/index.php?showtopic=1125489)**.
2. Install on Android 8.0 (API 26) or newer.
3. Grant Bluetooth / Nearby Devices permissions and enjoy!

---

## 🛠 Tech Stack

* **Language:** Kotlin
* **UI Framework:** Jetpack Compose + Material 3 Expressive
* **Architecture:** StateFlow, ViewModel, Coroutines
* **Communication:** Android BLE GATT, Lightweight HTTP Client

---

## 👨‍💻 Author & Credits

* **Developer:** Danil ([@YouRooni](https://t.me/YouRooni))
* **4PDA Community:** [Aovo Control](https://4pda.to/forum/index.php?showtopic=1125489)
* **Protocol Research:** [vova7878 (Vladimir)](https://github.com/vova7878) — for ViCont protocol reverse-engineering
* **Donate & Support:** [payRooni.t.me](https://t.me/payRooni) 💖

---

## 📄 License

Distributed under the **GNU General Public License v3.0 (GPLv3)**. All derivative works and forks must remain open-source under the same license with author attribution preserved.
