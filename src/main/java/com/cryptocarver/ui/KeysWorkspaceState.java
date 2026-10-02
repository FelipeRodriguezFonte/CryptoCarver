package com.cryptocarver.ui;

import com.cryptocarver.model.GeneratedKeySummary;
import com.cryptocarver.model.GeneratedAsymmetricKeySummary;
import java.security.KeyPair;

/** Generation state shared by the Keys workbenches, without controls or providers. */
final class KeysWorkspaceState {
    byte[] lastGeneratedSymmetricKeyBytes;
    String lastGeneratedSymmetricKeyType;
    GeneratedKeySummary currentGeneratedKeySummary;
    GeneratedAsymmetricKeySummary currentRsaSummary;
    GeneratedAsymmetricKeySummary currentEcdsaSummary;
    GeneratedAsymmetricKeySummary currentDsaSummary;
    GeneratedAsymmetricKeySummary currentEddsaSummary;
    KeyPair lastGeneratedKeyPair;
    String lastKeyType;
}
