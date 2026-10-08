# Pipetto wrapper update

Imported range: [`60c291b9..f194ef97`](https://github.com/Pipetto-crypto/winlator/compare/60c291b9dea9c9eaf1dd5562f80eb90f3305baaa...f194ef9761ce004a6828f3409c08a67308b31019), branch `winlator_bionic`.

- Replaced `app/src/main/assets/graphics_driver/wrapper.tzst` with the exact target asset. SHA-256: `b5d76cfbfe698aa730c4f5b215506dfdaf471128581702eb3e95b87a4afb44ce`.
- Bumped the extracted graphics runtime marker so existing containers receive the wrapper.
- Ported EGL RGBA channel swapping, minimum root-window stacking order, empty hidden-window-class handling, opaque DisplayX buffers, and conditional Vulkan validation layers.
- Serialized DisplayX event mutations and composition batches with a mutex. Retained the GPU completion wait because this fork does not pass a Vulkan completion fence to Android's surface transaction. Failed conversions retain the original buffer.
- Retained equivalent existing features: FEX Unix-library trust entries; container/shortcut renderer selection; EGL surface format and nearest/bilinear filtering; DisplayX configuration and wrapper environment variables. EGL now honors the chosen drawable surface format at launch.

Validation: `./gradlew assembleDebug testDebugUnitTest --console=plain`. The source wrapper matches the target Git blob and the asset packaged in the debug APK. Rendering still needs validation on an Android device.

## October update

Imported `2180ec05..89e002fc` from `winlator_bionic`: DisplayX protocol/mailbox updates, Box64 0.4.5, and Proton 9 winedmo container patterns. Kept Box64/WoWBox64 0.4.2 and FEX 2601 available.

Updated only `usr/lib/displayx_layer.so` inside the split `extra_libs.tzst` asset; its bytes match upstream `89e002fc`. Bumped the runtime extraction marker for existing containers. Preserved this fork’s fullscreen handling, atomic FPS state, and presentation wakeups. Fixed queue removal and duplicate pending-window entries.

Decoder controls work in the legacy and Compose container/shortcut editors. Component values retain decoder strings, compare installed DLL settings by key, and apply decoder environment flags on every launch.

Validation: debug APK and unit tests pass; the host DisplayX queue check covers mailbox replacement, FIFO order, window removal, and fence closure. Device rendering and cutscene playback still need an Android device.
