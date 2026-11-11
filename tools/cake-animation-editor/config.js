(function defineCakeEditorConfig(global) {
  "use strict";

  const SCHEMA_VERSION = 1;
  const DIFFICULTY_KEYS = ["easy", "medium", "hard"];
  const GROUP_KEYS = ["support", "largeBodies", "smallParts", "candles"];
  const SOURCE_PACE_SECONDS = Object.freeze({
    easy: 3.4,
    medium: 4.2,
    hard: 5,
  });
  const DURATION_FRACTIONS = Object.freeze({
    support: 0.05,
    largeBodies: 0.05,
    smallParts: 0.05,
    candles: 0.07,
  });

  const DIFFICULTY_META = Object.freeze({
    easy: { label: "简单", tierCount: 1, level: 1, title: "简单 · 单层草莓奶油蛋糕" },
    medium: { label: "普通", tierCount: 2, level: 2, title: "普通 · 两层复古奶油蛋糕" },
    hard: { label: "困难", tierCount: 3, level: 3, title: "困难 · 三层浆果奶油蛋糕" },
  });

  function track(id, tierIndex, startSeconds, durationScale) {
    return { id, tierIndex, startSeconds, durationScale };
  }

  function tierTracks(type, starts, scales) {
    const suffixes = {
      largeBodies: "large-body",
      smallParts: "small-parts",
      candles: "candles",
    };
    return starts.map((startSeconds, tierIndex) => track(
      `tier-${tierIndex}-${suffixes[type]}`,
      tierIndex,
      startSeconds,
      Array.isArray(scales) ? scales[tierIndex] : scales,
    ));
  }

  function createDefaults() {
    return {
      schemaVersion: SCHEMA_VERSION,
      activeDifficulty: "hard",
      difficulties: {
        easy: {
          label: "简单",
          tierCount: 1,
          smallPartStaggerSeconds: 0.03,
          candleSlotStaggerSeconds: 0.163,
          groups: {
            support: [track("cake-support", -1, 0, 0.8)],
            largeBodies: tierTracks("largeBodies", [0.272], 0.8),
            smallParts: tierTracks("smallParts", [0.744], 1.44),
            candles: tierTracks("candles", [1.349], 0.7),
          },
        },
        medium: {
          label: "普通",
          tierCount: 2,
          smallPartStaggerSeconds: 0.03,
          candleSlotStaggerSeconds: 0.202,
          groups: {
            support: [track("cake-support", -1, 0, 0.8)],
            largeBodies: tierTracks("largeBodies", [0.168, 0.336], 0.8),
            smallParts: tierTracks("smallParts", [0.536, 0.704], 1.44),
            candles: tierTracks("candles", [0.898, 1.398], 0.7),
          },
        },
        hard: {
          label: "困难",
          tierCount: 3,
          smallPartStaggerSeconds: 0.03,
          candleSlotStaggerSeconds: 0.24,
          groups: {
            support: [track("cake-support", -1, 0, 0.8)],
            largeBodies: tierTracks("largeBodies", [0.2, 0.4, 0.6], 0.8),
            smallParts: tierTracks("smallParts", [0.6, 1.1, 1.6], 1.44),
            candles: tierTracks("candles", [1.86, 2.36, 2.86], 0.7),
          },
        },
      },
    };
  }

  function clone(value) {
    return JSON.parse(JSON.stringify(value));
  }

  function finiteNumber(value, path, min, max) {
    const numeric = Number(value);
    if (!Number.isFinite(numeric) || numeric < min || numeric > max) {
      throw new Error(`${path} 必须是 ${min}–${max} 之间的有限数值`);
    }
    return Math.round(numeric * 1000) / 1000;
  }

  function canonicalize(input) {
    if (!input || typeof input !== "object") {
      throw new Error("JSON 根节点必须是对象");
    }
    if (input.schemaVersion !== SCHEMA_VERSION) {
      throw new Error(`仅支持 schemaVersion=${SCHEMA_VERSION}`);
    }
    if (!input.difficulties || typeof input.difficulties !== "object") {
      throw new Error("缺少 difficulties");
    }

    const defaults = createDefaults();
    const output = createDefaults();
    output.activeDifficulty = DIFFICULTY_KEYS.includes(input.activeDifficulty)
      ? input.activeDifficulty
      : defaults.activeDifficulty;

    for (const difficultyKey of DIFFICULTY_KEYS) {
      const source = input.difficulties[difficultyKey];
      const expected = defaults.difficulties[difficultyKey];
      if (!source || typeof source !== "object") {
        throw new Error(`缺少 difficulties.${difficultyKey}`);
      }
      if (!source.groups || typeof source.groups !== "object") {
        throw new Error(`缺少 difficulties.${difficultyKey}.groups`);
      }

      const target = output.difficulties[difficultyKey];
      target.smallPartStaggerSeconds = finiteNumber(
        source.smallPartStaggerSeconds,
        `${difficultyKey}.smallPartStaggerSeconds`,
        0,
        2,
      );
      target.candleSlotStaggerSeconds = finiteNumber(
        source.candleSlotStaggerSeconds,
        `${difficultyKey}.candleSlotStaggerSeconds`,
        0,
        2,
      );

      for (const groupKey of GROUP_KEYS) {
        const sourceTracks = source.groups[groupKey];
        const expectedTracks = expected.groups[groupKey];
        if (!Array.isArray(sourceTracks)) {
          throw new Error(`${difficultyKey}.groups.${groupKey} 必须是数组`);
        }
        if (sourceTracks.length !== expectedTracks.length) {
          throw new Error(
            `${difficultyKey}.groups.${groupKey} 应包含 ${expectedTracks.length} 项`,
          );
        }

        target.groups[groupKey] = expectedTracks.map((expectedTrack) => {
          const sourceTrack = sourceTracks.find((candidate) => candidate.id === expectedTrack.id);
          if (!sourceTrack) {
            throw new Error(`${difficultyKey}.${groupKey} 缺少稳定 ID ${expectedTrack.id}`);
          }
          if (sourceTrack.tierIndex !== expectedTrack.tierIndex) {
            throw new Error(`${sourceTrack.id}.tierIndex 不可修改`);
          }
          return track(
            expectedTrack.id,
            expectedTrack.tierIndex,
            finiteNumber(sourceTrack.startSeconds, `${sourceTrack.id}.startSeconds`, 0, 30),
            finiteNumber(sourceTrack.durationScale, `${sourceTrack.id}.durationScale`, 0.25, 6),
          );
        });
      }
    }

    return output;
  }

  function staggerForGroup(difficulty, groupKey) {
    if (groupKey === "smallParts") return difficulty.smallPartStaggerSeconds;
    if (groupKey === "candles") return difficulty.candleSlotStaggerSeconds;
    return 0;
  }

  function getBaseDuration(difficultyKey, groupKey) {
    return SOURCE_PACE_SECONDS[difficultyKey] * DURATION_FRACTIONS[groupKey];
  }

  function getEffectiveDuration(difficultyKey, difficulty, groupKey, item, memberCount) {
    const baseDuration = getBaseDuration(difficultyKey, groupKey) * item.durationScale;
    const stagger = staggerForGroup(difficulty, groupKey);
    return baseDuration + Math.max(0, memberCount - 1) * stagger;
  }

  function getTimelineEnd(difficultyKey, difficulty, memberCounts) {
    let latest = 0;
    for (const groupKey of GROUP_KEYS) {
      for (const item of difficulty.groups[groupKey]) {
        const memberCount = Math.max(1, Number(memberCounts?.[item.id]) || 1);
        latest = Math.max(
          latest,
          item.startSeconds +
            getEffectiveDuration(difficultyKey, difficulty, groupKey, item, memberCount),
        );
      }
    }
    return Math.ceil((latest + 0.22) * 100) / 100;
  }

  global.CakeEditorConfig = Object.freeze({
    SCHEMA_VERSION,
    DIFFICULTY_KEYS,
    GROUP_KEYS,
    SOURCE_PACE_SECONDS,
    DURATION_FRACTIONS,
    DIFFICULTY_META,
    createDefaults,
    clone,
    canonicalize,
    staggerForGroup,
    getBaseDuration,
    getEffectiveDuration,
    getTimelineEnd,
  });
}(window));
