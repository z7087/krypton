package me.steinborn.krypton.mod.shared.network;

import javax.crypto.SecretKey;
import java.security.GeneralSecurityException;

public interface ClientConnectionEncryptionExtension {
    void krypton$setupEncryption(SecretKey key) throws GeneralSecurityException;
}
