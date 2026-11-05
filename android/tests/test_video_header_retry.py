"""Exercise the real receiver with a host-side simulated decoder."""
import os
from pathlib import Path
import subprocess
import tempfile
import unittest


class VideoHeaderRetryTest(unittest.TestCase):
    def test_delayed_surface_and_decoder_recreation(self):
        root = Path(__file__).resolve().parents[2]
        with tempfile.TemporaryDirectory(dir=os.environ.get("TMPDIR")) as tmp:
            executable = Path(tmp) / "video-header-test"
            subprocess.run([
                "cc", "-std=gnu11", "-Wall", "-Wextra", "-Ilib/include",
                "android/tests/video_header_retry_test.c", "lib/src/videoreceiver.c",
                "-o", str(executable),
            ], cwd=root, check=True)
            subprocess.run([str(executable)], cwd=root, check=True)


if __name__ == "__main__":
    unittest.main()
