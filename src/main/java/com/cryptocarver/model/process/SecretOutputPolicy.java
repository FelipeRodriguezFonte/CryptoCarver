package com.cryptocarver.model.process;

/** Classifies process outputs that contain secret material. */
public final class SecretOutputPolicy {
    private SecretOutputPolicy() { }

    public static boolean isSecretMaterialOutput(String type) {
        return "AES_KEY_GENERATE".equals(type) || "KDF_PBKDF2".equals(type) || "RSA_KEYPAIR_GENERATE".equals(type)
                || "RANDOM_BYTES".equals(type) || "KEY_SPLIT_XOR".equals(type) || "KEY_COMBINE_XOR".equals(type)
                || "COMPONENT_SELECT".equals(type)
                || "PARITY_ADJUST".equals(type) || type != null && type.startsWith("KDF_")
                || "AES_UNWRAP_3394".equals(type) || "AES_UNWRAP_5649".equals(type)
                || "TR31_UNWRAP".equals(type) || "TR31_WRAP".equals(type) || "ICSF_TOKEN_PARSE".equals(type)
                || "ATALLA_AKB_WRAP".equals(type) || "ATALLA_AKB_UNWRAP".equals(type)
                || "SAFENET_KM_ENCRYPT".equals(type) || "SAFENET_KM_DECRYPT".equals(type)
                || "FUTUREX_MFK_ENCRYPT".equals(type) || "FUTUREX_MFK_DECRYPT".equals(type)
                || "KEYPAIR_GENERATE".equals(type)
                || "PQC_KEYPAIR_GENERATE".equals(type) || "PQC_KEM_DECAPSULATE".equals(type)
                || "PIN_BLOCK_ENCODE".equals(type) || "PIN_BLOCK_DECODE".equals(type) || "PIN_BLOCK_TRANSLATE".equals(type)
                || "CVV_GENERATE".equals(type) || "DCVV_GENERATE".equals(type) || "PVV_GENERATE".equals(type)
                || "IBM3624_OFFSET".equals(type) || "DUKPT_TDES_DERIVE".equals(type) || "DUKPT_AES_DERIVE".equals(type)
                || "DUKPT_PIN_CRYPT".equals(type) || "EMV_ICC_MASTER_KEY".equals(type) || "EMV_SESSION_KEY".equals(type)
                || "EMV_ARQC_GENERATE".equals(type) || "EMV_ARPC".equals(type) || "EMV_TLV_PARSE".equals(type)
                || "EMV_SM_CARD_KEY".equals(type) || "EMV_SM_SESSION_KEY".equals(type) || "VISA_HCE_LUK".equals(type)
                || "TRACK2_ENCODE".equals(type) || "TRACK2_PARSE".equals(type);
    }

}
