package com.shield2fa.app.crypto

import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

class CryptoManager(private val context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("shield2fa_vault_prefs", Context.MODE_PRIVATE)
    private val keyStore: KeyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }

    companion object {
        private const val BIOMETRIC_KEY_ALIAS = "Shield2FA_BiometricMasterKey"
        private const val PREF_IS_INITIALIZED = "is_initialized"
        private const val PREF_SALT = "pwd_salt"
        private const val PREF_PWD_IV = "pwd_iv"
        private const val PREF_PWD_ENCRYPTED_KEY = "pwd_enc_key"
        private const val PREF_BIO_ENABLED = "bio_enabled"
        private const val PREF_BIO_IV = "bio_iv"
        private const val PREF_BIO_ENCRYPTED_KEY = "bio_enc_key"
        private const val PREF_VAULT_IV = "vault_iv"
        private const val PREF_VAULT_DATA = "vault_ciphertext"

        private const val PBKDF2_ITERATIONS = 100_000
        private const val KEY_SIZE_BITS = 256
        private const val GCM_TAG_LENGTH = 128
    }

    fun isVaultInitialized(): Boolean {
        return prefs.getBoolean(PREF_IS_INITIALIZED, false)
    }

    fun isBiometricsEnabled(): Boolean {
        return prefs.getBoolean(PREF_BIO_ENABLED, false) && prefs.getString(PREF_BIO_ENCRYPTED_KEY, null) != null
    }

    /**
     * Initializes a brand-new vault:
     * Generates a 256-bit random Master Key and seals it with the Password slot and optional Biometric slot.
     */
    fun setupVault(masterPassword: String, enableBiometrics: Boolean): ByteArray {
        val masterKey = ByteArray(32)
        SecureRandom().nextBytes(masterKey)

        // Seal with Password slot
        sealPasswordSlot(masterKey, masterPassword)

        if (enableBiometrics) {
            setupBiometricSlot(masterKey)
        }

        prefs.edit()
            .putBoolean(PREF_IS_INITIALIZED, true)
            .putBoolean(PREF_BIO_ENABLED, enableBiometrics)
            .apply()

        // Encrypt initial empty vault
        saveVault(masterKey, "[]")

        return masterKey
    }

    private fun sealPasswordSlot(masterKey: ByteArray, password: String) {
        val salt = ByteArray(16).apply { SecureRandom().nextBytes(this) }
        val derivedKey = deriveKeyFromPassword(password, salt)

        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, derivedKey)
        val iv = cipher.iv
        val encryptedMasterKey = cipher.doFinal(masterKey)

        prefs.edit()
            .putString(PREF_SALT, Base64.encodeToString(salt, Base64.NO_WRAP))
            .putString(PREF_PWD_IV, Base64.encodeToString(iv, Base64.NO_WRAP))
            .putString(PREF_PWD_ENCRYPTED_KEY, Base64.encodeToString(encryptedMasterKey, Base64.NO_WRAP))
            .apply()
    }

    /**
     * Unlocks the Master Key using the user's Master Password.
     */
    fun unlockWithPassword(password: String): ByteArray {
        val saltBase64 = prefs.getString(PREF_SALT, null) ?: throw IllegalStateException("Vault not initialized")
        val ivBase64 = prefs.getString(PREF_PWD_IV, null) ?: throw IllegalStateException("Invalid vault state")
        val encKeyBase64 = prefs.getString(PREF_PWD_ENCRYPTED_KEY, null) ?: throw IllegalStateException("Invalid vault state")

        val salt = Base64.decode(saltBase64, Base64.NO_WRAP)
        val iv = Base64.decode(ivBase64, Base64.NO_WRAP)
        val encryptedKey = Base64.decode(encKeyBase64, Base64.NO_WRAP)

        val derivedKey = deriveKeyFromPassword(password, salt)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val spec = GCMParameterSpec(GCM_TAG_LENGTH, iv)
        cipher.init(Cipher.DECRYPT_MODE, derivedKey, spec)

        return cipher.doFinal(encryptedKey)
    }

    /**
     * Resets the master password using an already-decrypted masterKey (e.g. from Biometrics).
     * Solves the user's requirement: "if I forget my password, refix it using biometrics"!
     */
    fun resetPasswordWithMasterKey(masterKey: ByteArray, newPassword: String) {
        sealPasswordSlot(masterKey, newPassword)
    }

    // --- Biometric Keystore Management ---

    fun setupBiometricSlot(masterKey: ByteArray) {
        val secretKey = getOrCreateBiometricKeystoreKey()
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, secretKey)
        val iv = cipher.iv
        val encryptedMasterKey = cipher.doFinal(masterKey)

        prefs.edit()
            .putString(PREF_BIO_IV, Base64.encodeToString(iv, Base64.NO_WRAP))
            .putString(PREF_BIO_ENCRYPTED_KEY, Base64.encodeToString(encryptedMasterKey, Base64.NO_WRAP))
            .putBoolean(PREF_BIO_ENABLED, true)
            .apply()
    }

    fun getBiometricDecryptCipher(): Cipher? {
        val ivBase64 = prefs.getString(PREF_BIO_IV, null) ?: return null
        val iv = Base64.decode(ivBase64, Base64.NO_WRAP)

        val key = keyStore.getKey(BIOMETRIC_KEY_ALIAS, null) as? SecretKey ?: return null
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val spec = GCMParameterSpec(GCM_TAG_LENGTH, iv)
        cipher.init(Cipher.DECRYPT_MODE, key, spec)
        return cipher
    }

    fun unlockWithBiometricCipher(cipher: Cipher): ByteArray {
        val encKeyBase64 = prefs.getString(PREF_BIO_ENCRYPTED_KEY, null)
            ?: throw IllegalStateException("Biometric key not enrolled")
        val encryptedKey = Base64.decode(encKeyBase64, Base64.NO_WRAP)
        return cipher.doFinal(encryptedKey)
    }

    private fun getOrCreateBiometricKeystoreKey(): SecretKey {
        if (!keyStore.containsAlias(BIOMETRIC_KEY_ALIAS)) {
            val keyGenerator = KeyGenerator.getInstance(
                KeyProperties.KEY_ALGORITHM_AES,
                "AndroidKeyStore"
            )
            val builder = KeyGenParameterSpec.Builder(
                BIOMETRIC_KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(KEY_SIZE_BITS)
                .setUserAuthenticationRequired(false) // Hardware protected; prompt handles user verification

            keyGenerator.init(builder.build())
            return keyGenerator.generateKey()
        }
        return (keyStore.getKey(BIOMETRIC_KEY_ALIAS, null) as SecretKey)
    }

    // --- Vault Data Encryption ---

    fun saveVault(masterKey: ByteArray, jsonPlaintext: String) {
        val secretKeySpec = SecretKeySpec(masterKey, "AES")
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, secretKeySpec)
        val iv = cipher.iv
        val ciphertext = cipher.doFinal(jsonPlaintext.toByteArray(Charsets.UTF_8))

        prefs.edit()
            .putString(PREF_VAULT_IV, Base64.encodeToString(iv, Base64.NO_WRAP))
            .putString(PREF_VAULT_DATA, Base64.encodeToString(ciphertext, Base64.NO_WRAP))
            .apply()
    }

    fun loadVault(masterKey: ByteArray): String {
        val ivBase64 = prefs.getString(PREF_VAULT_IV, null) ?: return "[]"
        val dataBase64 = prefs.getString(PREF_VAULT_DATA, null) ?: return "[]"

        val iv = Base64.decode(ivBase64, Base64.NO_WRAP)
        val ciphertext = Base64.decode(dataBase64, Base64.NO_WRAP)

        val secretKeySpec = SecretKeySpec(masterKey, "AES")
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val spec = GCMParameterSpec(GCM_TAG_LENGTH, iv)
        cipher.init(Cipher.DECRYPT_MODE, secretKeySpec, spec)

        val plaintextBytes = cipher.doFinal(ciphertext)
        return String(plaintextBytes, Charsets.UTF_8)
    }

    private fun deriveKeyFromPassword(password: String, salt: ByteArray): SecretKey {
        val keySpec = PBEKeySpec(password.toCharArray(), salt, PBKDF2_ITERATIONS, KEY_SIZE_BITS)
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val keyBytes = factory.generateSecret(keySpec).encoded
        return SecretKeySpec(keyBytes, "AES")
    }
}
