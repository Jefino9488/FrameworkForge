#@name Disable Signature Verification
#@description Patches framework.jar to bypass signature verification
#@requires framework.jar

FRAMEWORK="$1"
WORK_DIR="$TMP/fw_dc"

# Smali patch content
disable='
   .locals 1
   const/4 v0, 0x0
   return v0
'

echo "[*] Decompiling framework.jar..."
dynamic_apktool -decompile "$FRAMEWORK" -o "$WORK_DIR"

echo "[*] Applying signature verification bypass..."
smali_kit -c -m "hasAncestorOrSelf" -re "$disable" -d "$WORK_DIR" -name "SigningDetails*"

echo "[*] Recompiling framework.jar..."
dynamic_apktool -preserve-signature -recompile "$WORK_DIR" -o "$FRAMEWORK"

# Cleanup
delete_recursive "$WORK_DIR"

echo "[*] Signature verification patch applied."
