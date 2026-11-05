import unittest
import xml.etree.ElementTree as ET
from pathlib import Path

RES = Path(__file__).resolve().parents[1] / 'app/src/main/res'
APP = '{http://schemas.android.com/apk/res-auto}'
ANDROID = '{http://schemas.android.com/apk/res/android}'


class VideoFilterPreferencesTest(unittest.TestCase):
    def test_filter_sliders_have_persisted_integer_ranges_and_labels(self):
        root = ET.parse(RES / 'xml/preferences.xml').getroot()
        strings = {s.get('name'): s.text for s in ET.parse(RES / 'values/strings.xml').getroot().findall('string')}
        category = next((c for c in root.findall('PreferenceCategory')
                         if c.get(APP + 'key') == 'category_video_filter'), None)
        self.assertIsNotNone(category, 'Video filter category is missing')
        self.assertEqual(strings[category.get(APP + 'title').removeprefix('@string/')], 'Video filter')
        sliders = {p.get(APP + 'key'): p for p in category.findall('SeekBarPreference')}
        expected = {'filter_' + name: name.title() for name in
                    ('brightness', 'saturation', 'gamma', 'contrast', 'hue', 'sharpness')}
        self.assertEqual(set(sliders), set(expected))
        for key, label in expected.items():
            with self.subTest(key=key):
                slider = sliders[key]
                limit = 5 if key == 'filter_sharpness' else 100
                self.assertEqual(slider.get(APP + 'min'), str(-limit))
                self.assertEqual(slider.get(ANDROID + 'max'), str(limit))
                self.assertEqual(slider.get(ANDROID + 'defaultValue'), '0')
                self.assertEqual(slider.get(APP + 'persistent', 'true'), 'true')
                self.assertEqual(slider.get(APP + 'showSeekBarValue'), 'true')
                self.assertEqual(strings[slider.get(APP + 'title').removeprefix('@string/')], label)


if __name__ == '__main__':
    unittest.main()
