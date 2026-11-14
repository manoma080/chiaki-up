import pathlib
import unittest

ROOT = pathlib.Path(__file__).resolve().parents[1] / 'app/src/main/java/com/metallic/chiaki/stream'


def method(source, name, next_name):
    return source.split('fun ' + name + '(', 1)[1].split('fun ' + next_name + '(', 1)[0]


class StreamResizeLifecycleTest(unittest.TestCase):
    def test_pause_detaches_video_without_stopping_connection(self):
        activity = (ROOT / 'StreamActivity.kt').read_text()
        pause = method(activity, 'onPause', 'onStop')
        self.assertIn('binding.surfaceView.pauseVideo()', pause)
        self.assertNotIn('viewModel.session.pause()', pause)

    def test_stop_preserves_configuration_change_but_pauses_background(self):
        activity = (ROOT / 'StreamActivity.kt').read_text()
        stop = method(activity, 'onStop', 'onDestroy')
        self.assertIn('!isChangingConfigurations', stop)
        self.assertIn('viewModel.session.pause()', stop)

    def test_restored_controls_are_bound_after_viewmodel_initialization(self):
        activity = (ROOT / 'StreamActivity.kt').read_text()
        self.assertIn('if(!::viewModel.isInitialized) return', method(activity, 'onAttachFragment', 'onResume'))
        self.assertIn('supportFragmentManager.fragments', method(activity, 'onCreate', 'onAttachFragment'))

    def test_focus_loss_and_pause_release_controller_input(self):
        activity = (ROOT / 'StreamActivity.kt').read_text()
        self.assertIn('viewModel.input.releaseControls()', method(activity, 'onPause', 'onStop'))
        self.assertIn('viewModel.input.releaseControls()', method(activity, 'onWindowFocusChanged', 'hideSystemUI'))
        input_source = (ROOT.parent / 'session/StreamInput.kt').read_text()
        release = method(input_source, 'releaseControls', 'dispatchKeyEvent')
        for state in ('keyControllerState', 'motionControllerState', 'touchControllerState'):
            self.assertIn(state + ' = ControllerState()', release)
        self.assertIn('controllerStateUpdated()', release)

    def test_viewmodel_clears_actual_session(self):
        viewmodel = (ROOT / 'StreamViewModel.kt').read_text()
        cleared = method(viewmodel, 'onCleared', 'setOnScreenControlsEnabled')
        self.assertIn('session.shutdown()', cleared)
        self.assertNotIn('_session', viewmodel)


if __name__ == '__main__':
    unittest.main()
