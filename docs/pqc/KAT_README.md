# Post-Quantum Cryptography Known-Answer-Tests (KAT)

This directory documents the origin of the static test vectors (fixtures) used in `PQCKATTest.java` to verify ML-KEM, ML-DSA, and SLH-DSA implementations.

## Fixture 1: ML-KEM-512 (kyber512.rsp)

*   **Source / URL**: https://raw.githubusercontent.com/bcgit/bc-test-data/b330d97c8a6a7bf1d3f709421a8a11b86e29a6a9/pqc/crypto/kyber/kyber512.rsp
*   **Version / Date**: BouncyCastle Test Data Repository, commit `b330d97c8a6a7bf1d3f709421a8a11b86e29a6a9`. 
*   **Compatibility**: Compatible with Bouncy Castle 1.78.1.
*   **License**: Bouncy Castle License (MIT-like).
*   **Algorithm**: ML-KEM-512 (Kyber512 in BC 1.78.1).
*   **Local SHA-256 Hash**: `4b88ac7643ff60209af1175e025f354272e88df827a0ce1c056e403629b88e04`
*   **Update Procedure**: If a new BouncyCastle version is integrated into the `pom.xml`, run `curl -sL <new-url> > src/test/resources/pqc/kat/kyber512.rsp`. Verify the test passes and document the new hash.

## Fixture 2: ML-DSA-44 (PQCsignKAT_Dilithium2.rsp)

*   **Source / URL**: https://raw.githubusercontent.com/bcgit/bc-test-data/b330d97c8a6a7bf1d3f709421a8a11b86e29a6a9/pqc/crypto/dilithium/PQCsignKAT_Dilithium2.rsp
*   **Version / Date**: BouncyCastle Test Data Repository, commit `b330d97c8a6a7bf1d3f709421a8a11b86e29a6a9`.
*   **Compatibility**: Compatible with Bouncy Castle 1.78.1.
*   **License**: Bouncy Castle License (MIT-like).
*   **Algorithm**: ML-DSA-44 (Dilithium2 in BC 1.78.1).
*   **Local SHA-256 Hash**: `4657f244d1204e5847b3cacea4fc6116579571bee8ac89b8cba6771f303ee260`
*   **Update Procedure**: Same as above, fetch from the `bc-test-data` repository to ensure exact alignment with the BouncyCastle JCE provider.

## Fixture 3: SLH-DSA-SHA2-128F (subset_sha2-128f-simple.rsp)

*   **Source / URL**: https://raw.githubusercontent.com/bcgit/bc-test-data/b330d97c8a6a7bf1d3f709421a8a11b86e29a6a9/pqc/crypto/sphincs_plus/subset_sha2-128f-simple.rsp
*   **Version / Date**: BouncyCastle Test Data Repository, commit `b330d97c8a6a7bf1d3f709421a8a11b86e29a6a9`.
*   **Compatibility**: Compatible with Bouncy Castle 1.78.1.
*   **License**: Bouncy Castle License (MIT-like).
*   **Algorithm**: SLH-DSA-SHA2-128F (SPHINCS+-SHA2-128F-SIMPLE in BC 1.78.1).
*   **Local SHA-256 Hash**: `4846dcaa749af6237e4cd8c0e15a59c487ef8cb3b6b051f425bb026638cdb69e`
*   **Update Procedure**: Same as above, fetch from the `bc-test-data` repository to ensure exact alignment with the BouncyCastle JCE provider.
