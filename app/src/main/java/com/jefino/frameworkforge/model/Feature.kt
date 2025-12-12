package com.jefino.frameworkforge.model

/**
 * Represents a patchable feature
 */
data class Feature(
    val id: String,
    val displayName: String,
    val description: String,
    val isEnabled: Boolean = false,
    val isDefault: Boolean = false,
    val requiresMiui: Boolean = false
) {
    companion object {
        val DISABLE_SIGNATURE_VERIFICATION = Feature(
            id = "disable_signature_verification",
            displayName = "Disable Signature Verification",
            description = "Allows installation of unsigned or modified applications",
            isDefault = true
        )

        val CN_NOTIFICATION_FIX = Feature(
            id = "cn_notification_fix",
            displayName = "CN Notification Fix",
            description = "Resolves notification delays on MIUI China ROMs",
            requiresMiui = true
        )

        val DISABLE_SECURE_FLAG = Feature(
            id = "disable_secure_flag",
            displayName = "Disable Secure Flag",
            description = "Enables screenshots and screen recording in secure apps"
        )

        val KAORIOS_TOOLBOX = Feature(
            id = "kaorios_toolbox",
            displayName = "Kaorios Toolbox",
            description = "Integrates Play Integrity fixes and device spoofing"
        )

        fun getAllFeatures(): List<Feature> = listOf(
            DISABLE_SIGNATURE_VERIFICATION.copy(isEnabled = true),
            CN_NOTIFICATION_FIX,
            DISABLE_SECURE_FLAG,
            KAORIOS_TOOLBOX
        )
    }
}
