---
name: android-release-signing
description: Generate, configure, manage, and verify Android release keystores, Google Play App Signing, and Gradle signingConfigs. Use when setting up or troubleshooting release keys, creating keystores, configuring local.properties, extracting SHA-1/SHA-256 fingerprints, or preparing release builds (APK/AAB).
---

# Android Release Signing Standards

## 1. Core Rules
- **One Keystore Per App**: Never share keystores across multiple apps. Guarantees blast-radius security, smooth app transfers (M&A), and isolated Play Console upload key resets.
- **Separate Debug & Release**: Debug uses `~/.android/debug.keystore`. Release uses dedicated production `.jks`.
- **Play App Signing Model**: Google holds the master app signing key. Local `.jks` is strictly an **Upload Key**. If lost, request an upload key reset via Play Console without impacting users.
- **Git Security**: Never commit `.jks`, `.keystore`, or signing secrets. Ensure `.gitignore` covers `*.jks`, `*.keystore`, and `local.properties`.

## 2. Quick Keystore Generation
Run the bundled script or use `keytool` directly:

```bash
# Option A: Helper script (Generates key, prints fingerprints & local.properties config)
./.agents/skills/android-release-signing/scripts/generate-keystore.sh <APP_ID_OR_NAME> [PASSWORD]

# Option B: Direct keytool (RSA 2048, 10000 days validity)
keytool -genkeypair -v -keystore "<app_id>.jks" -alias "<app_id>" \
    -keyalg RSA -keysize 2048 -validity 10000 \
    -storepass "<password>" -keypass "<password>" \
    -dname "CN=CODE12 Studio, OU=Mobile App Development, O=CODE12, L=Hanoi, ST=Hanoi, C=VN"
```

## 3. Configuration & Verification

### `local.properties` (Local Dev)
```properties
RELEASE_STORE_FILE=<app_id>.jks
RELEASE_STORE_PASSWORD=<password>
RELEASE_KEY_ALIAS=<app_id>
RELEASE_KEY_PASSWORD=<password>
```

### Essential Commands
| Action | Command |
| :--- | :--- |
| **Validate Signing Config** | `./gradlew validateSigningRelease --console=plain` |
| **Extract SHA-1 & SHA-256** | `keytool -list -v -keystore <app_id>.jks -alias <app_id> -storepass "<pwd>" \| grep -E "(SHA1\|SHA256):"` |
| **Build Signed Bundle (AAB)** | `./gradlew bundleRelease` |
| **CI/CD Base64 Export** | `base64 -i <app_id>.jks \| pbcopy` |

## 4. Troubleshooting Quick Reference

| Issue | Root Cause | Solution |
| :--- | :--- | :--- |
| **`Keystore was tampered with`** | Wrong `RELEASE_STORE_PASSWORD`. | Test password with `keytool -list -keystore <file>`. Avoid shell escaping issues. |
| **`Cannot recover key`** | `RELEASE_KEY_PASSWORD` mismatch or wrong alias. | Check alias via `keytool -list`. For PKCS12, keep storePassword and keyPassword identical. |
| **Lost Upload Key (`.jks`)** | Hardware loss or machine migration. | Play Console -> **Release** -> **App Integrity** -> **Request upload key reset** (submit new `.pem`). |
