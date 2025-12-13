#@name Disable FLAG_SECURE
#@description Allows screenshots and screen recording in apps that block it
#@requires services.jar

SERVICES="$1"
WORK_DIR="$TMP/svc_dc"

# Smali patch contents
disable='
   .locals 1
   const/4 v0, 0x0
   return v0
'

dummy='
   .registers 6
   return-void
'

echo "[*] Decompiling services.jar..."
dynamic_apktool -decompile "$SERVICES" -o "$WORK_DIR"

echo "[*] Disabling Flag Secure..."
smali_kit -c -m "isSecureLocked" -re "$disable" -d "$WORK_DIR" -name "WindowManagerService*" -name "WindowState*"
smali_kit -c -m "notAllowCaptureDisplay" -re "$disable" -d "$WORK_DIR" -name "WindowManagerService*" -name "WindowState*"
smali_kit -c -m "preventTakingScreenshotToTargetWindow" -re "$disable" -d "$WORK_DIR" -name "ScreenshotController*"

echo "[*] Recompiling services.jar..."
dynamic_apktool -preserve-signature -recompile "$WORK_DIR" -o "$SERVICES"

# Cleanup
delete_recursive "$WORK_DIR"

echo "[*] FLAG_SECURE patch applied."
