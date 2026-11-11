(function startCakeAnimationEditor(global) {
  "use strict";

  const Config = global.CakeEditorConfig;
  const GROUP_META = Object.freeze({
    support: { label: "支撑", short: "SUP", className: "support" },
    largeBodies: { label: "主体", short: "BODY", className: "largeBodies" },
    smallParts: { label: "小零件", short: "PART", className: "smallParts" },
    candles: { label: "蜡烛", short: "CNDL", className: "candles" },
  });

  const elements = {
    frame: document.getElementById("prototype-frame"),
    previewLoading: document.getElementById("preview-loading"),
    previewError: document.getElementById("preview-error"),
    runtimeState: document.getElementById("runtime-state"),
    previewTitle: document.getElementById("preview-title"),
    documentStatus: document.getElementById("document-status"),
    playButton: document.getElementById("play-button"),
    replayButton: document.getElementById("replay-button"),
    resetViewButton: document.getElementById("reset-view-button"),
    currentTime: document.getElementById("current-time"),
    totalTime: document.getElementById("total-time"),
    scrubber: document.getElementById("transport-scrubber"),
    timelineContent: document.getElementById("timeline-content"),
    timelineTracks: document.getElementById("timeline-tracks"),
    rulerGrid: document.getElementById("ruler-grid"),
    playhead: document.getElementById("timeline-playhead"),
    inspectorTitle: document.getElementById("inspector-title"),
    inspectorDescription: document.getElementById("inspector-description"),
    selectionEmpty: document.getElementById("selection-empty"),
    trackForm: document.getElementById("track-form"),
    startRange: document.getElementById("start-seconds-range"),
    startNumber: document.getElementById("start-seconds"),
    scaleRange: document.getElementById("duration-scale-range"),
    scaleNumber: document.getElementById("duration-scale"),
    trackId: document.getElementById("track-id"),
    trackTier: document.getElementById("track-tier"),
    trackNodeCount: document.getElementById("track-node-count"),
    trackDuration: document.getElementById("track-duration"),
    smallPartStagger: document.getElementById("small-part-stagger"),
    candleSlotStagger: document.getElementById("candle-slot-stagger"),
    configSummary: document.getElementById("config-summary"),
    importButton: document.getElementById("import-button"),
    importFile: document.getElementById("import-file"),
    copyButton: document.getElementById("copy-button"),
    downloadButton: document.getElementById("download-button"),
    resetButton: document.getElementById("reset-button"),
    toast: document.getElementById("toast"),
  };

  let config = Config.createDefaults();
  let activeDifficulty = config.activeDifficulty;
  let selectedTrack = { groupKey: "smallParts", id: "tier-0-small-parts" };
  let memberCounts = {};
  let currentTime = 0;
  let totalTime = 3;
  let timelineScale = 3;
  let playing = false;
  let dirty = false;
  let animationFrame = 0;
  let lastAnimationTime = 0;
  let toastTimer = 0;

  const adapter = new global.CakePrototypeAdapter(elements.frame, {
    onReady: handleRuntimeReady,
    onError: handleRuntimeError,
  });

  function activeConfig() {
    return config.difficulties[activeDifficulty];
  }

  function allTracks() {
    const difficulty = activeConfig();
    return Config.GROUP_KEYS.flatMap((groupKey) => (
      difficulty.groups[groupKey].map((item) => ({ groupKey, item }))
    ));
  }

  function findSelectedTrack() {
    if (!selectedTrack) return null;
    const item = activeConfig().groups[selectedTrack.groupKey]
      ?.find((candidate) => candidate.id === selectedTrack.id);
    return item ? { groupKey: selectedTrack.groupKey, item } : null;
  }

  function trackLabel(groupKey, item) {
    if (groupKey === "support") return "蛋糕支撑";
    return `第 ${item.tierIndex + 1} 层${GROUP_META[groupKey].label}`;
  }

  function round(value, digits = 2) {
    const factor = 10 ** digits;
    return Math.round(value * factor) / factor;
  }

  function markDirty() {
    dirty = true;
    elements.documentStatus.textContent = "schemaVersion 1 · 已修改";
  }

  function markClean(message = "未修改") {
    dirty = false;
    elements.documentStatus.textContent = `schemaVersion 1 · ${message}`;
  }

  function handleRuntimeReady() {
    elements.previewLoading.hidden = true;
    elements.previewError.hidden = true;
    elements.frame.classList.add("ready");
    elements.runtimeState.classList.add("ready");
    elements.runtimeState.classList.remove("error");
    elements.runtimeState.lastChild.textContent = "原型节点已连接";
    switchRuntimeDifficulty();
    currentTime = totalTime;
    renderAll();
    applyCurrentFrame();
  }

  function handleRuntimeError(error) {
    elements.previewLoading.hidden = true;
    elements.previewError.hidden = false;
    elements.runtimeState.classList.remove("ready");
    elements.runtimeState.classList.add("error");
    elements.runtimeState.lastChild.textContent = "原型连接失败";
    console.error("[cake-animation-editor]", error);
  }

  function switchRuntimeDifficulty() {
    if (!adapter.ready) return;
    adapter.switchDifficulty(activeDifficulty);
    memberCounts = adapter.getMemberCounts();
    recomputeTimeline();
  }

  function recomputeTimeline() {
    totalTime = Config.getTimelineEnd(activeDifficulty, activeConfig(), memberCounts);
    timelineScale = Math.max(3, Math.ceil(totalTime * 2) / 2);
    currentTime = Math.min(currentTime, totalTime);
    elements.scrubber.max = String(totalTime);
    elements.totalTime.textContent = `${totalTime.toFixed(2)} s`;
  }

  function renderAll() {
    renderDifficulty();
    renderTimeline();
    renderInspector();
    renderGlobalFields();
    renderSummary();
    updateTransport();
  }

  function renderDifficulty() {
    const meta = Config.DIFFICULTY_META[activeDifficulty];
    elements.previewTitle.textContent = meta.title;
    document.querySelectorAll("[data-difficulty]").forEach((button) => {
      button.setAttribute(
        "aria-pressed",
        String(button.dataset.difficulty === activeDifficulty),
      );
    });
  }

  function renderTimeline() {
    const difficulty = activeConfig();
    const secondWidth = `${100 / timelineScale}%`;
    elements.rulerGrid.style.setProperty("--second-width", secondWidth);
    elements.rulerGrid.replaceChildren();

    for (let time = 0; time <= timelineScale + 0.001; time += 0.5) {
      const tick = document.createElement("div");
      tick.className = "ruler-tick";
      tick.style.left = `${time / timelineScale * 100}%`;
      if (Math.abs(time - Math.round(time)) < 0.001) {
        const label = document.createElement("span");
        label.textContent = `${time.toFixed(1)}s`;
        tick.appendChild(label);
      }
      elements.rulerGrid.appendChild(tick);
    }

    elements.timelineTracks.replaceChildren();
    for (const groupKey of Config.GROUP_KEYS) {
      for (const item of difficulty.groups[groupKey]) {
        const row = document.createElement("div");
        row.className = "timeline-track";
        row.dataset.group = groupKey;
        row.dataset.trackId = item.id;
        row.tabIndex = 0;
        row.setAttribute("role", "button");
        row.setAttribute("aria-label", `编辑${trackLabel(groupKey, item)}`);
        if (selectedTrack?.id === item.id && selectedTrack.groupKey === groupKey) {
          row.classList.add("selected");
        }

        const count = Math.max(0, Number(memberCounts[item.id]) || 0);
        const effectiveDuration = Config.getEffectiveDuration(
          activeDifficulty,
          difficulty,
          groupKey,
          item,
          Math.max(count, 1),
        );
        const stagger = Config.staggerForGroup(difficulty, groupKey);
        const staggerStep = count > 1 && stagger > 0
          ? `${stagger / effectiveDuration * 100}%`
          : "100%";

        const label = document.createElement("div");
        label.className = "track-label";
        label.innerHTML = `
          <span class="track-kind">${GROUP_META[groupKey].short}</span>
          <span class="track-name">${trackLabel(groupKey, item)}</span>
          <span class="track-count">${count || "–"}</span>
        `;

        const lane = document.createElement("div");
        lane.className = "track-lane";
        lane.style.setProperty("--second-width", secondWidth);

        const bar = document.createElement("div");
        bar.className = `timeline-bar ${GROUP_META[groupKey].className}`;
        bar.style.left = `${item.startSeconds / timelineScale * 100}%`;
        bar.style.width = `${effectiveDuration / timelineScale * 100}%`;
        bar.style.setProperty("--stagger-step", staggerStep);
        const barText = document.createElement("span");
        barText.textContent = `${item.startSeconds.toFixed(2)}s · ${item.durationScale.toFixed(2)}×`;
        bar.appendChild(barText);
        lane.appendChild(bar);
        row.append(label, lane);

        row.addEventListener("click", (event) => {
          selectTrack(groupKey, item.id);
          if (event.target.closest(".track-lane")) {
            const bounds = lane.getBoundingClientRect();
            pause();
            setCurrentTime(
              clamp((event.clientX - bounds.left) / bounds.width, 0, 1) * timelineScale,
            );
          }
        });
        row.addEventListener("keydown", (event) => {
          if (event.key === "Enter" || event.key === " ") {
            event.preventDefault();
            selectTrack(groupKey, item.id);
          }
        });
        elements.timelineTracks.appendChild(row);
      }
    }
    updatePlayhead();
  }

  function renderInspector() {
    const selection = findSelectedTrack();
    if (!selection) {
      elements.selectionEmpty.hidden = false;
      elements.trackForm.hidden = true;
      elements.inspectorTitle.textContent = "选择一条时间轴轨道";
      elements.inspectorDescription.textContent = "点击底部轨道，编辑该层的开始时间和时长倍率。";
      return;
    }

    const { groupKey, item } = selection;
    const count = Number(memberCounts[item.id]) || 0;
    const duration = Config.getEffectiveDuration(
      activeDifficulty,
      activeConfig(),
      groupKey,
      item,
      Math.max(count, 1),
    );

    elements.selectionEmpty.hidden = true;
    elements.trackForm.hidden = false;
    elements.inspectorTitle.textContent = trackLabel(groupKey, item);
    elements.inspectorDescription.textContent =
      `${GROUP_META[groupKey].label}轨道 · 修改后预览会跳到该轨道中段。`;
    elements.startRange.max = String(Math.max(6, timelineScale + 2));
    elements.startRange.value = String(item.startSeconds);
    elements.startNumber.value = item.startSeconds.toFixed(2);
    elements.scaleRange.value = String(item.durationScale);
    elements.scaleNumber.value = item.durationScale.toFixed(2);
    elements.trackId.textContent = item.id;
    elements.trackTier.textContent = item.tierIndex < 0 ? "support" : String(item.tierIndex);
    elements.trackNodeCount.textContent = `${count} nodes`;
    elements.trackDuration.textContent = `${duration.toFixed(2)} s`;
  }

  function renderGlobalFields() {
    elements.smallPartStagger.value =
      activeConfig().smallPartStaggerSeconds.toFixed(2);
    elements.candleSlotStagger.value =
      activeConfig().candleSlotStaggerSeconds.toFixed(2);
  }

  function renderSummary() {
    const trackCount = allTracks().length;
    const nodeCount = Object.values(memberCounts).reduce((sum, value) => sum + value, 0);
    elements.configSummary.textContent = `${trackCount} 条轨道 · ${nodeCount} 个实际节点`;
  }

  function selectTrack(groupKey, id) {
    selectedTrack = { groupKey, id };
    renderTimeline();
    renderInspector();
  }

  function previewSelection() {
    const selection = findSelectedTrack();
    if (!selection) return;
    const count = Math.max(1, Number(memberCounts[selection.item.id]) || 1);
    const duration = Config.getEffectiveDuration(
      activeDifficulty,
      activeConfig(),
      selection.groupKey,
      selection.item,
      count,
    );
    pause();
    setCurrentTime(selection.item.startSeconds + duration * 0.56);
  }

  function updateSelectedTrack(field, value) {
    const selection = findSelectedTrack();
    if (!selection || !Number.isFinite(value)) return;
    const limits = field === "startSeconds" ? [0, 30] : [0.25, 6];
    selection.item[field] = round(clamp(value, limits[0], limits[1]), 3);
    markDirty();
    recomputeTimeline();
    renderTimeline();
    renderInspector();
    previewSelection();
  }

  function updateStagger(field, value, preferredGroup) {
    if (!Number.isFinite(value)) return;
    activeConfig()[field] = round(clamp(value, 0, 2), 3);
    const target = activeConfig().groups[preferredGroup][0];
    selectedTrack = { groupKey: preferredGroup, id: target.id };
    markDirty();
    recomputeTimeline();
    renderTimeline();
    renderInspector();
    renderGlobalFields();
    previewSelection();
  }

  function setCurrentTime(value) {
    currentTime = clamp(Number(value) || 0, 0, totalTime);
    updateTransport();
    applyCurrentFrame();
  }

  function updateTransport() {
    elements.currentTime.textContent = currentTime.toFixed(2);
    elements.scrubber.value = String(currentTime);
    elements.playButton.classList.toggle("playing", playing);
    elements.playButton.setAttribute("aria-label", playing ? "暂停" : "播放");
    updatePlayhead();
  }

  function updatePlayhead() {
    const width = elements.rulerGrid.clientWidth;
    const offset = width * clamp(currentTime / timelineScale, 0, 1);
    elements.playhead.style.setProperty("--playhead-x", `${offset}px`);
  }

  function applyCurrentFrame() {
    adapter.apply(currentTime, activeConfig());
  }

  function play() {
    if (!adapter.ready) return;
    if (currentTime >= totalTime - 0.001) {
      setCurrentTime(0);
    }
    playing = true;
    lastAnimationTime = performance.now();
    updateTransport();
    cancelAnimationFrame(animationFrame);
    animationFrame = requestAnimationFrame(animate);
  }

  function pause() {
    playing = false;
    cancelAnimationFrame(animationFrame);
    updateTransport();
  }

  function animate(now) {
    if (!playing) return;
    const deltaSeconds = Math.min(0.05, (now - lastAnimationTime) / 1000);
    lastAnimationTime = now;
    setCurrentTime(currentTime + deltaSeconds);
    if (currentTime >= totalTime - 0.001) {
      pause();
      return;
    }
    animationFrame = requestAnimationFrame(animate);
  }

  function changeDifficulty(difficultyKey, options = {}) {
    if (!Config.DIFFICULTY_KEYS.includes(difficultyKey)) return;
    pause();
    activeDifficulty = difficultyKey;
    config.activeDifficulty = difficultyKey;
    const selectedGroup = selectedTrack?.groupKey || "smallParts";
    const firstTrack = config.difficulties[difficultyKey].groups[selectedGroup][0];
    selectedTrack = { groupKey: selectedGroup, id: firstTrack.id };
    if (!options.preserveClean) markDirty();
    switchRuntimeDifficulty();
    currentTime = totalTime;
    renderAll();
    applyCurrentFrame();
  }

  function serializeConfig() {
    config.activeDifficulty = activeDifficulty;
    return JSON.stringify(config, null, 2);
  }

  async function copyConfig() {
    const text = serializeConfig();
    try {
      await navigator.clipboard.writeText(text);
    } catch (_) {
      const textarea = document.createElement("textarea");
      textarea.value = text;
      textarea.style.position = "fixed";
      textarea.style.opacity = "0";
      document.body.appendChild(textarea);
      textarea.select();
      const copied = document.execCommand("copy");
      textarea.remove();
      if (!copied) throw new Error("浏览器未授权剪贴板");
    }
    showToast("JSON 已复制到剪贴板");
  }

  function downloadConfig() {
    const blob = new Blob([serializeConfig()], { type: "application/json;charset=utf-8" });
    const url = URL.createObjectURL(blob);
    const anchor = document.createElement("a");
    anchor.href = url;
    anchor.download = "cake-animation-timing.schema-v1.json";
    anchor.click();
    URL.revokeObjectURL(url);
    showToast("JSON 下载已开始");
  }

  function applyImportedConfig(nextConfig) {
    config = Config.canonicalize(nextConfig);
    activeDifficulty = config.activeDifficulty;
    selectedTrack = { groupKey: "smallParts", id: "tier-0-small-parts" };
    pause();
    switchRuntimeDifficulty();
    currentTime = totalTime;
    markDirty();
    renderAll();
    applyCurrentFrame();
  }

  function importConfigFile(file) {
    if (!file) return;
    const reader = new FileReader();
    reader.addEventListener("load", () => {
      try {
        applyImportedConfig(JSON.parse(String(reader.result)));
        showToast(`已导入 ${file.name}`);
      } catch (error) {
        showToast(`导入失败：${error.message}`, 4200);
      } finally {
        elements.importFile.value = "";
      }
    });
    reader.addEventListener("error", () => {
      showToast("导入失败：无法读取文件", 4200);
      elements.importFile.value = "";
    });
    reader.readAsText(file);
  }

  function resetConfig() {
    config = Config.createDefaults();
    activeDifficulty = config.activeDifficulty;
    selectedTrack = { groupKey: "smallParts", id: "tier-0-small-parts" };
    pause();
    switchRuntimeDifficulty();
    currentTime = totalTime;
    markClean("已恢复默认值");
    renderAll();
    applyCurrentFrame();
    showToast("三档时序已恢复默认值");
  }

  function showToast(message, duration = 2400) {
    clearTimeout(toastTimer);
    elements.toast.textContent = message;
    elements.toast.classList.add("visible");
    toastTimer = setTimeout(() => {
      elements.toast.classList.remove("visible");
    }, duration);
  }

  function bindInputs() {
    document.querySelectorAll("[data-difficulty]").forEach((button) => {
      button.addEventListener("click", () => changeDifficulty(button.dataset.difficulty));
    });

    elements.playButton.addEventListener("click", () => (playing ? pause() : play()));
    elements.replayButton.addEventListener("click", () => {
      pause();
      setCurrentTime(0);
      play();
    });
    elements.resetViewButton.addEventListener("click", () => adapter.resetView());
    elements.scrubber.addEventListener("input", () => {
      pause();
      setCurrentTime(Number(elements.scrubber.value));
    });

    elements.startRange.addEventListener("input", () => {
      updateSelectedTrack("startSeconds", Number(elements.startRange.value));
    });
    elements.startNumber.addEventListener("change", () => {
      updateSelectedTrack("startSeconds", Number(elements.startNumber.value));
    });
    elements.scaleRange.addEventListener("input", () => {
      updateSelectedTrack("durationScale", Number(elements.scaleRange.value));
    });
    elements.scaleNumber.addEventListener("change", () => {
      updateSelectedTrack("durationScale", Number(elements.scaleNumber.value));
    });
    elements.smallPartStagger.addEventListener("change", () => {
      updateStagger(
        "smallPartStaggerSeconds",
        Number(elements.smallPartStagger.value),
        "smallParts",
      );
    });
    elements.candleSlotStagger.addEventListener("change", () => {
      updateStagger(
        "candleSlotStaggerSeconds",
        Number(elements.candleSlotStagger.value),
        "candles",
      );
    });

    elements.importButton.addEventListener("click", () => elements.importFile.click());
    elements.importFile.addEventListener("change", () => {
      importConfigFile(elements.importFile.files?.[0]);
    });
    elements.copyButton.addEventListener("click", copyConfig);
    elements.downloadButton.addEventListener("click", downloadConfig);
    elements.resetButton.addEventListener("click", resetConfig);

    global.addEventListener("resize", updatePlayhead);
    global.addEventListener("keydown", (event) => {
      if (event.code !== "Space" || /INPUT|TEXTAREA|SELECT/.test(event.target.tagName)) return;
      event.preventDefault();
      playing ? pause() : play();
    });
    global.addEventListener("pagehide", () => {
      pause();
      adapter.destroy();
    });
  }

  function clamp(value, min, max) {
    return Math.max(min, Math.min(max, value));
  }

  global.cakeAnimationEditor = Object.freeze({
    schemaVersion: Config.SCHEMA_VERSION,
    getConfig: () => Config.clone(config),
    getState: () => ({
      activeDifficulty,
      currentTime,
      totalTime,
      playing,
      dirty,
      memberCounts: { ...memberCounts },
    }),
    getRuntimeSnapshot: () => adapter.getSnapshot(),
    setTime: (seconds) => {
      pause();
      setCurrentTime(seconds);
    },
    setDifficulty: (difficultyKey) => changeDifficulty(difficultyKey),
    importConfig: (value) => applyImportedConfig(value),
  });

  bindInputs();
  recomputeTimeline();
  renderAll();
  markClean();
  adapter.connect();
}(window));
