#!/usr/bin/env python3
# Regenerates namestr_blank_rest_3x3.xpt, the XPT fixture whose NAMESTR records carry ASCII
# blanks in rest[52] instead of zero bytes. Built from the XPORT v5 record layout (TS-140), NOT
# from this product's own writer.
#
# TS-140 declares the field "char rest[52]; /* remaining fields are irrelevant */", so a reader
# must not require any particular content there; transport files whose writer blank-fills it
# exist in the wild. nfill[2] stays zero here on purpose: the blank-nfill case is covered by
# phuse_send_1_bg.xpt, so each field has a fixture that only it can fail on.
#
# Run it from anywhere:  python3 generate-namestr-blank-rest-fixture.py
# It rewrites the .xpt beside itself and must reproduce it byte-for-byte (md5sum after
# regenerating; the provenance row in README.md carries the digest).
#
# Checked in deliberately: a binary test resource nobody can regenerate is the next
# silent-erosion candidate. Same convention as generate-threemembers-fixture.py.
import os
import struct

DT = '20JAN21:10:48:57'


def ieee2ibm(x):
    """IEEE double -> 8-byte IBM System/360 hex float (TS-140 numeric storage)."""
    if x == 0:
        return b'\x00' * 8
    sign = 0x80 if x < 0 else 0
    x = abs(x)
    exp = 0
    while x >= 1.0:
        x /= 16.0
        exp += 1
    while x < 1 / 16.0:
        x *= 16.0
        exp -= 1
    mant = int(round(x * (2.0 ** 56)))
    if mant >= 1 << 56:
        mant >>= 4
        exp += 1
    return bytes([sign | (exp + 64)]) + mant.to_bytes(7, 'big')


def rec(b):
    assert len(b) <= 80, len(b)
    return b + b' ' * (80 - len(b))


def pad80(b):
    r = len(b) % 80
    return b + (b' ' * (80 - r) if r else b'')


def a(s, n):
    return s.encode('ascii').ljust(n)[:n]


def namestr(ntype, nlng, nvar0, name, label, nform, nfl, nfd, niform, nifl, nifd, npos):
    out = struct.pack('>hhhh', ntype, 0, nlng, nvar0)
    out += a(name, 8) + a(label, 40) + a(nform, 8)
    out += struct.pack('>hhh', nfl, nfd, 0) + b'\x00\x00'      # nfl nfd nfj nfill
    out += a(niform, 8) + struct.pack('>hh', nifl, nifd)       # niform nifl nifd
    out += struct.pack('>i', npos)
    out += b' ' * 52                                           # rest[52]: ASCII blanks
    assert len(out) == 140, len(out)
    return out


# (ntype, nlng, name, label, nform, nfl, nfd, niform, nifl, nifd)
VARS = [
    (2, 8, 'USUBJID', 'Unique Subject Identifier', '$', 8, 0, '$', 8, 0),
    (1, 8, 'AGE', 'Age', 'BEST', 8, 0, 'BEST', 8, 0),
    (1, 8, 'WEIGHT', 'Weight (kg)', '', 8, 1, '', 0, 0),
]
ROWS = [['SUBJ-001', 34.0, 70.5], ['SUBJ-002', 61.0, 82.25], ['SUBJ-003', 47.0, 0.0]]

out = rec(b'HEADER RECORD*******LIBRARY HEADER RECORD!!!!!!!000000000000000000000000000000')
out += rec(a('SAS     ', 8) + a('SAS     ', 8) + a('SASLIB  ', 8) + a('9.4     ', 8)
           + a('Linux   ', 8) + b' ' * 24 + a(DT, 16))
out += rec(a(DT, 16))
out += rec(b'HEADER RECORD*******MEMBER  HEADER RECORD!!!!!!!000000000000000001600000000140')
out += rec(b'HEADER RECORD*******DSCRPTR HEADER RECORD!!!!!!!000000000000000000000000000000')
out += rec(a('SAS     ', 8) + a('BLANKRST', 8) + a('SASDATA ', 8) + a('9.4     ', 8)
           + a('Linux   ', 8) + b' ' * 24 + a(DT, 16))
out += rec(a(DT, 16) + b' ' * 16 + a('rest[52] blank-filled', 40) + a('', 8))
out += rec(('HEADER RECORD*******NAMESTR HEADER RECORD!!!!!!!%010d%s'
            % (len(VARS), '0' * 20)).encode('ascii'))
ns = b''
pos = 0
for i, (ntype, nlng, name, label, nform, nfl, nfd, niform, nifl, nifd) in enumerate(VARS):
    ns += namestr(ntype, nlng, i + 1, name, label, nform, nfl, nfd, niform, nifl, nifd, pos)
    pos += nlng
out += pad80(ns)
out += rec(b'HEADER RECORD*******OBS     HEADER RECORD!!!!!!!000000000000000000000000000000')
data = b''
for row in ROWS:
    for (ntype, nlng, *_), val in zip(VARS, row):
        data += ieee2ibm(val)[:nlng] if ntype == 1 else a(val, nlng)
out += pad80(data)
assert len(out) % 80 == 0

p = os.path.join(os.path.dirname(os.path.abspath(__file__)), 'namestr_blank_rest_3x3.xpt')
with open(p, 'wb') as f:
    f.write(out)
print('wrote', p, len(out), 'bytes,', len(out) // 80, 'records')
