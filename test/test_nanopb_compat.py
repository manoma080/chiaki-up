import os
import pathlib
import subprocess
import sys
import tempfile
import unittest

ROOT = pathlib.Path(__file__).resolve().parents[1]
SOURCE = ROOT / 'third-party/nanopb/generator'
ADAPTER = ROOT / 'cmake/prepare_nanopb_generator.py'


class NanopbCompatTest(unittest.TestCase):
    def test_clean_and_locally_modified_generators_generate_code(self):
        clean = subprocess.check_output(['git', '-C', str(SOURCE.parent), 'show', 'HEAD:generator/nanopb_generator.py'])
        dirty = (SOURCE / 'nanopb_generator.py').read_bytes()
        for contents in (clean, dirty):
            with self.subTest(clean=contents == clean), tempfile.TemporaryDirectory() as temp:
                source = pathlib.Path(temp) / 'source'
                import shutil
                shutil.copytree(SOURCE, source)
                (source / 'nanopb_generator.py').write_bytes(contents)
                out = pathlib.Path(temp) / 'generator'
                subprocess.run([sys.executable, str(ADAPTER), str(source), str(out)], check=True)
                descriptor = pathlib.Path(temp) / 'takion.pb'
                subprocess.run(['protoc', '-o' + str(descriptor), '-I' + str(ROOT / 'lib/protobuf'), str(ROOT / 'lib/protobuf/takion.proto')], check=True)
                subprocess.run([sys.executable, str(out / 'nanopb_generator.py'), str(descriptor)], check=True, cwd=temp)
                self.assertTrue(descriptor.with_suffix('.pb.c').is_file())
                self.assertTrue(descriptor.with_suffix('.pb.h').is_file())

    def test_unknown_generator_is_rejected(self):
        with tempfile.TemporaryDirectory() as temp:
            source = pathlib.Path(temp) / 'source'
            source.mkdir()
            (source / 'nanopb_generator.py').write_text('unexpected')
            result = subprocess.run([sys.executable, str(ADAPTER), str(source), str(pathlib.Path(temp) / 'out')], capture_output=True)
            self.assertNotEqual(result.returncode, 0)
            self.assertIn(b'unsupported', result.stderr.lower())


if __name__ == '__main__':
    unittest.main()
