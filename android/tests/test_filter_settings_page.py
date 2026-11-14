import unittest
import xml.etree.ElementTree as ET
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1] / 'app/src/main'
APP = '{http://schemas.android.com/apk/res-auto}'

class FilterSettingsPageTest(unittest.TestCase):
    def test_stream_links_to_distinct_filter_page(self):
        root = ET.parse(ROOT / 'res/xml/preferences.xml').getroot()
        stream = next(c for c in root.findall('PreferenceCategory') if c.get(APP + 'key') == 'category_stream')
        link = next((p for p in stream.findall('Preference') if p.get(APP + 'key') == 'video_filters'), None)
        self.assertIsNotNone(link)
        self.assertEqual(link.get(APP + 'fragment'), 'com.gameblabla.chiaki.settings.SettingsVideoFiltersFragment')
        self.assertFalse(any(c.get(APP + 'key') == 'category_video_filter' for c in root.findall('PreferenceCategory')))

    def test_activity_preserves_restored_page_and_title(self):
        activity = (ROOT / 'java/com/metallic/chiaki/settings/SettingsActivity.kt').read_text()
        self.assertIn('if(savedInstanceState == null)', activity)
        self.assertIn('findFragmentById(R.id.settingsFragment) as? TitleFragment', activity)
        self.assertNotIn('binding.titleTextView.text = rootFragment.getTitle(resources)', activity)

    def test_overlay_refreshes_shared_presets_when_shown(self):
        overlay = (ROOT / 'java/com/metallic/chiaki/stream/FilterOverlay.kt').read_text()
        self.assertIn('fun show() {\n        refreshPresets()', overlay)
        page = (ROOT / 'java/com/metallic/chiaki/settings/SettingsVideoFiltersFragment.kt').read_text()
        self.assertIn('FilterPresetStore(requireContext())', page)
        self.assertIn('preferenceManager.preferenceDataStore = DataStore(preferences)', page)
