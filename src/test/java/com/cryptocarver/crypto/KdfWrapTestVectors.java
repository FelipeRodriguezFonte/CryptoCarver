package com.cryptocarver.crypto;

/** Hex values shared from the pre-existing KDF and key-wrap primitive tests. */
public final class KdfWrapTestVectors {
    public static final String HKDF_RFC5869_SALT = "000102030405060708090A0B0C";
    public static final String HKDF_RFC5869_INFO = "F0F1F2F3F4F5F6F7F8F9";
    public static final String HKDF_RFC5869_PRK = "077709362C2E32DF0DDC3F0DC47BBA6390B6C73BB50F9C3122EC844AD7C2B3E5";
    public static final String HKDF_RFC5869_OKM = "3CB25F25FAACD57A90434F64D0362F2A2D2D0A90CF1A5A4C5DB02D56ECC4C5BF34007208D5B887185865";
    public static final String KDF_AES128_KEY = "603DEB1015CA71BE2B73AEF0857D7781";
    public static final String KDF_CONTEXT = "01020304";
    public static final String KEY_DATA_128 = "00112233445566778899AABBCCDDEEFF";
    public static final String KEK_128 = "000102030405060708090A0B0C0D0E0F";
    public static final String RFC3394_WRAPPED_KEY_DATA_128 = "1FA68B0A8112B447AEF34BD8FB5A7B829D3E862371D2CFE5";
    public static final String KWP_SHORT_PLAINTEXT = "466F7250617369";

    private KdfWrapTestVectors() { }
}
