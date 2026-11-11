(function defineCakePrototypeAdapter(global) {
  "use strict";

  const Config = global.CakeEditorConfig;

  function easeOutCubic(value) {
    return 1 - Math.pow(1 - value, 3);
  }

  function clamp(value, min, max) {
    return Math.max(min, Math.min(max, value));
  }

  function partIdOf(node) {
    return node?.userData?.partId || "";
  }

  class CakePrototypeAdapter {
    constructor(frame, callbacks = {}) {
      this.frame = frame;
      this.onReady = callbacks.onReady || (() => {});
      this.onError = callbacks.onError || (() => {});
      this.frameWindow = null;
      this.runtime = null;
      this.difficultyKey = null;
      this.trackNodes = new Map();
      this.ready = false;
      this.attachStartedAt = 0;
      this.attachFrame = 0;
      this.currentTime = 0;
      this.currentDifficulty = null;
    }

    connect() {
      this.attachStartedAt = performance.now();
      const attempt = () => {
        try {
          const frameWindow = this.frame.contentWindow;
          if (frameWindow?.__ready && frameWindow.__model?.userData?.sculptRuntime) {
            this.frameWindow = frameWindow;
            this.ready = true;
            this.prepareFrame();
            this.onReady();
            return;
          }
        } catch (error) {
          this.fail(error);
          return;
        }

        if (performance.now() - this.attachStartedAt > 12000) {
          this.fail(new Error("原型在 12 秒内未完成初始化"));
          return;
        }
        this.attachFrame = requestAnimationFrame(attempt);
      };

      attempt();
    }

    prepareFrame() {
      this.frameWindow.__captureModel?.();
      this.frameWindow.candleDemo?.pause?.();
      const body = this.frameWindow.document?.body;
      if (body) {
        body.style.margin = "0";
        body.style.overflow = "hidden";
      }
    }

    fail(error) {
      cancelAnimationFrame(this.attachFrame);
      this.ready = false;
      this.onError(error instanceof Error ? error : new Error(String(error)));
    }

    switchDifficulty(difficultyKey) {
      if (!this.ready) return;
      const meta = Config.DIFFICULTY_META[difficultyKey];
      if (!meta) throw new Error(`未知难度 ${difficultyKey}`);

      this.frameWindow.__setLevel(meta.level);
      this.frameWindow.candleDemo?.pause?.();
      this.frameWindow.candleDemo?.candles?.finishInsertion?.();
      this.frameWindow.__captureModel?.();
      this.frameWindow.__setView?.(16);

      this.difficultyKey = difficultyKey;
      this.runtime = this.frameWindow.__model.userData.sculptRuntime;
      this.buildTrackMap();
    }

    buildTrackMap() {
      const entries = Object.entries(this.runtime?.nodes || {})
        .filter(([, node]) => Array.isArray(node?.userData?.home));
      const topLevel = entries.map(([id, node]) => ({ id, node }));
      const candles = topLevel
        .filter(({ id, node }) => id.startsWith("candle-") || node.userData.candleIndex != null)
        .sort((a, b) => a.node.userData.candleIndex - b.node.userData.candleIndex);

      const supportIds = this.difficultyKey === "easy"
        ? new Set(["wooden-stand", "scalloped-board"])
        : new Set(["silver-board"]);
      const bodyIds = this.difficultyKey === "easy"
        ? new Set(["cream-body", "top-icing"])
        : new Set(Array.from(
          { length: Config.DIFFICULTY_META[this.difficultyKey].tierCount },
          (_, index) => `tier-${index}`,
        ));

      const supportNodes = topLevel.filter(({ id }) => supportIds.has(id));
      const bodyNodes = topLevel.filter(({ id }) => bodyIds.has(id));
      const claimedIds = new Set([
        ...supportNodes.map(({ id }) => id),
        ...bodyNodes.map(({ id }) => id),
        ...candles.map(({ id }) => id),
      ]);
      const smallNodes = topLevel
        .filter(({ id }) => !claimedIds.has(id))
        .sort((a, b) => {
          const stageDelta = (a.node.userData.stage || 0) - (b.node.userData.stage || 0);
          return stageDelta || a.id.localeCompare(b.id, "en");
        });

      this.trackNodes.clear();
      this.trackNodes.set("cake-support", supportNodes);

      const tierCount = Config.DIFFICULTY_META[this.difficultyKey].tierCount;
      for (let tierIndex = 0; tierIndex < tierCount; tierIndex += 1) {
        const largeId = `tier-${tierIndex}-large-body`;
        const smallId = `tier-${tierIndex}-small-parts`;
        const candleId = `tier-${tierIndex}-candles`;

        this.trackNodes.set(
          largeId,
          this.difficultyKey === "easy"
            ? bodyNodes
            : bodyNodes.filter(({ node }) => node.userData.tierIndex === tierIndex),
        );
        this.trackNodes.set(
          smallId,
          smallNodes.filter(({ node }) => (node.userData.tierIndex ?? 0) === tierIndex),
        );
        this.trackNodes.set(
          candleId,
          candles.filter(({ node }) => (node.userData.tierIndex ?? 0) === tierIndex),
        );
      }
    }

    getMemberCounts() {
      return Object.fromEntries(
        Array.from(this.trackNodes, ([trackId, members]) => [trackId, members.length]),
      );
    }

    getTrackMembers(trackId) {
      return this.trackNodes.get(trackId) || [];
    }

    apply(timeSeconds, difficulty) {
      if (!this.ready || !this.runtime) return;
      this.currentTime = Math.max(0, timeSeconds);
      this.currentDifficulty = difficulty;

      for (const groupKey of Config.GROUP_KEYS) {
        const stagger = Config.staggerForGroup(difficulty, groupKey);
        const baseDuration = Config.getBaseDuration(this.difficultyKey, groupKey);
        for (const item of difficulty.groups[groupKey]) {
          const members = this.getTrackMembers(item.id);
          members.forEach(({ node }, memberIndex) => {
            const start = item.startSeconds + memberIndex * stagger;
            const duration = baseDuration * item.durationScale;
            const progress = clamp((this.currentTime - start) / duration, 0, 1);
            const eased = easeOutCubic(progress);
            const home = node.userData.home;
            const travel = Number(node.userData.travel) || 0.72;

            node.visible = this.currentTime >= start;
            node.position.set(
              home[0],
              home[1] + (1 - eased) * travel,
              home[2],
            );
          });
        }
      }

      this.frameWindow.candleDemo?.render?.();
    }

    resetView() {
      if (!this.ready) return;
      this.frameWindow.__setView?.(16);
    }

    getSnapshot() {
      if (!this.ready) return { ready: false, tracks: {} };
      const tracks = {};
      for (const [trackId, members] of this.trackNodes) {
        tracks[trackId] = members.map(({ id, node }) => ({
          id,
          tierIndex: node.userData.tierIndex ?? 0,
          visible: node.visible,
          y: Number(node.position.y.toFixed(4)),
          homeY: Number(node.userData.home[1].toFixed(4)),
        }));
      }
      return {
        ready: true,
        difficulty: this.difficultyKey,
        timeSeconds: this.currentTime,
        tracks,
      };
    }

    destroy() {
      cancelAnimationFrame(this.attachFrame);
    }
  }

  global.CakePrototypeAdapter = CakePrototypeAdapter;
}(window));
