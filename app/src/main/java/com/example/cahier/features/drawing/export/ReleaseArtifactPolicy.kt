package com.example.cahier.features.drawing.export

import java.security.MessageDigest

enum class ArtifactInstallability {
    INSTALLABLE,
    NOT_INSTALLABLE
}

data class ArtifactPolicyResult(
    val installability: ArtifactInstallability,
    val reason: String
)

data class ArtifactChecksumMetadata(
    val fileName: String,
    val sha256: String,
    val sizeBytes: Int
)

object ReleaseArtifactPolicy {
    fun classify(fileName: String, isSignedApk: Boolean): ArtifactPolicyResult {
        val lower = fileName.lowercase()
        return when {
            lower.endsWith(".aab") -> ArtifactPolicyResult(
                installability = ArtifactInstallability.NOT_INSTALLABLE,
                reason = "AAB is not directly installable on device."
            )
            lower.contains("debug") && lower.endsWith(".apk") -> ArtifactPolicyResult(
                installability = ArtifactInstallability.INSTALLABLE,
                reason = "Debug APK is installable."
            )
            lower.endsWith(".apk") && isSignedApk -> ArtifactPolicyResult(
                installability = ArtifactInstallability.INSTALLABLE,
                reason = "Signed release APK is installable."
            )
            lower.endsWith(".apk") -> ArtifactPolicyResult(
                installability = ArtifactInstallability.NOT_INSTALLABLE,
                reason = "Unsigned APK is not installable."
            )
            else -> ArtifactPolicyResult(
                installability = ArtifactInstallability.NOT_INSTALLABLE,
                reason = "Unknown artifact type."
            )
        }
    }

    fun checksumMetadata(fileName: String, bytes: ByteArray): ArtifactChecksumMetadata {
        val digest = MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(bytes).joinToString("") { b -> "%02x".format(b) }
        return ArtifactChecksumMetadata(
            fileName = fileName,
            sha256 = hash,
            sizeBytes = bytes.size
        )
    }
}
