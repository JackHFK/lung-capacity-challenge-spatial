# Lung Capacity Challenge in Spatial

[简体中文](README.zh-CN.md) | English

![Lung Capacity Challenge poster](output/artwork/lung-capacity-poster.jpg)

**Lung Capacity Challenge in Spatial** is a microphone-driven mixed-reality game built for PICO. Players blow into the headset microphone to create streams of bubbles, aim them through head movement, and extinguish every candle on a procedural 3D cake.

## Gameplay

1. Choose one of three challenges:
   - **Easy**: 8 candles on a single-tier cake.
   - **Normal**: 16 candles across two tiers.
   - **Hard**: 24 candles across three tiers.
2. Enter the spatial cake challenge.
3. Blow into the microphone to emit bubbles along your current head direction.
4. Track the candle positions, rotate the cake when needed, and extinguish all flames as quickly as possible.
5. Earn coins from completed challenges and spend them in the growth tree to improve force, density, spread, lifetime, charge, bubble splitting, drift, bloom, and extinguish-wave effects.

## Development and Technology

The project was developed through Vibe Coding using TRAE with GPT 6.1 SOL.

Development used pico-cli to list devices, install and launch APKs, capture Spatial screenshots, and read runtime logs. Development references include the [PICO developer resources](https://developer-cn.picoxr.com/resources/).

| Area | Technology |
| --- | --- |
| Application | Android, Kotlin 2.1.20 |
| Spatial runtime | PICO Spatial SDK 6.2.8 |
| UI | PICO SpatialUI, Jetpack Compose |
| 3D scenes | Procedural meshes, PBR materials and textures |
| Input | Microphone input, head tracking, spatial drag gestures |
| Testing | JUnit and Android instrumentation tests |
| Development tools | pico-cli |
| Build | Gradle 8.13, Android Gradle Plugin 8.13.2 |

The main menu and growth tree appear in a window, while the blowing challenge takes place in a Mixed Stage.

## Models and Artwork

The cakes are modeled from reference images. Procedural meshes form the tiers, icing, fruit decorations, and candles, while materials and textures provide cream and fruit surface details. Assembly animations bring in the layers and decorations in sequence, and flames and smoke respond to candle state.

Modeling and animation were informed by the image-to-geometry methods in [img2threejs](https://github.com/hoainho/img2threejs) and staged assembly effects in [threejs-architecture-effects](https://github.com/lhlGitHub/threejs-architecture-effects).

The game icon and poster are in [`output/artwork`](output/artwork/).

## Animation Tuning Tool

The project includes a standalone [cake animation editor](tools/cake-animation-editor/). Preview all three cakes in a browser and adjust the start time, duration, and staggering of each layer's parts on a timeline. Export settings as JSON for reference when adjusting the Android animation. See the [editor README](tools/cake-animation-editor/README.md) for details.

Run it from the repository root with a local HTTP server:

```bash
python3 -m http.server 4173
```

Then open:

```text
http://localhost:4173/tools/cake-animation-editor/
```

## Build and Run

Install and set up pico-cli:

```bash
npm install -g @picoxr/pico-cli && pico-cli setup
```

Build the APK:

```bash
./gradlew :app:testDebugUnitTest :app:assembleDebug --console=plain
```

Install and launch with pico-cli:

```bash
pico-cli app install app/build/outputs/apk/debug/app-debug.apk
pico-cli app launch com.example.lungcapacity.pcda76db4 \
  --activity .platform.LaunchActivity
```

## Layout

```text
app/src/main/                  Android and PICO Spatial application source
app/src/test/                  JVM unit tests
app/src/androidTest/           PICO device instrumentation tests
tools/cake-animation-editor/   Standalone cake animation tuning page
output/artwork/                Public game icon and poster
```

## License

This project is licensed under the [MIT License](LICENSE). The cake animation editor retains the original MIT license notice for its embedded Three.js code.
