# 蛋糕动画节奏编辑器 / Cake Animation Timing Editor

独立的 Three.js 蛋糕组装动画调参工具。可预览简单、普通、困难三种蛋糕，编辑各层支撑、主体、小零件与蜡烛的开始时间、时长倍率和层内错峰。

A standalone Three.js editor for cake assembly timing. Preview all three difficulties and adjust support, body, small-part, and candle tracks, including start times, duration scales, and staggering.

## 运行 / Run

在本目录执行 / Run from this directory:

```bash
python3 -m http.server 4173 --bind 127.0.0.1
```

打开 / Open: `http://127.0.0.1:4173/`

## 使用 / Usage

1. 选择难度，点击时间轴轨道。 / Select a difficulty and a timeline track.
2. 调节参数，拖动播放头或重播观察组装。 / Adjust parameters, scrub, or replay the assembly.
3. 使用导入、复制、下载保存或恢复 `schemaVersion: 1` 的 JSON 配置。 / Import, copy, or download a `schemaVersion: 1` JSON configuration.
4. 将采用的参数同步到 Android 源码 `CakeAssemblyTimeline.kt`。 / Apply the chosen parameters to `CakeAssemblyTimeline.kt` in the Android source.

## 文件 / Files

| 文件 / File | 用途 / Purpose |
| --- | --- |
| `index.html`、`styles.css` | 编辑器界面 / Editor UI |
| `app.js` | 时间轴与交互 / Timeline and interactions |
| `config.js` | 默认配置、结构校验与时间计算 / Defaults, validation, and timing |
| `runtime-adapter.js` | 预览节点与时间轴桥接 / Preview and timeline bridge |
| `prototype.html` | 内嵌 Three.js 的蛋糕预览 / Cake preview with embedded Three.js |

`prototype.html` 保留内嵌 Three.js 的 MIT 许可证声明。 / `prototype.html` retains the MIT license notice for embedded Three.js.
