package com.airchecklists.app.data.model

import kotlinx.serialization.Serializable

/** One file listed in the remote map release manifest. */
@Serializable
data class MapManifestFile(
    val name: String,
    val kind: String = "",
    val bytes: Long = 0L,
)

/**
 * Remote map release manifest (manifest.json attached to a GitHub Release).
 * Produced by mapbuild/make_manifest.ps1. Parsed with ignoreUnknownKeys.
 */
@Serializable
data class MapManifest(
    val tag: String = "",
    val generatedAt: String = "",
    val files: List<MapManifestFile> = emptyList(),
) {
    fun file(name: String): MapManifestFile? = files.firstOrNull { it.name == name }
}
