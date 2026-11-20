# VoltCore — Smart BMS & Power Pack Monitor

[![Latest Release](https://img.shields.io/github/v/release/gianrufin/VoltCore?label=Latest%20Version&color=00E676&style=for-the-badge)](https://github.com/gianrufin/VoltCore/releases/latest)
[![Android](https://img.shields.io/badge/Android-7.0%2B%20(API%2024%2B)-0B0E12?style=for-the-badge&logo=android&logoColor=00E676)](https://github.com/gianrufin/VoltCore/releases/latest)

**VoltCore** is a minimalist, OLED-dark Smart BMS Android monitor designed for power packs and portable energy stations. It focuses on high-contrast, clutter-free real-time telemetry: State of Charge (SoC %), live wattage flow, hours remaining until empty or full charge, voltage, current, temperature, cycle counts, plugged devices, offline Room caching, local health alerts, and in-app GitHub Release updates.

---

## Download Latest APK

> **Current Packaged Version:** `v1.0.0` (`versionCode: 1`)

<p align="left">
  <a href="https://github.com/gianrufin/VoltCore/releases/latest/download/VoltCore-latest.apk">
    <img src="https://img.shields.io/badge/Download_APK-VoltCore_Latest-00E676?style=for-the-badge&logo=android&logoColor=000000" alt="Download Latest APK" />
  </a>
  &nbsp;
  <a href="https://github.com/gianrufin/VoltCore/releases/latest">
    <img src="https://img.shields.io/badge/View_Release_Notes-GitHub_Releases-131820?style=for-the-badge&logo=github&logoColor=FFFFFF" alt="View Release Notes" />
  </a>
</p>

- **Direct APK Download:** [`VoltCore-latest.apk`](https://github.com/gianrufin/VoltCore/releases/latest/download/VoltCore-latest.apk)
- **All Releases & Changelogs:** [`github.com/gianrufin/VoltCore/releases`](https://github.com/gianrufin/VoltCore/releases)

---

## Publishing Releases on GitHub

1. Push updates from AI Studio to `gianrufin/VoltCore`.
2. Open **Actions → Release APK → Run workflow** (or push a version tag such as `v1.0.0`).
3. GitHub Actions automatically builds the APK, runs unit & Robolectric tests, and publishes a Release containing `VoltCore-<version>.apk` and `VoltCore-latest.apk`.
4. Inside **VoltCore → Settings → GitHub APK Updater**, `gianrufin/VoltCore` is pre-configured so users can check for and install new releases directly from the app.

---

## Core Features

- **EcoFlow-Style Hero Power Card**: Oversized bold SoC % ring with dynamic charging (emerald) and discharging (cyan) states, live input/output wattage, remaining Wh, and calculated **hours remaining to empty** or **hours remaining to 100% full**.
- **Critical Battery Diagnostics**: Tabular monospace 2×2 telemetry grid for **Voltage (V)**, **Current (A)**, **Temperature (°C)**, and **Cycle Count** with State of Health (SoH %) and cell delta mV.
- **Plugged Devices Monitor**: Track connected loads across `USB-C PD`, `AC Inverter`, `12V DC`, and `Anderson` ports with live allocation tracking.
- **Multi-Protocol Smart BMS BLE + Test Pack Mode**: Real Bluetooth Low Energy GATT client for **JBD/Xiaoxiang**, **JK BMS**, and **Daly BMS**, paired with a toggleable **Simulated Test Pack** mode for bench testing without hardware.
- **Offline Room Cache & Trend Diagnostics**: Automatically caches telemetry in Room SQLite; switches seamlessly to **Offline Cached** mode when disconnected with Voltage, Current, and Temperature sparklines, 16S cell balance bars, and an alert log.
- **Local Push Notifications**: Configurable alerts for **Charge Completion %**, **Low Battery %**, and **High Temperature °C** via Android `NotificationChannel`.

---

## Building Locally

```bash
# Generate a debug keystore if not already present
keytool -genkeypair -v -keystore debug.keystore -storepass android -alias androiddebugkey -keypass android -keyalg RSA -keysize 2048 -validity 10000 -dname "CN=Android Debug,O=Android,C=US"

# Build the debug APK
gradle :app:assembleDebug

# Run unit and Robolectric tests
gradle :app:testDebugUnitTest
```

The compiled APK will be generated at `app/build/outputs/apk/debug/app-debug.apk`.
