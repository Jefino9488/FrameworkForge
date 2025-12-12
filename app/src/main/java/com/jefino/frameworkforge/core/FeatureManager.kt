package com.jefino.frameworkforge.core

import com.jefino.frameworkforge.model.Feature

/**
 * Manages feature configuration and builds the feature string for workflow inputs
 */
object FeatureManager {

    /**
     * Builds the comma-separated feature string for workflow inputs
     */
    fun buildFeatureString(features: List<Feature>): String {
        return features
            .filter { it.isEnabled }
            .joinToString(",") { it.id }
    }

    /**
     * Filters features based on device capabilities
     */
    fun getAvailableFeatures(hasMiuiServices: Boolean): List<Feature> {
        return Feature.getAllFeatures().map { feature ->
            if (feature.requiresMiui && !hasMiuiServices) {
                // Disable MIUI-specific features if MIUI services not available
                feature.copy(isEnabled = false)
            } else {
                feature
            }
        }
    }

    /**
     * Updates a feature's enabled state
     */
    fun updateFeature(features: List<Feature>, featureId: String, enabled: Boolean): List<Feature> {
        return features.map { feature ->
            if (feature.id == featureId) {
                feature.copy(isEnabled = enabled)
            } else {
                feature
            }
        }
    }

    /**
     * Gets a summary of enabled features for display
     */
    fun getEnabledFeaturesSummary(features: List<Feature>): String {
        val enabledFeatures = features.filter { it.isEnabled }
        return when {
            enabledFeatures.isEmpty() -> "No features selected"
            enabledFeatures.size == 1 -> enabledFeatures.first().displayName
            else -> "${enabledFeatures.size} features selected"
        }
    }
}
