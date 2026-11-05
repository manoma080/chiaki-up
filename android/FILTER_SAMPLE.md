# Stream filter controls

While streaming, hold the controller's **Select / Share / Create** button for 1.5 seconds to open the filter panel, even when the on-screen controls are hidden. A normal short press still reaches the game; the beginning of a long press also reaches the game before the panel opens. While the panel is open, use the controller to adjust the filters and press B to close it.

# Filter playback sample (debug only)

Install the debug APK, then launch the debug-only bridge:

```sh
adb shell am start -n com.gameblabla.chiaki/.stream.FilterSampleLauncherActivity
```

For builds with `-PisolatedTestApp`, use package `com.gameblabla.chiaki.filtertest` and the fully qualified component class `com.gameblabla.chiaki.stream.FilterSampleLauncherActivity`. The sample activity itself remains non-exported in every build; the bridge is absent from release builds.
