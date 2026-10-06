# SkyboxSpatial

A PICO OS 6 app: describe a place in one sentence, generate a static 360° panorama with the Skybox AI
service, preview it in a Planar window, enter it as a Full Space, and keep favourites on the device.

- Package / applicationId: `com.kejing.skyboxspatial`
- PICO Spatial SDK BOM 6.1.9, project layout copied from Element Space (same Gradle setup)
- Design (Figma): https://www.figma.com/design/PJJhWi8tdte203bSA23FHG (PRD frame "00" + 17 screens)
- Generation API: Blockade Labs Skybox AI, https://api-documentation.blockadelabs.com/api/skybox.html

## Containers

| Container | Declared in | Notes |
| --- | --- | --- |
| Main window `SkyboxMainWindow` | `DefaultWindowContainer` in `Main.kt`, size and glass in `AndroidManifest.xml` | Planar, 1040 x 760 dp, system glass. Create / library / settings behind one icon title bar |
| Stage `SkyboxStage` | `Stage(id = …)` in `Main.kt` | Opened with `StageStyle.Full`. A 30 m lat-long sphere with the panorama as an unlit texture, plus an `AttachmentPanelComponent` console |

The main window stays visible while the stage is open (platform behaviour); its Enter button turns
into Exit, and the floating console in the stage offers save / turn / reset heading / language / exit.

## Layout of the code

```
AppGraph.kt            process-wide singletons (settings, library, API client, StageSession)
data/api/              SkyboxApi interface + BlockadeSkyboxApi (HttpURLConnection + org.json, no extra deps)
data/local/            SkyboxLibraryStore (files/library/<id>/{image.jpg,thumb.jpg,meta.json}, atomic rename,
                       MediaStore copy in Pictures/SkyboxSpatial), ImageFiles (bounded decoding)
data/prefs/            SettingsStore (API key, language, style, last prompt, pending job id)
domain/                models, PromptRules (length, title derivation)
ui/main/               MainUiState / MainEvent / MainViewModel / MainScreen + components (pages)
ui/stage/              StageSession hand-off, SkySphereScene (ECS), SkyboxStageScreen (console)
ui/theme/              Noto typography (Noto Sans SC for zh, Noto Sans for en), in-app locale
ui/navigation/         container ids, DemoMode (emulator scripting)
```

## Rules to keep

- All 2D UI is SpatialUI inside `PicoTheme`; no Material imports. Colours come from
  `PicoTheme.colorScheme` except the stage console, which uses the design's light ink/paper.
- Icons where an icon is enough (title-bar nav, save, edit, details, remove, turn, reset); the one
  emphasised action per page is a `PrimaryButton` (accent fill, icon + label).
- Text comes from `res/values` (English) and `res/values-zh`; read it with `str(...)`.
- Composables never touch a store directly; the main window goes through `MainViewModel`. The stage
  screen is the exception by design: it saves through `AppGraph.library` because it is a different
  container with no shared ViewModel.
- Generation rules from the PRD: one submission at a time (button disabled while busy), an
  unconfirmed submit is never retried automatically (`UnknownSubmit` looks the task up first), a lost
  connection re-polls the same task, a failed download only re-downloads, a failed save adds nothing,
  and every failure keeps the description. Saving the same generation twice returns the existing entry.
- Refining: after a result, the next generation passes `remix_imagine_id` when the style is Model 3
  and the switch under the prompt is on. Model 4 styles have no remix; the switch hides.
- Resolution: the native `file_url` is used (Model 3 = 8192 x 4096). `ImageFiles.decodeForStage`
  decodes at full size and halves on OutOfMemoryError.

## Build and check

```bash
./gradlew :app:assembleDebug --offline
./gradlew :app:testDebugUnitTest --offline
```

`JAVA_HOME` must point at Android Studio's JBR. `--offline` works because every dependency is the
same as Element Space and already cached; drop it when adding a dependency.

Emulator scripting (nobody can click in the emulator from the CLI):

```bash
adb push docs/test-assets/test-panorama.jpg /data/local/tmp/demo.jpg   # in Git Bash: MSYS_NO_PATHCONV=1
adb shell "run-as com.kejing.skyboxspatial cp /data/local/tmp/demo.jpg files/demo.jpg"
adb shell settings put global skyboxspatial_demo stage:demo.jpg       # or preview:demo.jpg
adb shell settings delete global skyboxspatial_demo
```

## Known gaps (as of 2026-10-06)

- Never run with a real API key: the request/response shapes follow the published docs and were not
  exercised against the service. First thing to test on a headset: paste a key in settings, load
  styles, generate.
- Verified on the PICO emulator 6.1 only via demo mode: main window, preview page, opening the Full
  stage, the sky sphere (orientation and mirroring checked with `docs/test-assets/test-panorama.jpg`:
  the image centre faces forward and text reads correctly). The emulator screenshot composites only
  the upper part of a Full stage, so the floating console was confirmed only through the system log
  (its panel window is created) and not seen; check its placement (`Vector3(0f, 1.2f, -1.6f)`, stage
  origin is at floor level) on a device.
- Head pose is not exposed by the SDK, so "reset heading" resets the panorama's yaw offset rather than
  re-centring on the user's gaze; the console also has turn-left / turn-right (30° steps).
- Clipboard: the paste icon reads the Android clipboard, which the PICO browser fills when the user
  copies the key on the web page. Typing with the system keyboard also works.
- `MediaStore` export is best effort: if it fails the library entry still exists (without the folder copy).
- Fonts: `NotoSans-VF.ttf` and `NotoSansSC-VF.ttf` are bundled under `assets/fonts` (OFL texts alongside).
