package com.shield2fa.app.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.FragmentActivity
import androidx.biometric.BiometricPrompt
import androidx.compose.runtime.*
import androidx.core.content.ContextCompat
import com.shield2fa.app.crypto.CryptoManager
import com.shield2fa.app.crypto.TotpEngine
import com.shield2fa.app.data.VaultRepository
import com.shield2fa.app.model.AccountItem
import com.shield2fa.app.ui.screens.*
import com.shield2fa.app.ui.theme.Shield2FATheme
import java.util.concurrent.Executor

enum class AppScreen {
    SETUP,
    UNLOCK,
    HOME,
    QR_SCANNER
}

class MainActivity : FragmentActivity() {

    private lateinit var cryptoManager: CryptoManager
    private var vaultRepository by mutableStateOf<VaultRepository?>(null)
    private var currentScreen by mutableStateOf(AppScreen.UNLOCK)

    private var showManualAddDialog by mutableStateOf(false)
    private var showResetPasswordDialog by mutableStateOf(false)
    private var pendingMasterKeyForReset by mutableStateOf<ByteArray?>(null)

    private lateinit var executor: Executor
    private lateinit var biometricPrompt: BiometricPrompt
    private var isBiometricForReset = false

    private val requestCameraPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            if (isGranted) {
                currentScreen = AppScreen.QR_SCANNER
            } else {
                Toast.makeText(this, "Camera permission needed to scan QR codes", Toast.LENGTH_LONG).show()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        cryptoManager = CryptoManager(applicationContext)
        executor = ContextCompat.getMainExecutor(this)

        setupBiometricPrompt()

        // Check if vault is initialized
        currentScreen = if (!cryptoManager.isVaultInitialized()) {
            AppScreen.SETUP
        } else {
            AppScreen.UNLOCK
        }

        // Handle opening an otpauth:// URL directly
        intent?.data?.toString()?.let { uriStr ->
            TotpEngine.parseOtpAuthUri(uriStr)?.let { pendingAccount ->
                vaultRepository?.addAccount(pendingAccount)
            }
        }

        setContent {
            Shield2FATheme {
                when (currentScreen) {
                    AppScreen.SETUP -> {
                        SetupScreen(
                            onSetupCompleted = { password, enableBio ->
                                val masterKey = cryptoManager.setupVault(password, enableBio)
                                vaultRepository = VaultRepository(cryptoManager, masterKey)
                                currentScreen = AppScreen.HOME
                            }
                        )
                    }

                    AppScreen.UNLOCK -> {
                        UnlockScreen(
                            isBiometricAvailable = cryptoManager.isBiometricsEnabled(),
                            onUnlockPassword = { password ->
                                val masterKey = cryptoManager.unlockWithPassword(password)
                                vaultRepository = VaultRepository(cryptoManager, masterKey)
                                currentScreen = AppScreen.HOME
                            },
                            onTriggerBiometrics = { forReset ->
                                triggerBiometricAuthentication(forReset)
                            }
                        )
                    }

                    AppScreen.HOME -> {
                        vaultRepository?.let { repo ->
                            HomeScreen(
                                vaultRepository = repo,
                                onLockVault = {
                                    vaultRepository = null
                                    currentScreen = AppScreen.UNLOCK
                                },
                                onOpenQrScanner = {
                                    if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                                        == PackageManager.PERMISSION_GRANTED
                                    ) {
                                        currentScreen = AppScreen.QR_SCANNER
                                    } else {
                                        requestCameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                                    }
                                },
                                onOpenManualAdd = { showManualAddDialog = true },
                                onRequestChangePassword = {
                                    pendingMasterKeyForReset = null
                                    showResetPasswordDialog = true
                                }
                            )
                        }
                    }

                    AppScreen.QR_SCANNER -> {
                        QrScannerScreen(
                            onDismiss = { currentScreen = AppScreen.HOME },
                            onAccountScanned = { account ->
                                vaultRepository?.addAccount(account)
                                currentScreen = AppScreen.HOME
                                Toast.makeText(this, "Added ${account.issuer} (${account.accountName})", Toast.LENGTH_SHORT).show()
                            }
                        )
                    }
                }

                if (showManualAddDialog) {
                    AddAccountDialog(
                        onDismiss = { showManualAddDialog = false },
                        onAccountAdded = { account ->
                            vaultRepository?.addAccount(account)
                            showManualAddDialog = false
                            Toast.makeText(this, "Added ${account.issuer}", Toast.LENGTH_SHORT).show()
                        }
                    )
                }

                if (showResetPasswordDialog) {
                    ResetPasswordDialog(
                        onDismiss = { showResetPasswordDialog = false },
                        onConfirmReset = { newPassword ->
                            val keyToUse = pendingMasterKeyForReset
                            if (keyToUse != null) {
                                cryptoManager.resetPasswordWithMasterKey(keyToUse, newPassword)
                                vaultRepository = VaultRepository(cryptoManager, keyToUse)
                                pendingMasterKeyForReset = null
                                currentScreen = AppScreen.HOME
                            } else {
                                vaultRepository?.resetMasterPassword(newPassword)
                            }
                            showResetPasswordDialog = false
                            Toast.makeText(this, "Master password updated successfully!", Toast.LENGTH_LONG).show()
                        }
                    )
                }
            }
        }
    }

    private fun setupBiometricPrompt() {
        biometricPrompt = BiometricPrompt(this, executor, object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                super.onAuthenticationSucceeded(result)
                try {
                    val cipher = result.cryptoObject?.cipher
                    val masterKey = if (cipher != null) {
                        cryptoManager.unlockWithBiometricCipher(cipher)
                    } else {
                        val fallbackCipher = cryptoManager.getBiometricDecryptCipher()
                            ?: throw IllegalStateException("Could not obtain biometric cipher")
                        cryptoManager.unlockWithBiometricCipher(fallbackCipher)
                    }

                    if (isBiometricForReset) {
                        // User forgot password -> Verified via fingerprint -> Open reset password dialog!
                        pendingMasterKeyForReset = masterKey
                        showResetPasswordDialog = true
                    } else {
                        // Normal biometric unlock
                        vaultRepository = VaultRepository(cryptoManager, masterKey)
                        currentScreen = AppScreen.HOME
                    }
                } catch (e: Exception) {
                    Toast.makeText(this@MainActivity, "Failed to unlock vault: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                super.onAuthenticationError(errorCode, errString)
                if (errorCode != BiometricPrompt.ERROR_USER_CANCELED && errorCode != BiometricPrompt.ERROR_NEGATIVE_BUTTON) {
                    Toast.makeText(this@MainActivity, "Biometric error: $errString", Toast.LENGTH_SHORT).show()
                }
            }
        })
    }

    private fun triggerBiometricAuthentication(forReset: Boolean) {
        isBiometricForReset = forReset
        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle(if (forReset) "Verify Fingerprint to Reset Password" else "Unlock Shield 2FA")
            .setSubtitle(if (forReset) "Authenticate to set a new Master Password" else "Confirm biometric to access 2FA tokens")
            .setNegativeButtonText("Cancel")
            .build()

        val cipher = cryptoManager.getBiometricDecryptCipher()
        if (cipher != null) {
            biometricPrompt.authenticate(promptInfo, BiometricPrompt.CryptoObject(cipher))
        } else {
            biometricPrompt.authenticate(promptInfo)
        }
    }
}
