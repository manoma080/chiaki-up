import pathlib
import unittest
import xml.etree.ElementTree as ET

ROOT = pathlib.Path(__file__).resolve().parents[1] / 'app/src/main'

class SurfaceLifecycleRegression(unittest.TestCase):
    def test_replacement_notifies_consumer_before_retiring_old_input(self):
        text = (ROOT / 'java/com/metallic/chiaki/stream/FilterVideoView.kt').read_text()
        created = text.split('override fun onSurfaceCreated', 1)[1].split('fun releaseInput()', 1)[0]
        self.assertNotIn('releaseInput()', created)
        self.assertLess(created.index('onInputSurface?.invoke(input)'), created.index('oldSurface?.release()'))
        self.assertLess(created.index('onInputSurface?.invoke(input)'), created.index('oldTexture?.release()'))

    def test_decoder_checks_surface_swap_and_handles_shutdown(self):
        text = (ROOT / 'cpp/video-decoder.c').read_text()
        self.assertIn('media_status_t swap_result = AMediaCodec_setOutputSurface', text)
        self.assertIn('swap_result != AMEDIA_OK', text)
        self.assertIn('kill_decoder_locked(decoder)', text)
        self.assertIn('chiaki_thread_join(&decoder->output_thread, NULL)', text)
        self.assertIn('decoder->shutdown_output || !decoder->codec', text)

    def test_decoder_serializes_surface_teardown_across_join(self):
        text = (ROOT / 'cpp/video-decoder.c').read_text()
        surface = text.split('void android_chiaki_video_decoder_set_surface', 1)[1].split('bool android_chiaki_video_decoder_video_sample', 1)[0]
        self.assertLess(surface.index('chiaki_mutex_lock(&decoder->lifecycle_mutex)'), surface.index('chiaki_mutex_lock(&decoder->codec_mutex)'))
        self.assertGreater(surface.index('chiaki_mutex_unlock(&decoder->lifecycle_mutex)'), surface.index('chiaki_mutex_unlock(&decoder->codec_mutex)'))

    def test_surface_and_dispose_share_session_lifetime_lock(self):
        text = (ROOT / 'java/com/metallic/chiaki/lib/Chiaki.kt').read_text().split('class Session(', 1)[1].split('class ', 1)[0]
        self.assertIn('@Synchronized\n\tfun dispose()', text)
        self.assertIn('@Synchronized\n\tfun setSurface(surface: Surface?)', text)
        self.assertIn('if(nativePtr != 0L)', text.split('fun setSurface', 1)[1])

    def test_sample_activity_not_exposed_in_production(self):
        tree = ET.parse(ROOT / 'AndroidManifest.xml')
        android = '{http://schemas.android.com/apk/res/android}'
        activities = tree.findall('./application/activity')
        sample = [a for a in activities if a.get(android + 'name') == '.stream.FilterSampleActivity']
        self.assertTrue(not sample or sample[0].get(android + 'exported') == 'false')

if __name__ == '__main__':
    unittest.main()
