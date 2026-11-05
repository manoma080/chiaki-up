import pathlib
import unittest

ROOT = pathlib.Path(__file__).resolve().parents[1] / 'app/src/main/java/com/metallic/chiaki'


def section(source, start, end):
    return source.split(start, 1)[1].split(end, 1)[0]


class ResizeOverlayInteractionTest(unittest.TestCase):
    def test_input_neutralization_and_blocking_coexist(self):
        source = (ROOT / 'session/StreamInput.kt').read_text()
        self.assertNotIn('<<<<<<<', source)
        self.assertIn('private var sensorControllerState', source)
        blocked = section(source, 'fun setFilterInputBlocked(', 'fun observe(')
        for state in ('sensorControllerState', 'keyControllerState', 'motionControllerState', 'touchControllerState'):
            self.assertIn(state + ' = ControllerState()', blocked)
        release = section(source, 'fun releaseControls(', 'fun dispatchKeyEvent(')
        for state in ('keyControllerState', 'motionControllerState', 'touchControllerState'):
            self.assertIn(state + ' = ControllerState()', release)
        self.assertIn('if (filterInputBlocked) return false', section(source, 'fun dispatchKeyEvent(', 'fun onGenericMotionEvent('))
        self.assertIn('if (filterInputBlocked) return false', source.split('fun onGenericMotionEvent(', 1)[1])

    def test_open_overlay_clears_fragment_state_before_unblock(self):
        activity = (ROOT / 'stream/StreamActivity.kt').read_text()
        opening = section(activity, 'binding.filterButton.setOnClickListener {', 'window.decorView.setOnSystemUiVisibilityChangeListener')
        self.assertIn('setFilterInputBlocked(true)', opening)
        self.assertIn('it.releaseControls()', opening)
        self.assertLess(opening.index('setFilterInputBlocked(true)'), opening.index('it.releaseControls()'))

    def test_touchpad_cancel_uses_one_complete_release_path(self):
        source = (ROOT / 'touchcontrols/TouchpadView.kt').read_text()
        on_touch = section(source, 'override fun onTouchEvent(', 'private fun triggerShortButtonPress(')
        self.assertEqual(1, on_touch.count('MotionEvent.ACTION_CANCEL'))
        self.assertIn('MotionEvent.ACTION_CANCEL -> releaseControls()', on_touch)

    def test_recreation_unblocks_hidden_overlay_and_retains_session(self):
        activity = (ROOT / 'stream/StreamActivity.kt').read_text()
        create = section(activity, 'override fun onCreate(', 'private val controlsDisposable')
        self.assertIn('viewModel.input.setFilterInputBlocked(false)', create)
        stop = section(activity, 'override fun onStop(', 'override fun onDestroy(')
        self.assertIn('!isChangingConfigurations', stop)
        self.assertIn('viewModel.session.pause()', stop)


if __name__ == '__main__':
    unittest.main()
