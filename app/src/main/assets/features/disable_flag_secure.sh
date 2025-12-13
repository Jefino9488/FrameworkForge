#@name Disable FLAG_SECURE
#@description Allows screenshots and screen recording in apps that block it
#@requires services.jar

# Use environment variables from DiExecutor
SERVICES="$SERVICES_JAR"
MIUI_SERVICES="$MIUI_SERVICES_JAR"

# services.jar is required
if [ -z "$SERVICES" ]; then
    echo "[!] ERROR: services.jar not found for FLAG_SECURE patch"
    return 1
fi

SVC_WORK_DIR="$TMP/svc_dc"
MIUI_WORK_DIR="$TMP/miui_dc"

# Smali patch: force method to return 0 (false)
return_false='
    .locals 1
    const/4 v0, 0x0
    return v0
'

# Smali patch: force method to return void (for void methods)
return_void='
    .locals 1
    return-void
'

# ============================================
# SERVICES.JAR PATCHES
# ============================================
echo "[*] Decompiling services.jar..."
dynamic_apktool -decompile "$SERVICES" -o "$SVC_WORK_DIR"

if [ ! -d "$SVC_WORK_DIR" ]; then
    echo "[!] ERROR: services.jar decompilation failed"
    return 1
fi

echo "[*] Applying FLAG_SECURE patches to services.jar..."

# Patch WindowState.isSecureLocked() - main method for Android 16
echo "[*] Patching WindowState.isSecureLocked()..."
smali_kit -c -m "isSecureLocked" -re "$return_false" -d "$SVC_WORK_DIR" -name "WindowState.smali"

# Patch notAllowCaptureDisplay if exists in services.jar
echo "[*] Patching notAllowCaptureDisplay() if present..."
smali_kit -c -m "notAllowCaptureDisplay" -re "$return_false" -d "$SVC_WORK_DIR" -name "WindowManagerService*.smali"

# Patch preventTakingScreenshotToTargetWindow if exists
echo "[*] Patching preventTakingScreenshotToTargetWindow() if present..."
smali_kit -c -m "preventTakingScreenshotToTargetWindow" -re "$return_false" -d "$SVC_WORK_DIR" -name "ScreenshotController*.smali"

echo "[*] Recompiling services.jar..."
dynamic_apktool -recompile "$SVC_WORK_DIR" -o "$SERVICES"

if [ $? -ne 0 ]; then
    echo "[!] ERROR: services.jar recompilation failed"
    delete_recursive "$SVC_WORK_DIR"
    return 1
fi

delete_recursive "$SVC_WORK_DIR"
echo "[*] services.jar patched successfully."

# ============================================
# MIUI-SERVICES.JAR PATCHES (Optional - MIUI/HyperOS only)
# ============================================
if [ -n "$MIUI_SERVICES" ]; then
    echo "[*] Decompiling miui-services.jar..."
    dynamic_apktool -decompile "$MIUI_SERVICES" -o "$MIUI_WORK_DIR"

    if [ ! -d "$MIUI_WORK_DIR" ]; then
        echo "[!] WARNING: miui-services.jar decompilation failed"
    else
        echo "[*] Applying FLAG_SECURE patches to miui-services.jar..."

        # Patch WindowManagerServiceImpl.notAllowCaptureDisplay()
        echo "[*] Patching WindowManagerServiceImpl.notAllowCaptureDisplay()..."
        smali_kit -c -m "notAllowCaptureDisplay" -re "$return_false" -d "$MIUI_WORK_DIR" -name "WindowManagerServiceImpl.smali"

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

echo "[*] FLAG_SECURE patch applied successfully."
