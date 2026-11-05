import pathlib
import unittest

ROOT = pathlib.Path(__file__).resolve().parents[1] / 'app/src'

class StreamFilterOverlayTest(unittest.TestCase):
    def test_shared_panel_is_used_by_stream_and_sample(self):
        stream = (ROOT / 'main/java/com/metallic/chiaki/stream/StreamActivity.kt').read_text()
        sample = (ROOT / 'main/java/com/metallic/chiaki/stream/FilterSampleActivity.kt').read_text()
        self.assertIn('FilterOverlay', stream)
        self.assertIn('FilterOverlay', sample)

    def test_modal_input_and_debug_launch_are_wired(self):
        stream = (ROOT / 'main/java/com/metallic/chiaki/stream/StreamActivity.kt').read_text()
        inp = (ROOT / 'main/java/com/metallic/chiaki/session/StreamInput.kt').read_text()
        manifest = (ROOT / 'debug/AndroidManifest.xml').read_text() if (ROOT / 'debug/AndroidManifest.xml').exists() else ''
        self.assertIn('setFilterInputBlocked(true)', stream)
        self.assertIn('setFilterInputBlocked(false)', stream)
        self.assertIn('if (filterInputBlocked)', inp)
        self.assertIn('.stream.FilterSampleLauncherActivity', manifest)

    def test_recreated_activity_resets_retained_input_block(self):
        stream = (ROOT / 'main/java/com/metallic/chiaki/stream/StreamActivity.kt').read_text()
        init = stream.split('val modal = FrameLayout', 1)[1].split('binding.filterButton.setOnClickListener', 1)[0]
        self.assertIn('viewModel.input.setFilterInputBlocked(false)', init.split('filterOverlay = FilterOverlay', 1)[0])

    def test_sensor_events_are_ignored_while_modal_open(self):
        inp = (ROOT / 'main/java/com/metallic/chiaki/session/StreamInput.kt').read_text()
        sensor = inp.split('override fun onSensorChanged(event: SensorEvent)', 1)[1].split('when(event.sensor.type)', 1)[0]
        self.assertIn('if (filterInputBlocked) return', sensor)

    def test_focused_filter_activation_precedes_remote_dispatch(self):
        stream = (ROOT / 'main/java/com/metallic/chiaki/stream/StreamActivity.kt').read_text()
        dispatch = stream.split('override fun dispatchKeyEvent(event: KeyEvent): Boolean', 1)[1].split('override fun dispatchGenericMotionEvent', 1)[0]
        self.assertLess(dispatch.index('binding.filterButton.hasFocus()'), dispatch.index('viewModel.input.dispatchKeyEvent(event)'))
        self.assertIn('KeyEvent.KEYCODE_BUTTON_A', dispatch)
        self.assertIn('binding.filterButton.isShown', dispatch)

    def test_controller_shortcut_opens_panel_without_swallowing_short_press(self):
        stream = (ROOT / 'main/java/com/metallic/chiaki/stream/StreamActivity.kt').read_text()
        dispatch = stream.split('override fun dispatchKeyEvent(event: KeyEvent): Boolean', 1)[1].split('override fun dispatchGenericMotionEvent', 1)[0]
        self.assertIn('trackFilterShortcut(event)', dispatch)
        self.assertIn('viewModel.input.dispatchKeyEvent(event)', dispatch)
        self.assertIn('KeyEvent.KEYCODE_BUTTON_SELECT', stream)
        self.assertIn('binding.filterButton.performClick()', stream)
        self.assertIn('removeCallbacks(openFilterShortcut)', stream)

    def test_closing_panel_returns_focus_to_gameplay_not_filter_button(self):
        stream = (ROOT / 'main/java/com/metallic/chiaki/stream/StreamActivity.kt').read_text()
        close = stream.split('filterOverlay = FilterOverlay(this) {', 1)[1].split('\n\t\t}', 1)[0]
        self.assertIn('returnFocusToGameplay(binding.root)', close)
        self.assertNotIn('binding.filterButton.requestFocus()', close)

    def test_blocking_clears_sensor_state_before_notifying(self):
        inp = (ROOT / 'main/java/com/metallic/chiaki/session/StreamInput.kt').read_text()
        block = inp.split('fun setFilterInputBlocked(blocked: Boolean)', 1)[1].split('fun observe(', 1)[0]
        self.assertLess(block.index('sensorControllerState = ControllerState()'), block.index('touchControllerState = ControllerState()'))

    def test_all_six_are_present_and_reset(self):
        panel = (ROOT / 'main/java/com/metallic/chiaki/stream/FilterOverlay.kt').read_text()
        for name in ('brightness', 'saturation', 'gamma', 'contrast', 'hue', 'sharpness'):
            self.assertIn('filter_' + name, panel)
        self.assertIn('putFilterInt(spec.key, 0)', panel)

if __name__ == '__main__':
    unittest.main()
