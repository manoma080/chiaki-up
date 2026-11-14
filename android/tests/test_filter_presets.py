import pathlib
import unittest

ROOT = pathlib.Path(__file__).resolve().parents[1] / 'app/src/main'

class FilterPresetsTest(unittest.TestCase):
    def test_overlay_exposes_persistent_presets_and_confirmations(self):
        source = (ROOT / 'java/com/metallic/chiaki/stream/FilterOverlay.kt').read_text()
        self.assertIn('FilterPresetStore(context)', source)
        self.assertIn('setOnLongClickListener', source)
        self.assertIn('R.string.filter_preset_delete_confirm', source)
        self.assertIn('R.string.filter_preset_overwrite_confirm', source)
        self.assertLess(source.index('presetContainer ='), source.index('R.string.filter_save_preset'))
        self.assertLess(source.index('R.string.filter_save_preset'), source.index('R.string.filter_reset_all'))

if __name__ == '__main__':
    unittest.main()
