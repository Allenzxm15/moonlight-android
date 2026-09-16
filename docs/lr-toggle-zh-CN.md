# Artemis L/R 修改说明

本分支复现本地试用包 `20.2.6-lr-toggle-fix1` 的源码修改，方便逐行查看。
上游为 [ClassicOldSong/moonlight-android](https://github.com/ClassicOldSong/moonlight-android)，
基线提交为 [`3397ec7750969466ad8983364ee1a33182bbffa1`](https://github.com/ClassicOldSong/moonlight-android/commit/3397ec7750969466ad8983364ee1a33182bbffa1)。
这是 `moonlight-noir` 分支的一份快照，虽然版本字段写着 `20.2.6`，它不等同于 `v20.2.6` 标签。

## 使用效果

- 特殊按键布局新增 `L/R`。点一次进入单点触控的左右鼠标键互换模式，按钮保持高亮；再点一次恢复普通单点触控。
- 从串流菜单改变鼠标模式时，`L/R` 高亮会同步更新。重新创建按键布局时也会读取当前模式。
- 开启“粘滞修饰键”时，特殊按键布局里的内置 Shift 改成轻点一次锁定、再点一次并抬手释放。Ctrl/Alt 保留原有长按锁定行为。
- 锁定型按钮移动手指时不会滑入其他虚拟键；锁定状态下抬手会取消待执行的长按回调。

这里的 Shift 修改位于“特殊按键”控件中，未修改另一套虚拟全键盘实现。
`L/R` 从其他鼠标模式启动时也会进入互换模式，再点击回到普通单点触控，不会恢复之前的触摸板或多点触控模式。

## 提交与文件对应

| 提交 | 文件 | 内容 |
| --- | --- | --- |
| `feat(input)` | `app/src/main/assets/config/keyboard.json` | 新增 `L/R`，内部代码为 `1001`，元素标识为 `m_s_1001`。 |
| `feat(input)` | `app/src/main/java/com/limelight/Game.java` | 保存当前鼠标模式，在模式 1 / 5 间切换；遵循“记住鼠标模式”，通知按钮更新高亮。 |
| `feat(input)` | `KeyBoardController.java` | 拦截本地操作代码 `1001`，不将其发送为主机鼠标按钮；创建控件时同步状态。 |
| `feat(input)` | `KeyBoardDigitalButton.java` | 增加外部同步锁定状态的方法，并调整切换顺序。 |
| `feat(input)` | `KeyBoardControllerConfigurationLoader.java` | 内置 Shift 在粘滞选项开启时采用轻点锁定。 |
| `fix(input)` | `KeyBoardDigitalButton.java` | 修复锁定按钮滑键后可能遗漏其他按键释放，以及抬手遗漏取消长按回调的问题。 |
| `docs(build)` | `app/build.gradle` | 调试包显示为 `Artemis L/R`，版本后缀为 `-lr-toggle-fix1`。 |
| `docs(build)` | `gradle.properties` | 保留本地构建使用的 `android.overridePathCheck=true`。 |

表中三个 `KeyBoard*.java` 文件均在
`app/src/main/java/com/limelight/binding/input/virtual_controller/keyboard/` 下。

第二条提交单独保留了试用后追加的卡键修复，便于查看第一版与 fix1 的差异。
日常使用请从分支最终提交构建，不使用第一条功能提交的中间版本。

## 设置

1. 安装调试包，打开 `Artemis L/R`，与 Sunshine 配对并串流 Desktop。
2. 在“设置 → 特殊按键布局”中开启“屏幕显示虚拟按键”。
3. 使用屏幕上的按键设置按钮，依次进入启用/禁用、移动、缩放、保存退出，将 `L/R` 与 Shift 放到顺手的位置。
4. 在“界面设置”中保持“粘滞修饰键”开启。

应用包名沿用上游调试包的 `com.limelight.noirdebug`，可与正式 Artemis 共存。
APK 的签名密钥属于本地构建环境，没有提交到本仓库；其他环境构建的 APK 不保证可覆盖已有的本地试用包。

## 构建与验证记录

原工作目录已经完成 `app:assembleNonRoot_gameDebug` 构建，并校验 ARM64 APK 的包名、版本与签名。
本分支的七个应用源码/构建文件与该 fix1 源码逐文件核对，保留上游各文件的换行风格以减少无关差异。
这不代表完成了真实平板上的输入回归测试；用户此前报告过第一版持续输入字符，fix1 的实际效果仍需在设备上验证。

测试建议：重复点击 `L/R`，在按钮上稍微滑动后抬手；确认没有额外字符、左右键模式和高亮一致；再检查 Shift 锁定/释放。
还应检查菜单切换模式后按钮高亮、关闭粘滞选项时的 Shift、Ctrl/Alt 长按操作。

本次修改只在客户端；未修改 Sunshine。
分支没有上传 APK、SDK、NDK、Gradle 缓存、签名密钥，也没有改变原生子模块的提交。

### 本地所用工具版本

- JDK 17
- Gradle 8.13
- Android SDK Platform 36
- Android NDK `27.0.12077973`

获取完整源码需初始化递归子模块：

```sh
git submodule update --init --recursive
./gradlew app:assembleNonRoot_gameDebug
```

Windows 可使用 `gradlew.bat`。在含中文的目录下，`android.overridePathCheck=true` 只能跳过 Gradle 的路径检查，
不能解决 NDK `make` 的路径编码问题；建议在纯英文路径构建。本地制作试用包时使用了临时盘符映射。

基线锁定的原生依赖为：

- `moonlight-common-c`: `c999436858471dfefa7617af3b7dc03ec1644ce4`
- 其 ENet 子模块：`115a10baa1d7f291ff5b870765610fd3b4a6e43c`

GitHub 的源码 ZIP 不包含子模块实际内容，因此构建时不能仅解压 ZIP。
