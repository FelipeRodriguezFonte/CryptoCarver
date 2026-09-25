"""Independent reference for payment crypto, written from the standards, not from the Java code.

Usage: python3 -m venv .venv && .venv/bin/pip install pycryptodome && .venv/bin/python scripts/payment_reference.py
Control values it prints are pinned in PaymentControlValuesTest; see docs/VALORES_CONTROL_PAGOS.md.
"""
from Crypto.Cipher import DES, DES3, AES
from Crypto.Hash import CMAC
import json, sys

H = bytes.fromhex
def hx(b): return b.hex().upper()
def xor(a, b): return bytes(x ^ y for x, y in zip(a, b))
def tdes(k): return DES3.new(k if len(k) == 24 else k + k[:8], DES3.MODE_ECB) if k[:8] != k[8:16] else DES.new(k[:8], DES.MODE_ECB)
def des(k): return DES.new(k, DES.MODE_ECB)
def e3(k, d): return tdes(k).encrypt(d)
def d3(k, d): return tdes(k).decrypt(d)

out = {}
K2 = H("0123456789ABCDEFFEDCBA9876543210")
AES128 = H("000102030405060708090A0B0C0D0E0F")
PAN = "4111111111111111"

# ---------- KCV ----------
out["kcv.tdes2"] = hx(e3(K2, bytes(8)))[:6]
out["kcv.aes128.zero"] = hx(AES.new(AES128, AES.MODE_ECB).encrypt(bytes(16)))[:6]
c = CMAC.new(AES128, ciphermod=AES); c.update(bytes(16)); out["kcv.aes128.cmac"] = hx(c.digest())[:6]

# ---------- PIN blocks (deterministic formats) ----------
def pan12(pan): return "0000" + pan[-13:-1]
def iso0(pin, pan):
    f = ("0%X" % len(pin) + pin).ljust(16, "F"); return hx(xor(H(f), H(pan12(pan))))
out["pin.iso0"] = iso0("1234", PAN)
out["pin.iso2"] = ("2%X" % 4 + "1234").ljust(16, "F")
out["pin.iso0.pan19"] = iso0("1234", "4111111111111111113")

# ISO 9564-1 format 4: E(K, E(K, PINfield) XOR PANfield)
def iso4_panfield(pan):
    return ("%X" % (len(pan) - 12) + pan).ljust(32, "0")
def iso4_encipher(key, pinfield_hex, pan):
    a = AES.new(key, AES.MODE_ECB)
    ia = a.encrypt(H(pinfield_hex))
    return hx(a.encrypt(xor(ia, H(iso4_panfield(pan)))))
PF = "441234AAAAAAAAAA" + "0123456789ABCDEF"   # fixed "random" for reproducibility
out["pin.iso4.pinfield"] = PF
out["pin.iso4.panfield.16"] = iso4_panfield(PAN)
out["pin.iso4.panfield.13"] = iso4_panfield("4111111111111")
out["pin.iso4.panfield.19"] = iso4_panfield("4111111111111111113")
out["pin.iso4.block"] = iso4_encipher(AES128, PF, PAN)

# ---------- CVV (Visa CVV/CVC/iCVV) ----------
def cvv(ka, kb, pan, exp, svc):
    d = (pan + exp + svc).ljust(32, "0")
    b1, b2 = H(d[:16]), H(d[16:32])
    r = des(ka).encrypt(b1); r = xor(r, b2); r = e3(ka + kb, r)
    h = hx(r); digits = [c for c in h if c.isdigit()] + [str(int(c, 16) - 10) for c in h if not c.isdigit()]
    return "".join(digits)[:3]
CVKA, CVKB = H("0123456789ABCDEF"), H("FEDCBA9876543210")
out["cvv.published"] = cvv(CVKA, CVKB, "4123456789012345", "8701", "101")   # expected 561
out["cvv.house"] = cvv(CVKA, CVKB, PAN, "2512", "201")
out["icvv.house"] = cvv(CVKA, CVKB, PAN, "2512", "999")
out["cvv2.house"] = cvv(CVKA, CVKB, PAN, "2512", "000")

# ---------- Visa PVV ----------
def pvv(pin, pan, pvk, pvki):
    tsp = pan[-12:-1] + pvki + pin
    h = hx(e3(pvk, H(tsp)))
    digits = [c for c in h if c.isdigit()] + [str(int(c, 16) - 10) for c in h if not c.isdigit()]
    return "".join(digits)[:4]
out["pvv.house"] = pvv("1234", PAN, K2, "1")

# ---------- IBM 3624 offset ----------
DEC = "0123456789012345"
def ibm_natural(pan, pvk, dectab, n):
    h = hx(e3(pvk, H(pan12(pan))))
    return "".join(dectab[int(c, 16)] for c in h)[:n]
nat = ibm_natural(PAN, K2, DEC, 4)
out["ibm3624.natural"] = nat
out["ibm3624.offset"] = "".join(str((int(p) - int(n)) % 10) for p, n in zip("1234", nat))

# ---------- MACs ----------
def pad1(d): return d + bytes((-len(d)) % 8) if len(d) % 8 else d
def pad2(d): d = d + b"\x80"; return d + bytes((-len(d)) % 8)
def cbcmac_des(k, d):
    h = bytes(8)
    for i in range(0, len(d), 8): h = des(k).encrypt(xor(h, d[i:i+8]))
    return h
def alg3(k, d):
    h = cbcmac_des(k[:8], d); return des(k[:8]).encrypt(des(k[8:16]).decrypt(h))
def alg1_tdes(k, d):
    h = bytes(8)
    for i in range(0, len(d), 8): h = e3(k, xor(h, d[i:i+8]))
    return h
MSG = H("48656C6C6F2C20776F726C6421")  # "Hello, world!"
out["mac.x919.published"] = hx(alg3(K2, pad1(b"Now is the time for all ")))  # expected A1C72E74EA3FA9B6
out["mac.alg3.pad1"] = hx(alg3(K2, pad1(MSG)))
out["mac.alg3.pad2"] = hx(alg3(K2, pad2(MSG)))
out["mac.alg1.tdes.pad1"] = hx(alg1_tdes(K2, pad1(MSG)))
out["mac.alg1.tdes.pad2"] = hx(alg1_tdes(K2, pad2(MSG)))
# ISO/IEC 9797-1:1999, 7.2 (Alg 2, EMAC) and 7.4 (Alg 4, MacDES) with K || K' = K2.
def alg2(k, d):
    return des(k[8:16]).encrypt(cbcmac_des(k[:8], d))
def alg4(k, d):
    kpp = xor(k[8:16], H("F0F0F0F0F0F0F0F0"))  # K'': alternate 4-bit substrings of K' complemented
    h = des(kpp).encrypt(des(k[:8]).encrypt(d[:8]))
    for i in range(8, len(d), 8): h = des(k[:8]).encrypt(xor(h, d[i:i+8]))
    return des(k[8:16]).encrypt(h)
out["mac.alg2.pad1"] = hx(alg2(K2, pad1(MSG)))
out["mac.alg2.pad2"] = hx(alg2(K2, pad2(MSG)))
out["mac.alg4.pad1"] = hx(alg4(K2, pad1(MSG)))
out["mac.alg4.pad2"] = hx(alg4(K2, pad2(MSG)))
c = CMAC.new(K2 + K2[:8], ciphermod=DES3); c.update(MSG); out["mac.cmac.tdes"] = hx(c.digest())
c = CMAC.new(AES128, ciphermod=AES); c.update(MSG); out["mac.cmac.aes"] = hx(c.digest())

# ---------- TDES DUKPT (ANSI X9.24-1:2009) ----------
MASK = H("C0C0C0C000000000C0C0C0C000000000")
def ipek(bdk, ksn):
    base = bytearray(ksn[:8]); base[7] &= 0xE0
    l = e3(bdk, bytes(base)); r = e3(xor(bdk, MASK), bytes(base))
    return l + r
def nrkgp(key, reg):
    kl, kr = key[:8], key[8:]
    rr = des(kl).encrypt(xor(kr, reg)); rr = xor(rr, kr)
    kl2, kr2 = xor(kl, H("C0C0C0C0")+bytes(4)), xor(kr, H("C0C0C0C0")+bytes(4))
    lr = des(kl2).encrypt(xor(kr2, reg)); lr = xor(lr, kr2)
    return lr + rr
def dukpt_key(bdk, ksn):
    ksn = bytearray(ksn); cur = ipek(bdk, bytes(ksn))
    counter = int.from_bytes(ksn[7:10], "big") & 0x1FFFFF
    reg = bytearray(ksn[2:10]); reg[5] &= 0xE0; reg[6] = 0; reg[7] = 0
    bit = 0x100000
    while bit:
        if counter & bit:
            r = int.from_bytes(reg[5:8], "big") | bit
            reg[5:8] = r.to_bytes(3, "big")
            cur = nrkgp(cur, bytes(reg))
        bit >>= 1
    return cur
BDK = K2
KSN0 = H("FFFF9876543210E00000")
out["dukpt.ipek"] = hx(ipek(BDK, KSN0))            # expected 6AC292FAA1315B4D858AB3A3D7D5933A
PINV = H("00000000000000FF00000000000000FF")
MACV = H("000000000000FF00000000000000FF00")
for n in (1, 2, 8):
    ksn = H("FFFF9876543210E" + "%05X" % n)
    fk = dukpt_key(BDK, ksn)
    out[f"dukpt.ksn{n}.future"] = hx(fk)
    out[f"dukpt.ksn{n}.pin"] = hx(xor(fk, PINV))
    out[f"dukpt.ksn{n}.mac"] = hx(xor(fk, MACV))
pek1 = xor(dukpt_key(BDK, H("FFFF9876543210E00001")), PINV)
out["dukpt.ksn1.encpin.x924"] = hx(e3(pek1, H(iso0("1234", "4012345678909"))))  # expected 1B9C1845EB993A7A

# ---------- EMV ----------
def optA(imk, pan, psn):
    y = H((pan + psn)[-16:].rjust(16, "0"))
    return e3(imk, y) + e3(imk, xor(y, b"\xff" * 8))
def odd(b):
    return bytes(x ^ (0 if bin(x).count("1") % 2 else 1) for x in b)
IMK = K2
mk = optA(IMK, PAN, "00")
out["emv.optA.mkac.raw"] = hx(mk)
out["emv.optA.mkac.parity"] = hx(odd(mk))
ATC = H("0001")
sk = e3(mk, ATC + H("F0") + bytes(5)) + e3(mk, ATC + H("0F") + bytes(5))
out["emv.csk.sk.raw"] = hx(sk)
out["emv.csk.sk.parity"] = hx(odd(sk))
# CDOL1 typical data: amount, other, country, TVR, currency, date, type, UN, AIP, ATC, CVR(IAD)
TXN = H("000000001000" "000000000000" "0724" "0000000000" "0978" "250925" "00" "12345678" "1800" "0001" "03A4A000")
out["emv.arqc.data"] = hx(TXN)
out["emv.arqc.pad1"] = hx(alg3(sk, pad1(TXN)))
out["emv.arqc.pad2"] = hx(alg3(sk, pad2(TXN)))
arqc = alg3(sk, pad2(TXN))
out["emv.arpc.m1"] = hx(e3(sk, xor(arqc, H("3030") + bytes(6))))      # ARC "00"
CSU = H("00820000")
out["emv.arpc.m2"] = hx(alg3(sk, pad2(arqc + CSU)))[:8]                 # EMV Book 2 A1.2.2, no prop. auth data

json.dump(out, sys.stdout, indent=1)
