import pathlib
import unittest

SOURCE = pathlib.Path(__file__).resolve().parents[1] / 'app/src/main/java/com/metallic/chiaki/stream/FilterVideoView.kt'

class FilterResumeRegression(unittest.TestCase):
    def test_resume_delivers_surface_created_before_activation(self):
        text = SOURCE.read_text()
        resume = text.split('fun resumeVideo()', 1)[1].split('fun pauseVideo()', 1)[0]
        self.assertIn('deliverInput(renderer.currentSurface())', resume)

    def test_pause_waits_for_gl_thread_before_releasing_input(self):
        text = SOURCE.read_text()
        pause = text.split('fun pauseVideo()', 1)[1].split('private inner class', 1)[0]
        self.assertNotIn('queueEvent { renderer.releaseInput() }', pause)
        self.assertLess(pause.index('onPause()'), pause.index('renderer.releaseInput()'))

if __name__ == '__main__':
    unittest.main()
