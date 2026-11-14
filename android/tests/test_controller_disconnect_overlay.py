import pathlib
import unittest

SOURCE = pathlib.Path(__file__).resolve().parents[1] / 'app/src/main/java/com/metallic/chiaki/stream/StreamActivity.kt'


class ControllerDisconnectOverlayTest(unittest.TestCase):
    def test_disconnect_does_not_show_or_pin_overlay(self):
        stream = SOURCE.read_text()
        listener = stream.split('private val controllerListener', 1)[1].split('private val uiVisibilityHandler', 1)[0]
        on_start = stream.split('override fun onStart()', 1)[1].split('override fun onSaveInstanceState', 1)[0]
        hide = stream.split('private fun hideOverlay()', 1)[1].split('override fun onWindowFocusChanged', 1)[0]
        self.assertIn('releaseGamepadControls()', listener)
        self.assertIn('cancelFilterShortcut()', listener)
        for section in (listener, on_start):
            self.assertNotIn('showOverlay()', section)
            self.assertNotIn('updateControllerNotice', section)
        self.assertNotIn('controllers.isDisconnected', hide)
        self.assertNotIn('controllerNotice', stream)


if __name__ == '__main__':
    unittest.main()
