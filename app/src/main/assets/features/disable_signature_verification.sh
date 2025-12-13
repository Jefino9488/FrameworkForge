#@name Disable Signature Verification
#@description Patches framework.jar, services.jar and miui-services.jar to bypass APK signature verification
#@requires framework.jar,services.jar

# Use environment variables from DiExecutor
FRAMEWORK="$FRAMEWORK_JAR"
SERVICES="$SERVICES_JAR"
MIUI_SERVICES="$MIUI_SERVICES_JAR"

# At least one must be present
if [ -z "$FRAMEWORK" ] && [ -z "$SERVICES" ]; then
    echo "[!] ERROR: Neither framework.jar nor services.jar found"
    return 1
fi

FW_WORK_DIR="$TMP/fw_dc"
SVC_WORK_DIR="$TMP/svc_dc"
MIUI_WORK_DIR="$TMP/miui_dc"

# Smali patch: force method to return 0 (false) for boolean methods
return_false='
    .locals 1
    const/4 v0, 0x0
    return v0
'

# Smali patch: force method to return 1 (true) for boolean methods
return_true='
    .locals 1
    const/4 v0, 0x1
    return v0
'

# Smali patch: force method to return void
return_void='
    .locals 1
    return-void
'

# ============================================
# FRAMEWORK.JAR PATCHES
# ============================================
if [ -n "$FRAMEWORK" ]; then
    echo "[*] Decompiling framework.jar..."
    dynamic_apktool -decompile "$FRAMEWORK" -o "$FW_WORK_DIR"

    if [ ! -d "$FW_WORK_DIR" ]; then
        echo "[!] ERROR: framework.jar decompilation failed"
    else
        echo "[*] Applying signature verification patches to framework.jar..."

        # SigningDetails patches
        echo "[*] Patching SigningDetails.checkCapability()..."
        smali_kit -c -m "checkCapability" -re "$return_true" -d "$FW_WORK_DIR" -name "SigningDetails.smali"

        echo "[*] Patching SigningDetails.checkCapabilityRecover()..."
        smali_kit -c -m "checkCapabilityRecover" -re "$return_true" -d "$FW_WORK_DIR" -name "SigningDetails.smali"

        echo "[*] Patching SigningDetails.hasAncestorOrSelf()..."
        smali_kit -c -m "hasAncestorOrSelf" -re "$return_true" -d "$FW_WORK_DIR" -name "SigningDetails.smali"

        # PackageParser$SigningDetails patches (older Android)
        echo "[*] Patching PackageParser\$SigningDetails.checkCapability()..."
        smali_kit -c -m "checkCapability" -re "$return_true" -d "$FW_WORK_DIR" -name "PackageParser\$SigningDetails.smali"

        # ApkSignatureVerifier patches
        echo "[*] Patching ApkSignatureVerifier.getMinimumSignatureSchemeVersionForTargetSdk()..."
        smali_kit -c -m "getMinimumSignatureSchemeVersionForTargetSdk" -re "$return_false" -d "$FW_WORK_DIR" -name "ApkSignatureVerifier.smali"

        # StrictJarVerifier patches
        echo "[*] Patching StrictJarVerifier.verifyMessageDigest()..."
        smali_kit -c -m "verifyMessageDigest" -re "$return_true" -d "$FW_WORK_DIR" -name "StrictJarVerifier.smali"

        echo "[*] Recompiling framework.jar..."
        dynamic_apktool -recompile "$FW_WORK_DIR" -o "$FRAMEWORK"

        if [ $? -ne 0 ]; then
            echo "[!] ERROR: framework.jar recompilation failed"
        fi

        delete_recursive "$FW_WORK_DIR"
    fi
fi

# ============================================
# SERVICES.JAR PATCHES
# ============================================
if [ -n "$SERVICES" ]; then
    echo "[*] Decompiling services.jar..."
    dynamic_apktool -decompile "$SERVICES" -o "$SVC_WORK_DIR"

    if [ ! -d "$SVC_WORK_DIR" ]; then
        echo "[!] ERROR: services.jar decompilation failed"
    else
        echo "[*] Applying signature verification patches to services.jar..."

        # PackageManagerServiceUtils patches
        echo "[*] Patching verifySignatures()..."
        smali_kit -c -m "verifySignatures" -re "$return_false" -d "$SVC_WORK_DIR" -name "PackageManagerServiceUtils.smali"

        echo "[*] Patching matchSignaturesCompat()..."
        smali_kit -c -m "matchSignaturesCompat" -re "$return_true" -d "$SVC_WORK_DIR" -name "PackageManagerServiceUtils.smali"

        # checkDowngrade patches - make it return void (do nothing)
        echo "[*] Patching checkDowngrade()..."
        smali_kit -c -m "checkDowngrade" -re "$return_void" -d "$SVC_WORK_DIR" -name "PackageManagerServiceUtils.smali"

        # KeySetManagerService patches
        echo "[*] Patching shouldCheckUpgradeKeySetLocked()..."
        smali_kit -c -m "shouldCheckUpgradeKeySetLocked" -re "$return_false" -d "$SVC_WORK_DIR" -name "KeySetManagerService.smali"

        echo "[*] Recompiling services.jar..."
        dynamic_apktool -recompile "$SVC_WORK_DIR" -o "$SERVICES"

        if [ $? -ne 0 ]; then
            echo "[!] ERROR: services.jar recompilation failed"
        fi

        delete_recursive "$SVC_WORK_DIR"
    fi
fi

# ============================================
# MIUI-SERVICES.JAR PATCHES (Optional - MIUI/HyperOS only)
# ============================================
if [ -n "$MIUI_SERVICES" ]; then
    echo "[*] Decompiling miui-services.jar..."
    dynamic_apktool -decompile "$MIUI_SERVICES" -o "$MIUI_WORK_DIR"

    if [ ! -d "$MIUI_WORK_DIR" ]; then
        echo "[!] WARNING: miui-services.jar decompilation failed"
    else
        echo "[*] Applying signature verification patches to miui-services.jar..."

        # MIUI-specific patches: force specific methods to return-void
        echo "[*] Patching verifyIsolationViolation()..."
        smali_kit -c -m "verifyIsolationViolation" -re "$return_void" -d "$MIUI_WORK_DIR"

        echo "[*] Patching canBeUpdate()..."
        smali_kit -c -m "canBeUpdate" -re "$return_void" -d "$MIUI_WORK_DIR"

        echo "[*] Recompiling miui-services.jar..."
        dynamic_apktool -recompile "$MIUI_WORK_DIR" -o "$MIUI_SERVICES"

        if [ $? -ne 0 ]; then
            echo "[!] WARNING: miui-services.jar recompilation failed"
        fi

        delete_recursive "$MIUI_WORK_DIR"
        echo "[*] miui-services.jar patched successfully."
    fi
else
    echo "[*] miui-services.jar not found (not MIUI/HyperOS device), skipping..."
fi

echo "[*] Signature verification patches applied."
