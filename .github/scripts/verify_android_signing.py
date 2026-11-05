#!/usr/bin/env python3
"""Verify a debug APK has exactly the original signing certificate."""
import re
import subprocess
import sys

EXPECTED = '95ba5ab9d02ce299428dd12094001b85da8570c96e2834987d50252783742ff8'


def check_output(output):
    counts = re.findall(r'^Number of signers: (\d+)\s*$', output, re.MULTILINE)
    digests = re.findall(r'^Signer #\d+ certificate SHA-256 digest: ([0-9a-fA-F]+)\s*$', output, re.MULTILINE)
    if counts != ['1'] or [s.lower() for s in digests] != [EXPECTED]:
        raise ValueError('APK must have exactly one signer with the expected SHA-256 certificate')


if __name__ == '__main__':
    result = subprocess.run([sys.argv[1], 'verify', '--verbose', '--print-certs', sys.argv[2]], check=True, text=True, stdout=subprocess.PIPE)
    check_output(result.stdout)
    print('APK signature verified; signer SHA-256:', EXPECTED)
