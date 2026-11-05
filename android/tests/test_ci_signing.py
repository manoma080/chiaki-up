import importlib.util
import unittest
from pathlib import Path

spec = importlib.util.spec_from_file_location('verify_ci_signing', Path(__file__).parents[2] / '.github/scripts/verify_android_signing.py')
verify = importlib.util.module_from_spec(spec)
spec.loader.exec_module(verify)


class SigningVerificationTest(unittest.TestCase):
    def test_accepts_only_expected_single_signer(self):
        digest = verify.EXPECTED
        verify.check_output('Verified using v2 scheme: true\nNumber of signers: 1\nSigner #1 certificate SHA-256 digest: ' + digest)

    def test_rejects_wrong_and_multiple_signers(self):
        for output in ('Number of signers: 1\nSigner #1 certificate SHA-256 digest: ' + '0' * 64,
                       'Number of signers: 2\nSigner #1 certificate SHA-256 digest: ' + verify.EXPECTED):
            with self.subTest(output=output), self.assertRaises(ValueError):
                verify.check_output(output)

    def test_rejects_missing_digest(self):
        with self.assertRaises(ValueError):
            verify.check_output('Number of signers: 1')


if __name__ == '__main__':
    unittest.main()
