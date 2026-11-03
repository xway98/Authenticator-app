# Shield 2FA Authenticator (Android)

A lightweight, modern, ad-free, 100% offline 2FA authenticator for Android. Designed to generate RFC 6238 TOTP codes for GitHub, Instagram, Google, Discord, and other services with hardware-backed encryption and **biometric password self-recovery**.

---

## Key Highlights

- **Zero Ads, Zero Trackers, 100% Offline**:
  - The app **does not have internet permissions** (`android.permission.INTERNET` is omitted from `AndroidManifest.xml`).
  - It is physically impossible for the app to fetch ads, track you, or leak your secrets over the network.
- **Biometric Password Recovery ("Refix password if forgotten")**:
  - Employs a **Dual-Slot Key Architecture**: The 256-bit AES master key is encrypted by both your Master Password (PBKDF2-SHA256) and your device's Hardware Keystore Biometrics.
  - If you forget your password, simply tap **"Forgot password? Reset via Fingerprint"**. Once your fingerprint or face is verified, you are prompted to set a new password without losing any 2FA tokens!
- **Fast Offline Camera QR Scanner**:
  - Uses CameraX + ZXing to decode `otpauth://totp/...` QR codes directly on your device.
- **Modern Jetpack Compose UI**:
  - Dark AMOLED theme with high-contrast text.
  - Live 30-second circular countdown timer with color warnings (emerald turning rose in the final 5s).
  - 1-tap copy with haptic feedback vibration.
  - Instant live search.

---

## Architecture Overview

```
                      +-----------------------------+
                      |   Random 256-bit MasterKey  |
                      +--------------+--------------+
                                     |
               +---------------------+---------------------+
               |                                           |
               v                                           v
    [Password Slot]                              [Biometric Slot]
 PBKDF2-HMAC-SHA256 (100k)                 Android Keystore AES-256
      AES-256-GCM                                 AES-256-GCM
 (Unlocks with Password)                   (Hardware Biometric Prompt)
               |                                           |
               +---------------------+---------------------+
                                     |
                                     v
                       +---------------------------+
                       | Encrypted Vault (Accounts)|
                       |  - GitHub, Instagram...   |
                       +---------------------------+
```

### Self-Recovery Flow:
1. User taps **"Forgot password? Reset via Fingerprint"**.
2. Android `BiometricPrompt` prompts for fingerprint/face.
3. Keystore decrypts the `MasterKey`.
4. App opens **"Reset Master Password"** dialog.
5. User enters a new password. The app re-encrypts the `MasterKey` in the Password Slot with the new password.
6. The vault is completely preserved and the password is restored!

---

## How to Build the APK

### Method 1: Automatic Build via GitHub Actions (Recommended - No SDK install required!)
1. Initialize git in this folder or push to your GitHub account:
   ```bash
   git init
   git add .
   git commit -m "Initial commit of Shield 2FA"
   git branch -M main
   git remote add origin https://github.com/YOUR_USERNAME/Shield2FA.git
   git push -u origin main
   ```
2. In your GitHub repository, click on the **Actions** tab.
3. The included `.github/workflows/build-apk.yml` will automatically compile the APK in ~3 minutes.
4. Download the `Shield2FA-app-debug.apk` directly from the workflow summary and install it on your Android phone!

### Method 2: Local Build via Android Studio
1. Open Android Studio.
2. Select **Open** and select the `Shield2FA` folder.
3. Connect your Android device via USB (or start an emulator).
4. Click **Run** (`Shift + F10`) or run `./gradlew assembleDebug`.
5. The output APK will be in `app/build/outputs/apk/debug/app-debug.apk`.
