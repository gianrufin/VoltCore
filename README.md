# VoltCore — Smart BMS & Power Pack Monitor

[![Latest Release](https://img.shields.io/github/v/release/YOUR_GITHUB_USERNAME/YOUR_REPO_NAME?label=Latest%20Version&color=00E676&style=for-the-badge)](https://github.com/YOUR_GITHUB_USERNAME/YOUR_REPO_NAME/releases/latest)
[![Android](https://img.shields.io/badge/Android-7.0%2B%20(API%2024%2B)-0B0E12?style=for-the-badge&logo=android&logoColor=00E676)](https://github.com/YOUR_GITHUB_USERNAME/YOUR_REPO_NAME/releases/latest)

**VoltCore** is a minimalist, OLED-dark Smart BMS Android monitor designed for power packs and portable energy stations. It focuses on high-contrast, clutter-free real-time telemetry: State of Charge (SoC %), live wattage flow, hours remaining until empty or full charge, voltage, current, temperature, cycle counts, plugged devices, offline Room caching, local health alerts, and in-app GitHub Release updates.

---

## Download Latest APK

> **Current Packaged Version:** `v1.0.0` (`versionCode: 1`)

<p align="left">
  <a href="https://github.com/YOUR_GITHUB_USERNAME/YOUR_REPO_NAME/releases/latest/download/VoltCore-latest.apk">
    <img src="https://img.shields.io/badge/Download_APK-VoltCore_Latest-00E676?style=for-the-badge&logo=android&logoColor=000000" alt="Download Latest APK" />
  </a>
  &nbsp;
  <a href="https://github.com/YOUR_GITHUB_USERNAME/YOUR_REPO_NAME/releases/latest">
    <img src="https://img.shields.io/badge/View_Release_Notes-GitHub_Releases-131820?style=for-the-badge&logo=github&logoColor=FFFFFF" alt="View Release Notes" />
  </a>
</p>

- **Direct APK Download:** [`VoltCore-latest.apk`](https://github.com/YOUR_GITHUB_USERNAME/YOUR_REPO_NAME/releases/latest/download/VoltCore-latest.apk)
- **All Releases & Changelogs:** [`github.com/YOUR_GITHUB_USERNAME/YOUR_REPO_NAME/releases`](https://github.com/YOUR_GITHUB_USERNAME/YOUR_REPO_NAME/releases)

*(Replace `YOUR_GITHUB_USERNAME/YOUR_REPO_NAME` with your GitHub repository slug after pushing from AI Studio.)*

---

## How to Publish Your Repo & APK Release on GitHub

Because AI Studio runs in an isolated cloud workspace without access to your personal GitHub credentials, your repository and APK release are published from your GitHub account in two quick steps:

1. **Push This Project to GitHub**
   - In Google AI Studio, open the **Settings / Export** menu and choose **Push to GitHub** to create or update your repository.
   - Replace `YOUR_GITHUB_USERNAME/YOUR_REPO_NAME` in this `README.md` with your actual GitHub `owner/repo` slug.
2. **Generate & Publish the APK Release Automatically**
   - This repository includes a ready-to-run GitHub Actions workflow (`.github/workflows/release.yml`).
   - Either push a version tag:
     ```bash
     git tag v1.0.0
     git push origin v1.0.0
     ```
   - **Or** trigger it directly in your browser on GitHub: **Actions → Release APK → Run workflow** (enter e.g. `v1.0.0`).
   - GitHub Actions will compile the APK and publish a Release containing both `VoltCore-v1.0.0.apk` and `VoltCore-latest.apk` so the download button above and the in-app updater work immediately.
3. **Enable In-App Updates**
   - Open **VoltCore → Settings → GitHub APK Updater**, enter your `owner/repo`, and tap **Check GitHub Releases**.

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

If you want to build the APK manually on your machine (requires Android SDK and Gradle 9.3.1+):

```bash
# Decode the debug keystore included for local builds (if not already present)
base64 -d debug.keystore.base64 > debug.keystore

# Build the debug APK
gradle :app:assembleDebug

# Run unit and Robolectric tests
gradle :app:testDebugUnitTest
```

The compiled APK will be generated at `app/build/outputs/apk/debug/app-debug.apk`.
