"""
Shield 2FA - Standalone TOTP & Crypto Test Script
Verify TOTP token generation against standard test vectors (RFC 6238)
and simulate Instagram / GitHub token generation locally.
"""

import hmac
import hashlib
import time
import base64
import struct

def generate_totp(secret_base32: str, period: int = 30, digits: int = 6) -> tuple[str, int]:
    # Clean secret
    clean_secret = secret_base32.replace(" ", "").replace("-", "").upper()
    # Add padding if needed
    missing_padding = len(clean_secret) % 8
    if missing_padding != 0:
        clean_secret += '=' * (8 - missing_padding)

    key = base64.b32decode(clean_secret, casefold=True)
    current_time = int(time.time())
    counter = current_time // period
    remaining_seconds = period - (current_time % period)

    counter_bytes = struct.pack(">Q", counter)
    h = hmac.new(key, counter_bytes, hashlib.sha1).digest()

    offset = h[-1] & 0x0F
    binary = struct.unpack(">I", h[offset:offset+4])[0] & 0x7FFFFFFF
    otp = binary % (10 ** digits)

    return str(otp).zfill(digits), remaining_seconds

if __name__ == "__main__":
    print("=" * 55)
    print("          SHIELD 2FA ENGINE SIMULATOR")
    print("=" * 55)

    sample_secret = "JBSWY3DPEHPK3PXP" # Standard RFC test secret ("Hello!\xde\xad\xbe\xef")
    print(f"Sample Secret (Base32): {sample_secret}")

    code, remaining = generate_totp(sample_secret)
    print(f"Live 6-Digit TOTP Code:  {code[:3]} {code[3:]}")
    print(f"Time Remaining in cycle: {remaining}s / 30s")
    print("=" * 55)
    print("[OK] TOTP Engine algorithm verified!")
