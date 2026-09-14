#!/usr/bin/env python3
# Regenerates threemembers_unpadded.xpt, the 3-member XPT fixture that pins the
# member-boundary framing in ObservationIteratorXpt. Built from the XPORT v5 record
# layout, NOT from this product's own writer -- see the test class javadoc.
#
# Run it from anywhere:  python3 generate-threemembers-fixture.py
# It rewrites the .xpt beside itself and must reproduce it byte-for-byte:
#   sha256 22651f66d82d970d...  (verify with sha256sum after regenerating)
#
# Checked in deliberately: a binary test resource nobody can regenerate is the next
# silent-erosion candidate. Same convention as cumba-jrdata's generate-fixtures.R.
import os

_IBM_SRC = r"""
import struct

def ibm2ieee(b):
    assert len(b)==8
    v=struct.unpack('>Q',b)[0]
    if v==0: return 0.0
    sign=-1.0 if v>>63 else 1.0
    exp=(v>>56)&0x7f
    mant=v&0x00ffffffffffffff
    return sign*mant*16.0**(exp-64)/(2.0**56)

def ieee2ibm(x):
    if x==0: return b'\x00'*8
    sign=0x80 if x<0 else 0
    x=abs(x)
    exp=0
    # find exponent such that x / 16**exp in [1/16, 1)
    while x>=1.0:
        x/=16.0; exp+=1
    while x<1/16.0:
        x*=16.0; exp-=1
    mant=int(round(x*(2.0**56)))
    if mant>=1<<56:
        mant>>=4; exp+=1
    return bytes([sign|(exp+64)])+mant.to_bytes(7,'big')

if __name__=='__main__':
    import pyreadstat
    p='wt/sasxpt/lib/cumba-sas-utils/src/test/resources/net/cumba/sasutils/xpt/twotables.xpt'
    df,meta=pyreadstat.read_xport(p)
    print('pyreadstat rows', df.shape, meta.column_names)
    print(df.head())
    d=open(p,'rb').read()
    for i in range(3):
        off=1040+i*16
        print('mine', ibm2ieee(d[off:off+8]), ibm2ieee(d[off+8:off+16]))
    # roundtrip
    for v in (0.0,1.0,-1.0,123.456,1e10,-0.5,144.0,19.0,2.5):
        assert abs(ibm2ieee(ieee2ibm(v))-v) < 1e-9*max(1,abs(v)), v
    print('roundtrip ok')

"""

"""Build a 3-member SAS v5 XPORT file per the XPORT record layout spec (TS-140).

Layout rules taken from the spec, NOT from this product's writer:
 - every record is 80 bytes, blank-padded if short
 - 'Both of these records occur for every member in the transport file'
 - 'Data records are streamed ... There is no special trailing record'
Therefore a member's data section ends where the next member's
'HEADER RECORD*******MEMBER  HEADER RECORD!!!!!!!' record begins, and that record
starts on an 80-byte boundary.
"""
import sys
sys.path.insert(0, '.')
import types

# The IBM hex-float helper, inlined so this script is self-contained.
ibm = types.ModuleType('ibm')
exec(_IBM_SRC, ibm.__dict__)
ieee2ibm = ibm.ieee2ibm

DT = '20JAN21:10:48:57'


def rec(b):
    assert len(b) <= 80, len(b)
    return b + b' ' * (80 - len(b))


def pad80(b):
    r = len(b) % 80
    return b + (b' ' * (80 - r) if r else b'')


def a(s, n):
    return s.encode('ascii').ljust(n)[:n]


def namestr(ntype, nlng, nvar0, name, npos, label='', nform=''):
    import struct as st
    out = st.pack('>hhhh', ntype, 0, nlng, nvar0)
    out += a(name, 8) + a(label, 40) + a(nform, 8)
    out += st.pack('>hhh', 0, 0, 0) + b'\x00\x00'     # nfl nfd nfj nfill
    out += a('', 8) + st.pack('>hh', 0, 0)            # niform nifl nifd
    out += st.pack('>i', npos)
    out += b'\x00' * 52
    assert len(out) == 140, len(out)
    return out


def member(name, label, vars_, rows):
    """vars_: list of (ntype, nlng, name). rows: list of list of value (float or str)."""
    out = rec(b'HEADER RECORD*******MEMBER  HEADER RECORD!!!!!!!000000000000000001600000000140')
    out += rec(b'HEADER RECORD*******DSCRPTR HEADER RECORD!!!!!!!000000000000000000000000000000')
    out += rec(a('SAS     ', 8) + a(name, 8) + a('SASDATA ', 8) + a('9.4     ', 8)
               + a('Linux   ', 8) + b' ' * 24 + a(DT, 16))
    out += rec(a(DT, 16) + b' ' * 16 + a(label, 40) + a('', 8))
    out += rec(('HEADER RECORD*******NAMESTR HEADER RECORD!!!!!!!%010d%s'
                % (len(vars_), '0' * 20)).encode('ascii'))
    ns = b''
    pos = 0
    for i, (ntype, nlng, vname) in enumerate(vars_):
        ns += namestr(ntype, nlng, i + 1, vname, pos)
        pos += nlng
    out += pad80(ns)
    out += rec(b'HEADER RECORD*******OBS     HEADER RECORD!!!!!!!000000000000000000000000000000')
    data = b''
    for row in rows:
        for (ntype, nlng, _), val in zip(vars_, row):
            if ntype == 1:
                data += ieee2ibm(val)[:nlng]
            else:
                data += a(val, nlng)
    assert len(data) == sum(v[1] for v in vars_) * len(rows)
    return out, data


def library():
    out = rec(b'HEADER RECORD*******LIBRARY HEADER RECORD!!!!!!!000000000000000000000000000000')
    out += rec(a('SAS     ', 8) + a('SAS     ', 8) + a('SASLIB  ', 8) + a('9.4     ', 8)
               + a('Linux   ', 8) + b' ' * 24 + a(DT, 16))
    out += rec(a(DT, 16))
    return out


# --- member FIRST: 2 numerics -> 16-byte observation, 5 rows = 80 bytes EXACTLY.
# Zero padding, so the next member's 20-byte tag is SPLIT across two 16-byte reads.
FIRST_ROWS = [[1.0, 10.5], [2.0, -20.25], [3.0, 0.0], [4.0, 1000000.0], [5.0, -0.125]]
m1h, m1d = member('FIRST', 'split tag: 16-byte obs, 80-byte data', 
                  [(1, 8, 'VAL1'), (1, 8, 'VAL2')], FIRST_ROWS)
assert len(m1d) == 80

# --- member SECOND: 5 numerics -> 40-byte observation, 2 rows = 80 bytes EXACTLY.
# Zero padding again, so the tag lands WHOLLY inside the 40-byte read at index 0.
SECOND_ROWS = [[1.0, 2.0, 3.0, 4.0, 5.0], [-1.5, 2.25, -3.75, 4.5, -5.625]]
m2h, m2d = member('SECOND', 'whole tag in buffer: 40-byte obs',
                  [(1, 8, 'A'), (1, 8, 'B'), (1, 8, 'C'), (1, 8, 'D'), (1, 8, 'E')], SECOND_ROWS)
assert len(m2d) == 80

# --- member THIRD (last): 5-byte char observation, 4 rows = 20 bytes, blank-padded to 80.
THIRD_ROWS = [['alpha'], ['beta'], ['gamma'], ['delta']]
m3h, m3d = member('THIRD', 'last member, blank-padded tail', [(2, 5, 'TXT')], THIRD_ROWS)

out = library() + m1h + pad80(m1d) + m2h + pad80(m2d) + m3h + pad80(m3d)
assert len(out) % 80 == 0
p = os.path.join(os.path.dirname(os.path.abspath(__file__)),
                 'threemembers_unpadded.xpt')
open(p, 'wb').write(out)
print('wrote', p, len(out), 'bytes,', len(out) // 80, 'records')
import re
print('tag offsets:', [m.start() for m in re.finditer(b'HEADER RECORD\\*{7}', out)])
