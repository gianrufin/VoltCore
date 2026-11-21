(function () {
  const cfg = window.VOLTCORE_CONFIG || {
    githubOwner: "gianrufin",
    githubRepo: "VoltCore",
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

  // 2. Interactive Liquid Battery Juice Canvas
  const canvas = document.getElementById("hero-battery-canvas");
  const ctx = canvas ? canvas.getContext("2d") : null;
  const socSlider = document.getElementById("soc-slider");
  const socValueEl = document.getElementById("demo-soc-value");
  const socPctEl = document.getElementById("demo-soc-pct");
  const whLabelEl = document.getElementById("demo-wh-label");
  const modeBadgeEl = document.getElementById("demo-mode-badge");
  const modeLabelEl = document.getElementById("demo-mode-label");
  const etaReadoutEl = document.getElementById("demo-eta-readout");
  const grossInEl = document.getElementById("demo-gross-in");
  const packNetEl = document.getElementById("demo-pack-net");
  const loadOutEl = document.getElementById("demo-load-out");
  const dotsInEl = document.getElementById("demo-flow-dots-in");
  const dotsOutEl = document.getElementById("demo-flow-dots-out");
  const specEl = document.getElementById("demo-pack-spec");

  const PACK_WH = 3584;
  let targetSoc = 80.3;
  let animatedSoc = 80.3;
  let activeMode = "passthrough"; // passthrough | charging | discharging
  let wavePhase = 0;

  function getAccentColor() {
    if (targetSoc <= 15) return "#fb7185";
    if (activeMode === "discharging") return "#60a5fa";
    return "#2ef29b";
  }

  function syncDemoReadouts() {
    const color = getAccentColor();
    const remWh = Math.round((targetSoc / 100) * PACK_WH);
    if (socValueEl) socValueEl.textContent = targetSoc.toFixed(1);
    if (socPctEl) socPctEl.style.color = color;
    if (specEl) specEl.style.color = color;
    if (whLabelEl) whLabelEl.textContent = `${remWh} / ${PACK_WH} Wh`;

    if (modeBadgeEl) {
      modeBadgeEl.classList.remove("ice", "coral");
      if (targetSoc <= 15) modeBadgeEl.classList.add("coral");
      else if (activeMode === "discharging") modeBadgeEl.classList.add("ice");
    }

    if (activeMode === "passthrough") {
      if (modeLabelEl) modeLabelEl.textContent = "CHARGING · PASS-THROUGH";
      if (grossInEl) grossInEl.textContent = "+400 W";
      if (packNetEl) { packNetEl.textContent = "+340 W"; packNetEl.className = "mono text-mint"; }
      if (loadOutEl) loadOutEl.textContent = "60 W";
      if (dotsInEl) dotsInEl.classList.remove("paused");
      if (dotsOutEl) dotsOutEl.classList.remove("paused");
      const hrs = ((1 - targetSoc / 100) * PACK_WH) / 340;
      if (etaReadoutEl) {
        etaReadoutEl.textContent = formatHours(hrs) + " to full";
        etaReadoutEl.className = "mono tiny-label text-mint";
      }
    } else if (activeMode === "charging") {
      if (modeLabelEl) modeLabelEl.textContent = "CHARGING · SOLAR + ALT";
      if (grossInEl) grossInEl.textContent = "+400 W";
      if (packNetEl) { packNetEl.textContent = "+400 W"; packNetEl.className = "mono text-mint"; }
      if (loadOutEl) loadOutEl.textContent = "0 W";
      if (dotsInEl) dotsInEl.classList.remove("paused");
      if (dotsOutEl) dotsOutEl.classList.add("paused");
      const hrs = ((1 - targetSoc / 100) * PACK_WH) / 400;
      if (etaReadoutEl) {
        etaReadoutEl.textContent = formatHours(hrs) + " to full";
        etaReadoutEl.className = "mono tiny-label text-mint";
      }
    } else {
      if (modeLabelEl) modeLabelEl.textContent = "DISCHARGING · LOAD";
      if (grossInEl) grossInEl.textContent = "0 W";
      if (packNetEl) { packNetEl.textContent = "-120 W"; packNetEl.className = "mono text-ice"; }
      if (loadOutEl) loadOutEl.textContent = "120 W";
      if (dotsInEl) dotsInEl.classList.add("paused");
      if (dotsOutEl) dotsOutEl.classList.remove("paused");
      const hrs = ((targetSoc / 100) * PACK_WH) / 120;
      if (etaReadoutEl) {
        etaReadoutEl.textContent = formatHours(hrs) + " remaining";
        etaReadoutEl.className = "mono tiny-label text-ice";
      }
    }
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

  function drawBatteryCanvas() {
    if (!ctx || !canvas) return;
    const dpr = window.devicePixelRatio || 1;
    const displaySize = 268;
    if (canvas.width !== displaySize * dpr) {
      canvas.width = displaySize * dpr;
      canvas.height = displaySize * dpr;
    }
    ctx.save();
    ctx.scale(dpr, dpr);
    ctx.clearRect(0, 0, displaySize, displaySize);

    animatedSoc += (targetSoc - animatedSoc) * 0.12;
    const progress = Math.max(0, Math.min(1, animatedSoc / 100));
    const cx = displaySize / 2;
    const cy = displaySize / 2;
    const strokeW = 14;
    const ringRadius = (displaySize - strokeW) / 2 - 4;
    const vesselRadius = ringRadius - strokeW / 2 - 8;
    const accent = getAccentColor();
    const isCharge = activeMode !== "discharging";

    // Inner vessel backdrop
    ctx.beginPath();
    ctx.arc(cx, cy, vesselRadius, 0, Math.PI * 2);
    ctx.fillStyle = "rgba(5, 6, 8, 0.85)";
    ctx.fill();
    ctx.strokeStyle = "rgba(39, 48, 61, 0.7)";
    ctx.lineWidth = 1;
    ctx.stroke();

    // Clipped liquid wave
    ctx.save();
    ctx.beginPath();
    ctx.arc(cx, cy, vesselRadius, 0, Math.PI * 2);
    ctx.clip();

    const vTop = cy - vesselRadius;
    const vBottom = cy + vesselRadius;
    const vDiameter = vesselRadius * 2;
    const fillY = vBottom - vDiameter * progress;
    const damp = Math.max(0.18, Math.sin(progress * Math.PI));
    const amp = 7 * damp;

    // Back wave
    ctx.beginPath();
    ctx.moveTo(cx - vesselRadius, vBottom);
    for (let x = 0; x <= vDiameter; x += 4) {
      const frac = x / vDiameter;
      const y = fillY + Math.sin(frac * Math.PI * 2 - wavePhase + 1.1) * (amp * 0.85);
      ctx.lineTo(cx - vesselRadius + x, y);
    }
    ctx.lineTo(cx + vesselRadius, vBottom);
    ctx.closePath();
    ctx.fillStyle = accent + "36";
    ctx.fill();

    // Front wave
    ctx.beginPath();
    ctx.moveTo(cx - vesselRadius, vBottom);
    for (let x = 0; x <= vDiameter; x += 4) {
      const frac = x / vDiameter;
      const y = fillY + Math.sin(frac * Math.PI * 2.4 + wavePhase) * amp;
      ctx.lineTo(cx - vesselRadius + x, y);
    }
    ctx.lineTo(cx + vesselRadius, vBottom);
    ctx.closePath();
    const grad = ctx.createLinearGradient(0, fillY - amp, 0, vBottom);
    grad.addColorStop(0, accent + "82");
    grad.addColorStop(1, accent + "24");
    ctx.fillStyle = grad;
    ctx.fill();

    // Meniscus crest line
    ctx.beginPath();
    for (let x = 0; x <= vDiameter; x += 4) {
      const frac = x / vDiameter;
      const y = fillY + Math.sin(frac * Math.PI * 2.4 + wavePhase) * amp;
      if (x === 0) ctx.moveTo(cx - vesselRadius + x, y);
      else ctx.lineTo(cx - vesselRadius + x, y);
    }
    ctx.strokeStyle = accent;
    ctx.lineWidth = 2;
    ctx.stroke();

    // Rising charge bubbles
    if (isCharge && progress > 0.08) {
      const depth = Math.max(10, vBottom - fillY);
      for (let i = 0; i < 6; i++) {
        const bp = ((wavePhase / (Math.PI * 2)) + i * 0.19) % 1;
        const bx = cx - vesselRadius * 0.55 + i * (vesselRadius * 0.22) + Math.sin(bp * Math.PI * 2 + i) * 5;
        const by = vBottom - bp * depth;
        if (by > fillY + 6) {
          ctx.beginPath();
          ctx.arc(bx, by, 1.8 + (i % 3) * 0.9, 0, Math.PI * 2);
          ctx.fillStyle = `rgba(244, 247, 251, ${(1 - bp) * 0.5})`;
          ctx.fill();
        }
      }
    }
    ctx.restore();

    // Outer 270 deg track
    const startRad = (135 * Math.PI) / 180;
    const totalSweep = (270 * Math.PI) / 180;
    ctx.beginPath();
    ctx.arc(cx, cy, ringRadius, startRad, startRad + totalSweep);
    ctx.strokeStyle = "#161b23";
    ctx.lineWidth = strokeW;
    ctx.lineCap = "round";
    ctx.stroke();

    // Active SoC arc
    const activeSweep = totalSweep * progress;
    if (activeSweep > 0.01) {
      ctx.beginPath();
      ctx.arc(cx, cy, ringRadius, startRad, startRad + activeSweep);
      ctx.strokeStyle = accent;
      ctx.lineWidth = strokeW;
      ctx.lineCap = "round";
      ctx.stroke();

      // Animated directional pulse along arc
      const dirNorm = ((wavePhase / (Math.PI * 2)) * 0.7) % 1;
      const phasePos = isCharge ? dirNorm : 1 - dirNorm;
      const pulseLen = Math.min(0.45, activeSweep * 0.25);
      if (activeSweep > pulseLen + 0.05) {
        const pulseStart = startRad + phasePos * (activeSweep - pulseLen);
        ctx.beginPath();
        ctx.arc(cx, cy, ringRadius, pulseStart, pulseStart + pulseLen);
        ctx.strokeStyle = `rgba(244, 247, 251, ${0.45 + 0.35 * Math.sin(dirNorm * Math.PI)})`;
        ctx.lineWidth = strokeW * 0.55;
        ctx.lineCap = "round";
        ctx.stroke();
      }
    }

    ctx.restore();
    wavePhase += 0.048;
    requestAnimationFrame(drawBatteryCanvas);
  }

  if (socSlider) {
    socSlider.addEventListener("input", (e) => {
      targetSoc = parseFloat(e.target.value);
      syncDemoReadouts();
    });
    socSlider.addEventListener("change", () => {
      trackEvent("battery_demo_interact", { soc_percent: targetSoc, mode: activeMode });
    });
  }

  document.querySelectorAll(".pill-btn").forEach((btn) => {
    btn.addEventListener("click", () => {
      document.querySelectorAll(".pill-btn").forEach((b) => b.classList.remove("active"));
      btn.classList.add("active");
      activeMode = btn.dataset.mode || "passthrough";
      syncDemoReadouts();
      trackEvent("battery_demo_interact", { soc_percent: targetSoc, mode: activeMode });
    });
  });

  syncDemoReadouts();
  requestAnimationFrame(drawBatteryCanvas);

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

  // 4. Live GitHub Releases API Download Monitor & Modal
  const CACHE_KEY = `voltcore_releases_${cfg.githubOwner}_${cfg.githubRepo}`;
  let latestMeta = {
    tag: "v1.1.0",
    assetName: "VoltCore-latest.apk",
    downloadUrl: `https://github.com/${cfg.githubOwner}/${cfg.githubRepo}/releases/latest/download/VoltCore-latest.apk`,
    totalDownloads: 0,
    latestDownloads: 0
  };

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
        latestMeta.tag = rel.tag_name || latestMeta.tag;
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

    const navPill = document.getElementById("nav-version-pill");
    const navCount = document.getElementById("nav-download-count");
    const heroTotal = document.getElementById("hero-total-downloads");
    const heroTag = document.getElementById("hero-latest-tag");
    const heroMeta = document.getElementById("hero-apk-meta");
    const modalTotal = document.getElementById("modal-total-downloads");
    const modalLatest = document.getElementById("modal-latest-downloads");
    const modalGa = document.getElementById("modal-ga-id");
    const tbody = document.getElementById("release-table-body");

    if (navPill) navPill.textContent = latestMeta.tag;
    if (navCount) navCount.textContent = totalApkDownloads.toLocaleString();
    if (heroTotal) heroTotal.textContent = totalApkDownloads.toLocaleString();
    if (heroTag) heroTag.textContent = latestMeta.tag;
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
      if (heroMeta) heroMeta.textContent = "Direct GitHub Release APK";
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
          title: "VoltCore — Smart BMS Power Cockpit",
          text: "Monitor LiFePO4 0.1% SoC, alternator pass-through watts, and cell balance on Android.",
          url: shareUrl
        });
        return;
      } catch (_ignored) {}
    }
    try {
      await navigator.clipboard.writeText(shareUrl);
      if (toastEl) {
        toastEl.textContent = "Landing page link copied!";
        setTimeout(() => { toastEl.textContent = ""; }, 3000);
      }
    } catch (_ignored) {}
  });

  // 6. Scroll Reveal Observer
  const observer = new IntersectionObserver(
    (entries) => {
      entries.forEach((entry) => {
        if (entry.isIntersecting) {
          entry.target.classList.add("in-view");
          observer.unobserve(entry.target);
        }
      });
    },
    { threshold: 0.12 }
  );
  document.querySelectorAll(".reveal").forEach((el) => observer.observe(el));

  loadGitHubReleases();
})();
