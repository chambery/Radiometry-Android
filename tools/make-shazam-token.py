#!/usr/bin/env python3
"""Mint an Apple developer token (ES256 JWT) for ShazamKit on Android.

Usage: tools/make-shazam-token.py AuthKey_KEYID.p8 KEY_ID TEAM_ID [days<=180]

Create the key at developer.apple.com > Certificates, IDs & Profiles > Keys, with
"Media Services (MusicKit, ShazamKit, Apple Music Feed)" enabled. Then put the printed
token in local.properties as:  shazam.developerToken=<token>
Requires only the `openssl` command line tool.
"""
import base64, json, subprocess, sys, time


def b64url(data: bytes) -> str:
    return base64.urlsafe_b64encode(data).rstrip(b"=").decode()


def der_to_raw(der: bytes) -> bytes:
    """openssl emits an ASN.1 DER ECDSA signature; JWS ES256 wants raw r||s (32 bytes each)."""
    def read_int(buf, i):
        assert buf[i] == 0x02
        length = buf[i + 1]
        value = buf[i + 2:i + 2 + length]
        return value.lstrip(b"\x00").rjust(32, b"\x00"), i + 2 + length
    assert der[0] == 0x30
    i = 3 if der[1] & 0x80 else 2
    r, i = read_int(der, i)
    s, _ = read_int(der, i)
    return r + s


def main():
    if len(sys.argv) < 4:
        sys.exit(__doc__)
    key_path, key_id, team_id = sys.argv[1:4]
    days = min(int(sys.argv[4]) if len(sys.argv) > 4 else 180, 180)
    now = int(time.time())
    header = b64url(json.dumps({"alg": "ES256", "kid": key_id}).encode())
    payload = b64url(json.dumps({"iss": team_id, "iat": now, "exp": now + days * 86400}).encode())
    signing_input = f"{header}.{payload}".encode()
    der = subprocess.run(["openssl", "dgst", "-sha256", "-sign", key_path],
                         input=signing_input, capture_output=True, check=True).stdout
    print(f"{header}.{payload}.{b64url(der_to_raw(der))}")


if __name__ == "__main__":
    main()
