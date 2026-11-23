(function () {
  const cfg = window.VOLTCORE_CONFIG || {
    githubOwner: "gianrufin",
    githubRepo: "VoltCore",
    appVersion: "1.2.0",
    gaMeasurementId: "G-XXXXXXXXXX",
    pagesUrl: "https://gianrufin.github.io/VoltCore/"
  };

  // 1. Google Analytics 4 Initialization & Safe Event Tracker
  const hasGaId = typeof cfg.gaMeasurementId === "string" &&
    cfg.gaMeasurementId.startsWith("G-") &&
    cfg.gaMeasurementId !== "G-XXXXXXXXXX";

  window.dataLayer = window.dataLayer || [];
  function gtag() {
    window.dataLayer.push(arguments);
  }

  if (hasGaId) {
    const script = document.createElement("script");
    script.async = true;
    script.src = `https://www.googletagmanager.com/gtag/js?id=${encodeURIComponent(cfg.gaMeasurementId)}`;
    document.head.appendChild(script);
    gtag("js", new Date());
    gtag("config", cfg.gaMeasurementId, { anonymize_ip: true });
  }

  function trackEvent(eventName, params) {
    if (hasGaId) {
      gtag("event", eventName, params || {});
    }
  }

  // 2. Gyro-Reactive Liquid Juice Battery Canvas with Green-to-Red Gradient
  const canvas = document.getElementById("hero-battery-canvas");
  const ctx = canvas ? canvas.getContext("2d") : null;
  const socSlider = document.getElementById("soc-slider");
  const socValueEl = document.getElementById("demo-soc-value");
  const socStepEl = document.getElementById("demo-soc-step");
  const whLabelEl = document.getElementById("demo-wh-label");
  const stateDotEl = document.getElementById("demo-state-dot");
  const modeLabelEl = document.getElementById("demo-mode-label");
  const powerLabelEl = document.getElementById("demo-power-label");
  const powerValueEl = document.getElementById("demo-power-value");
  const etaLabelEl = document.getElementById("demo-eta-label");
  const etaReadoutEl = document.getElementById("demo-eta-readout");
  const passthroughBarEl = document.getElementById("demo-passthrough");
  const grossInEl = document.getElementById("demo-gross-in");
  const packNetEl = document.getElementById("demo-pack-net");
  const loadOutEl = document.getElementById("demo-load-out");
  const voltageEl = document.getElementById("demo-voltage");
  const currentEl = document.getElementById("demo-current");
  const currentUnitEl = document.getElementById("demo-current-unit");
  const currentSubEl = document.getElementById("demo-current-sub");

  const PACK_WH = 3584;
  let targetSoc = 99.2;
  let animatedSoc = 99.2;
  let activeMode = "discharging";
  let rafId = null;
  let tiltOffset = 0;
  let smoothedTilt = 0;
  let wavePhase = 0;
  let lastFrameTs = 0;

  // Gyroscope / Device Orientation & Mouse Tilt
  window.addEventListener("deviceorientation", (e) => {
    if (e.gamma != null) {
      tiltOffset = Math.max(-22, Math.min(22, e.gamma)) * 0.032;
      scheduleCanvasDraw();
    }
  });

  window.addEventListener("mousemove", (e) => {
    const cx = window.innerWidth / 2;
    tiltOffset = ((e.clientX - cx) / cx) * 0.28;
    scheduleCanvasDraw();
  });

  function getJuiceColor(soc) {
    if (soc >= 50) {
      // 50% (Amber) to 100% (Emerald Green)
      const t = (soc - 50) / 50;
      const r = Math.round(245 + (46 - 245) * t);
      const g = Math.round(158 + (230 - 158) * t);
      const b = Math.round(11 + (154 - 11) * t);
      return { r, g, b, css: `rgb(${r}, ${g}, ${b})` };
    } else {
      // 0% (Red) to 50% (Amber)
      const t = soc / 50;
      const r = Math.round(239 + (245 - 239) * t);
      const g = Math.round(68 + (158 - 68) * t);
      const b = Math.round(68 + (11 - 68) * t);
      return { r, g, b, css: `rgb(${r}, ${g}, ${b})` };
    }
  }

  function getStateDotClass() {
    if (targetSoc <= 15) return "status-dot red";
    if (activeMode === "discharging") return "status-dot blue";
    return "status-dot mint";
  }

  function formatHours(h) {
    if (!isFinite(h) || h <= 0) return "0m";
    const mins = Math.max(1, Math.round(h * 60));
    const hh = Math.floor(mins / 60);
    const mm = mins % 60;
    if (hh > 0 && mm > 0) return `${hh}h ${String(mm).padStart(2, "0")}m`;
    if (hh > 0) return `${hh}h`;
    return `${mm}m`;
  }

  function syncDemoReadouts() {
    const remWh = Math.floor((targetSoc / 100) * PACK_WH);
    if (socValueEl) socValueEl.textContent = targetSoc.toFixed(1);
    if (socStepEl) socStepEl.textContent = `${targetSoc.toFixed(1)}%`;
    if (whLabelEl) whLabelEl.textContent = `${remWh} / ${PACK_WH} Wh`;
    if (stateDotEl) stateDotEl.className = getStateDotClass();

    if (activeMode === "discharging") {
      if (modeLabelEl) modeLabelEl.textContent = "Discharging";
      if (powerLabelEl) powerLabelEl.textContent = "Output";
      if (powerValueEl) {
        powerValueEl.textContent = "46.2 W";
        powerValueEl.className = "summary-val mono text-blue";
      }
      if (etaLabelEl) etaLabelEl.textContent = "Est. remaining";
      if (etaReadoutEl) {
        const hrs = remWh / 46.2;
        etaReadoutEl.textContent = formatHours(hrs);
      }
      if (passthroughBarEl) passthroughBarEl.hidden = true;
      if (voltageEl) voltageEl.textContent = "13.20";
      if (currentEl) currentEl.textContent = "-3.50";
      if (currentUnitEl) currentUnitEl.className = "metric-unit text-blue";
      if (currentSubEl) currentSubEl.textContent = "Load draw";
    } else if (activeMode === "charging") {
      if (modeLabelEl) modeLabelEl.textContent = "Charging";
      if (powerLabelEl) powerLabelEl.textContent = "Input";
      if (powerValueEl) {
        powerValueEl.textContent = "+340.0 W";
        powerValueEl.className = "summary-val mono text-mint";
      }
      if (etaLabelEl) etaLabelEl.textContent = "To full";
      if (etaReadoutEl) {
        const missingWh = Math.max(0, PACK_WH - remWh);
        etaReadoutEl.textContent = missingWh <= 1 ? "Full" : formatHours(missingWh / 340);
      }
      if (passthroughBarEl) passthroughBarEl.hidden = true;
      if (voltageEl) voltageEl.textContent = "13.42";
      if (currentEl) currentEl.textContent = "+25.33";
      if (currentUnitEl) currentUnitEl.className = "metric-unit text-mint";
      if (currentSubEl) currentSubEl.textContent = "Charging flow";
    } else {
      if (modeLabelEl) modeLabelEl.textContent = "Pass-through";
      if (powerLabelEl) powerLabelEl.textContent = "Input";
      if (powerValueEl) {
        powerValueEl.textContent = "+340.0 W";
        powerValueEl.className = "summary-val mono text-mint";
      }
      if (etaLabelEl) etaLabelEl.textContent = "To full";
      if (etaReadoutEl) {
        const missingWh = Math.max(0, PACK_WH - remWh);
        etaReadoutEl.textContent = missingWh <= 1 ? "Full" : formatHours(missingWh / 340);
      }
      if (passthroughBarEl) passthroughBarEl.hidden = false;
      if (grossInEl) grossInEl.textContent = "+400.0 W";
      if (packNetEl) packNetEl.textContent = "+340.0 W";
      if (loadOutEl) loadOutEl.textContent = "60.0 W";
      if (voltageEl) voltageEl.textContent = "13.42";
      if (currentEl) currentEl.textContent = "+25.33";
      if (currentUnitEl) currentUnitEl.className = "metric-unit text-mint";
      if (currentSubEl) currentSubEl.textContent = "Charging flow";
    }
  }

  function drawBatteryCanvas(timestamp) {
    if (!ctx || !canvas) return;
    const now = typeof timestamp === "number" ? timestamp : performance.now();
    const dt = lastFrameTs ? Math.min(0.05, (now - lastFrameTs) / 1000) : 0.016;
    lastFrameTs = now;

    const dpr = window.devicePixelRatio || 1;
    const displaySize = 216;
    if (canvas.width !== displaySize * dpr) {
      canvas.width = displaySize * dpr;
      canvas.height = displaySize * dpr;
    }

    ctx.save();
    ctx.scale(dpr, dpr);
    ctx.clearRect(0, 0, displaySize, displaySize);

    const diff = targetSoc - animatedSoc;
    if (Math.abs(diff) < 0.05) {
      animatedSoc = targetSoc;
    } else {
      animatedSoc += diff * Math.min(1, dt * 10);
    }
    smoothedTilt += (tiltOffset - smoothedTilt) * Math.min(1, dt * 8);

    const progress = Math.max(0, Math.min(1, animatedSoc / 100));
    const cx = displaySize / 2;
    const cy = displaySize / 2;
    const strokeW = 11;
    const outerRadius = (displaySize / 2) - strokeW;
    const liquidRadius = outerRadius - (strokeW / 2) - 2;
    const juice = getJuiceColor(animatedSoc);

    // Outer track ring
    ctx.beginPath();
    ctx.arc(cx, cy, outerRadius, 0, Math.PI * 2);
    ctx.strokeStyle = "#151820";
    ctx.lineWidth = strokeW;
    ctx.stroke();

    // Inner cavity
    ctx.beginPath();
    ctx.arc(cx, cy, liquidRadius, 0, Math.PI * 2);
    ctx.fillStyle = "#000000";
    ctx.fill();

    // Clipped dual-layer 60fps liquid wave effect
    ctx.save();
    ctx.beginPath();
    ctx.arc(cx, cy, liquidRadius, 0, Math.PI * 2);
    ctx.clip();

    const liquidTopY = cy + liquidRadius - (liquidRadius * 2 * progress);
    const waveAmp = (progress <= 0.02 || progress >= 0.985) ? 2 : 5.5;
    const steps = 36;
    const startX = cx - liquidRadius - 12;
    const spanW = (liquidRadius * 2) + 24;

    // Back wave
    ctx.beginPath();
    ctx.moveTo(startX, cy + liquidRadius + 24);
    for (let i = 0; i <= steps; i++) {
      const x = startX + (i / steps) * spanW;
      const dx = x - cx;
      const angle = (i / steps) * Math.PI * 2 - wavePhase * 0.78;
      const y = liquidTopY + (dx * smoothedTilt) + Math.sin(angle) * (waveAmp * 0.8);
      ctx.lineTo(x, y);
    }
    ctx.lineTo(startX + spanW, cy + liquidRadius + 24);
    ctx.closePath();
    ctx.fillStyle = `rgba(${juice.r}, ${juice.g}, ${juice.b}, 0.42)`;
    ctx.fill();

    // Front wave
    ctx.beginPath();
    ctx.moveTo(startX, cy + liquidRadius + 24);
    for (let i = 0; i <= steps; i++) {
      const x = startX + (i / steps) * spanW;
      const dx = x - cx;
      const angle = (i / steps) * Math.PI * 2.2 + wavePhase;
      const y = liquidTopY + (dx * smoothedTilt) + Math.sin(angle) * waveAmp;
      ctx.lineTo(x, y);
    }
    ctx.lineTo(startX + spanW, cy + liquidRadius + 24);
    ctx.closePath();
    ctx.fillStyle = `rgba(${juice.r}, ${juice.g}, ${juice.b}, 0.92)`;
    ctx.fill();
    ctx.restore();

    // Outer active progress stroke arc
    ctx.beginPath();
    ctx.arc(cx, cy, outerRadius, -Math.PI / 2, -Math.PI / 2 + Math.PI * 2 * progress);
    ctx.strokeStyle = juice.css;
    ctx.lineWidth = strokeW;
    ctx.lineCap = "round";
    ctx.stroke();

    ctx.restore();

    wavePhase = (wavePhase + dt * 2.9) % (Math.PI * 2);
    rafId = requestAnimationFrame(drawBatteryCanvas);
  }

  function scheduleCanvasDraw() {
    if (rafId === null) {
      rafId = requestAnimationFrame(drawBatteryCanvas);
    }
  }

  if (socSlider) {
    socSlider.addEventListener("input", (e) => {
      targetSoc = parseFloat(e.target.value);
      syncDemoReadouts();
      scheduleCanvasDraw();
    });
    socSlider.addEventListener("change", () => {
      trackEvent("battery_demo_interact", { soc_percent: targetSoc, mode: activeMode });
    });
  }

  document.querySelectorAll(".mode-btn").forEach((btn) => {
    btn.addEventListener("click", () => {
      document.querySelectorAll(".mode-btn").forEach((b) => b.classList.remove("active"));
      btn.classList.add("active");
      activeMode = btn.dataset.mode || "discharging";
      syncDemoReadouts();
      scheduleCanvasDraw();
      trackEvent("battery_demo_interact", { soc_percent: targetSoc, mode: activeMode });
    });
  });

  syncDemoReadouts();
  drawBatteryCanvas();

  // 3. Screenshot Tabs Interaction
  document.querySelectorAll(".screen-tab").forEach((tab) => {
    tab.addEventListener("click", () => {
      const target = tab.dataset.screen;
      document.querySelectorAll(".screen-tab").forEach((t) => {
        t.classList.toggle("active", t === tab);
        t.setAttribute("aria-selected", t === tab ? "true" : "false");
      });
      document.querySelectorAll(".screen-card").forEach((card) => {
        card.classList.toggle("active", card.dataset.screenCard === target);
      });
      trackEvent("screen_sample_view", { screen_name: target });
    });
  });

  // 4. App Version Sync & Live GitHub Releases API Monitor + "New Version Available" Banner
  function normalizeTag(ver) {
    const cleaned = String(ver || "").trim();
    if (!cleaned) return "v1.2.0";
    return cleaned.startsWith("v") ? cleaned : `v${cleaned}`;
  }

  function parseSemver(ver) {
    const raw = String(ver || "").replace(/^v/i, "").split("-")[0];
    return raw.split(".").map((n) => parseInt(n, 10) || 0);
  }

  function compareSemver(a, b) {
    const pa = parseSemver(a);
    const pb = parseSemver(b);
    const len = Math.max(pa.length, pb.length, 3);
    for (let i = 0; i < len; i++) {
      const va = pa[i] || 0;
      const vb = pb[i] || 0;
      if (va !== vb) return va - vb;
    }
    return 0;
  }

  function updateVersionLabels(tag) {
    const formattedTag = normalizeTag(tag);
    [
      "nav-version-pill",
      "hero-latest-tag",
      "screens-version-label",
      "settings-mock-version",
      "settings-card-version",
      "update-banner-version"
    ].forEach((id) => {
      const el = document.getElementById(id);
      if (el) el.textContent = formattedTag;
    });
  }

  let currentAppTag = normalizeTag(cfg.appVersion || "1.2.0");
  updateVersionLabels(currentAppTag);

  const CACHE_KEY = `voltcore_releases_${cfg.githubOwner}_${cfg.githubRepo}`;
  let latestMeta = {
    tag: currentAppTag,
    assetName: "VoltCore-latest.apk",
    downloadUrl: `https://github.com/${cfg.githubOwner}/${cfg.githubRepo}/releases/latest/download/VoltCore-latest.apk`,
    totalDownloads: 0,
    latestDownloads: 0
  };

  async function loadBundledVersion() {
    try {
      const res = await fetch("version.json", { cache: "no-store" });
      if (!res.ok) return;
      const data = await res.json();
      const jsonTag = normalizeTag(data.tag || data.version);
      if (compareSemver(jsonTag, currentAppTag) >= 0) {
        currentAppTag = jsonTag;
        latestMeta.tag = jsonTag;
        updateVersionLabels(currentAppTag);
      }
    } catch (_ignored) {}
  }

  function applyReleaseData(releases) {
    if (!Array.isArray(releases) || releases.length === 0) return;

    let totalApkDownloads = 0;
    const rows = [];

    releases.forEach((rel, idx) => {
      const apkAssets = (rel.assets || []).filter((a) =>
        a.name && a.name.toLowerCase().endsWith(".apk")
      );
      const preferred =
        apkAssets.find((a) => !a.name.toLowerCase().includes("latest")) ||
        apkAssets[0];

      const releaseDownloads = apkAssets.reduce((acc, a) => acc + (a.download_count || 0), 0);
      totalApkDownloads += releaseDownloads;

      if (idx === 0) {
        const relTag = normalizeTag(rel.tag_name || latestMeta.tag);
        latestMeta.tag = relTag;
        latestMeta.latestDownloads = releaseDownloads;
        if (preferred) {
          latestMeta.assetName = preferred.name;
          latestMeta.downloadUrl = preferred.browser_download_url;
          latestMeta.sizeMb = (preferred.size / (1024 * 1024)).toFixed(1);
        }
      }

      if (preferred) {
        rows.push({
          tag: rel.tag_name,
          assetName: preferred.name,
          sizeMb: `${(preferred.size / (1024 * 1024)).toFixed(1)} MB`,
          downloads: releaseDownloads
        });
      }
    });

    latestMeta.totalDownloads = totalApkDownloads;

    // Always keep "New version is available" banner synced with latest release (including current release)
    const updateBanner = document.getElementById("update-banner");
    const updateBannerVer = document.getElementById("update-banner-version");
    if (compareSemver(latestMeta.tag, currentAppTag) >= 0) {
      if (updateBanner) updateBanner.hidden = false;
      if (updateBannerVer) updateBannerVer.textContent = latestMeta.tag;
    }

    updateVersionLabels(currentAppTag);

    const navCount = document.getElementById("nav-download-count");
    const heroTotal = document.getElementById("hero-total-downloads");
    const heroMeta = document.getElementById("hero-apk-meta");
    const modalTotal = document.getElementById("modal-total-downloads");
    const modalLatest = document.getElementById("modal-latest-downloads");
    const modalGa = document.getElementById("modal-ga-id");
    const tbody = document.getElementById("release-table-body");

    if (navCount) navCount.textContent = totalApkDownloads.toLocaleString();
    if (heroTotal) heroTotal.textContent = totalApkDownloads.toLocaleString();
    if (heroMeta) {
      const sizeStr = latestMeta.sizeMb ? ` · ${latestMeta.sizeMb} MB` : "";
      heroMeta.textContent = `${latestMeta.tag} · ${latestMeta.assetName}${sizeStr}`;
    }
    if (modalTotal) modalTotal.textContent = totalApkDownloads.toLocaleString();
    if (modalLatest) modalLatest.textContent = `${latestMeta.latestDownloads.toLocaleString()} (${latestMeta.tag})`;
    if (modalGa) modalGa.textContent = hasGaId ? cfg.gaMeasurementId : "Placeholder (config.js)";

    document.querySelectorAll(".track-apk-download").forEach((link) => {
      link.setAttribute("href", latestMeta.downloadUrl);
    });

    if (tbody) {
      tbody.innerHTML = rows.length
        ? rows
            .map(
              (r) => `
          <tr>
            <td class="mono text-mint">${escapeHtml(r.tag)}</td>
            <td class="mono">${escapeHtml(r.assetName)}</td>
            <td class="mono">${escapeHtml(r.sizeMb)}</td>
            <td class="mono"><strong>${r.downloads.toLocaleString()}</strong></td>
          </tr>`
            )
            .join("")
        : `<tr><td colspan="4" class="mono muted">No APK release assets published yet.</td></tr>`;
    }
  }

  function escapeHtml(str) {
    return String(str || "")
      .replace(/&/g, "&amp;")
      .replace(/</g, "&lt;")
      .replace(/>/g, "&gt;");
  }

  async function loadGitHubReleases() {
    await loadBundledVersion();
    const heroMeta = document.getElementById("hero-apk-meta");
    try {
      const cachedRaw = localStorage.getItem(CACHE_KEY);
      if (cachedRaw) {
        const parsed = JSON.parse(cachedRaw);
        if (Date.now() - parsed.ts < 5 * 60 * 1000 && Array.isArray(parsed.data)) {
          applyReleaseData(parsed.data);
          return;
        }
      }
      const res = await fetch(
        `https://api.github.com/repos/${cfg.githubOwner}/${cfg.githubRepo}/releases?per_page=20`,
        { headers: { Accept: "application/vnd.github+json" } }
      );
      if (!res.ok) throw new Error(`HTTP ${res.status}`);
      const data = await res.json();
      localStorage.setItem(CACHE_KEY, JSON.stringify({ ts: Date.now(), data }));
      applyReleaseData(data);
    } catch (_err) {
      if (heroMeta) heroMeta.textContent = `${currentAppTag} · GitHub Release APK`;
      const navCount = document.getElementById("nav-download-count");
      const heroTotal = document.getElementById("hero-total-downloads");
      if (navCount && navCount.textContent === "--") navCount.textContent = "Live";
      if (heroTotal && heroTotal.textContent === "--") heroTotal.textContent = "GitHub";
    }
  }

  document.querySelectorAll(".track-apk-download").forEach((link) => {
    link.addEventListener("click", () => {
      trackEvent("apk_download_click", {
        release_tag: latestMeta.tag,
        asset_name: latestMeta.assetName,
        button_id: link.id || "apk_cta"
      });
    });
  });

  // 5. Stats Modal & Share Button
  const statsModal = document.getElementById("stats-modal");
  const openModal = () => {
    if (!statsModal) return;
    statsModal.hidden = false;
    trackEvent("download_stats_modal_open", { total_downloads: latestMeta.totalDownloads });
  };
  const closeModal = () => {
    if (statsModal) statsModal.hidden = true;
  };

  document.getElementById("open-stats-btn")?.addEventListener("click", openModal);
  document.getElementById("hero-stats-trigger")?.addEventListener("click", openModal);
  document.getElementById("close-stats-btn")?.addEventListener("click", closeModal);
  statsModal?.addEventListener("click", (e) => {
    if (e.target === statsModal) closeModal();
  });

  const shareBtn = document.getElementById("share-link-btn");
  const toastEl = document.getElementById("toast-area");
  shareBtn?.addEventListener("click", async () => {
    const shareUrl = window.location.href;
    trackEvent("social_share_click", { method: navigator.share ? "web_share" : "clipboard" });
    if (navigator.share) {
      try {
        await navigator.share({
          title: "VoltCore — Smart BMS & Power Station Monitor",
          text: "Monitor LiFePO4 0.1% SoC, voltage, current, temperature trends, and Smart BMS telemetry on Android.",
          url: shareUrl
        });
        return;
      } catch (_ignored) {}
    }
    try {
      await navigator.clipboard.writeText(shareUrl);
      if (toastEl) {
        toastEl.textContent = "Link copied to clipboard";
        setTimeout(() => { toastEl.textContent = ""; }, 3000);
      }
    } catch (_ignored) {}
  });

  loadGitHubReleases();
})();
