# 肺活量大挑战

简体中文 | [English](README.md)

![肺活量大挑战海报](output/artwork/lung-capacity-poster.jpg)

**肺活量大挑战**是一款面向 PICO 的麦克风驱动混合现实游戏。玩家向头显麦克风吹气生成气泡，通过头部朝向控制气泡方向，在程序化生成的 3D 蛋糕上熄灭全部蜡烛。

## 玩法

1. 选择三种挑战难度之一：
   - **简单**：单层蛋糕，共 8 根蜡烛。
   - **普通**：双层蛋糕，共 16 根蜡烛。
   - **困难**：三层蛋糕，共 24 根蜡烛。
2. 进入空间中的蛋糕挑战场景。
3. 向麦克风吹气，沿当前头部朝向生成气泡。
4. 追踪不同高度和方向的蜡烛，必要时旋转蛋糕，尽快熄灭全部火焰。
5. 完成挑战获得金币，在成长树中升级力度、密度、扩散、悬浮、蓄压、气泡分裂、偏流、二次绽放和熄灭余波。

## 开发方式与技术栈

项目使用 TRAE，配合 GPT 6.1 SOL，通过 Vibe Coding 完成开发。

开发时使用 pico-cli 查询设备、安装和启动 APK、采集 Spatial 截图以及读取运行日志。相关开发资料参考 [PICO 开发者资源](https://developer-cn.picoxr.com/resources/)。

| 领域 | 技术 |
| --- | --- |
| 应用 | Android、Kotlin 2.1.20 |
| 空间运行时 | PICO Spatial SDK 6.2.8 |
| 界面 | PICO SpatialUI、Jetpack Compose |
| 3D 场景 | 程序化网格、PBR 材质与纹理 |
| 输入 | 麦克风输入、头部追踪、空间拖拽 |
| 测试 | JUnit、Android 设备测试 |
| 开发工具 | pico-cli |
| 构建 | Gradle 8.13、Android Gradle Plugin 8.13.2 |

主菜单和成长树显示在窗口中，吹气挑战在 Mixed Stage 中展开。

## 模型与素材

蛋糕以参考图片为视觉依据，通过程序化网格生成层体、糖霜、水果装饰和蜡烛，再用材质与纹理表现奶油和水果的表面细节。组装动画让各层和装饰依次出现，火焰与烟雾随蜡烛状态变化。

建模和动画制作参考了 [img2threejs](https://github.com/hoainho/img2threejs) 的图像几何重建方法，以及 [threejs-architecture-effects](https://github.com/lhlGitHub/threejs-architecture-effects) 的分阶段组装效果。

游戏图标和海报位于 [`output/artwork`](output/artwork/)。

## 动画调参工具

项目附带独立的[蛋糕动画编辑器](tools/cake-animation-editor/)。它可以在浏览器中预览三种蛋糕，并通过时间轴调整各层部件的开始时间、时长和出现间隔。调参结果可导出为 JSON，供调整安卓端动画时参考。具体用法见[编辑器说明](tools/cake-animation-editor/README.md)。

在仓库根目录启动本地 HTTP 服务：

```bash
python3 -m http.server 4173
```

然后打开：

```text
http://localhost:4173/tools/cake-animation-editor/
```

## 构建与运行

准备 JDK 17 和 Android SDK Platform 35，通过 `ANDROID_HOME` 指向 SDK 目录。在仓库根目录构建 Release：

```bash
./gradlew assembleRelease
```

产物为 `app/build/outputs/apk/release/app-release-unsigned.apk`。向 PICO Vibe Lab 提交 GitHub 仓库地址和 `git rev-parse HEAD` 输出的完整 40 位提交编号，APK 由平台统一签名。

本地测试与 Debug 构建：

```bash
./gradlew :app:testDebugUnitTest :app:assembleDebug --console=plain
```

安装并初始化 pico-cli，然后安装和启动 Debug APK：

```bash
npm install -g @picoxr/pico-cli && pico-cli setup
pico-cli app install app/build/outputs/apk/debug/app-debug.apk
pico-cli app launch com.example.lungcapacity.pcda76db4 \
  --activity .platform.LaunchActivity
```

## 目录

```text
app/src/main/                  Android 与 PICO Spatial 应用源码
app/src/test/                  JVM 单元测试
app/src/androidTest/           PICO 设备测试
tools/cake-animation-editor/   独立蛋糕动画调参页面
output/artwork/                公开游戏图标与海报
```

## 许可证

项目采用 [MIT 许可证](LICENSE)。蛋糕动画编辑器内嵌的 Three.js 保留其原有 MIT 许可声明。
