package com.example.cahier.features.drawing.export

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReleaseArtifactPolicyTest {
    @Test
    fun classify_debugApk_installable() {
        val result = ReleaseArtifactPolicy.classify("app-debug.apk", isSignedApk = false)
        assertEquals(ArtifactInstallability.INSTALLABLE, result.installability)
    }

    @Test
    fun classify_signedReleaseApk_installable() {
        val result = ReleaseArtifactPolicy.classify("app-release.apk", isSignedApk = true)
        assertEquals(ArtifactInstallability.INSTALLABLE, result.installability)
    }

    @Test
    fun classify_unsignedApk_notInstallable() {
        val result = ReleaseArtifactPolicy.classify("app-release-unsigned.apk", isSignedApk = false)
        assertEquals(ArtifactInstallability.NOT_INSTALLABLE, result.installability)
    }

    @Test
    fun classify_aab_notInstallable() {
        val result = ReleaseArtifactPolicy.classify("app-release.aab", isSignedApk = true)
        assertEquals(ArtifactInstallability.NOT_INSTALLABLE, result.installability)
    }

    @Test
    fun checksumMetadata_outputsSha256AndSize() {
        val metadata = ReleaseArtifactPolicy.checksumMetadata("artifact.apk", "abc".toByteArray())
        assertEquals("artifact.apk", metadata.fileName)
        assertEquals(3, metadata.sizeBytes)
        assertTrue(metadata.sha256.isNotBlank())
        assertEquals(64, metadata.sha256.length)
    }
}
