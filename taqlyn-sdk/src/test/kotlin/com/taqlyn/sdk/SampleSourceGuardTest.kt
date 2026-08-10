package com.taqlyn.sdk

import com.google.common.truth.Truth.assertThat
import java.io.File
import org.junit.Test

/**
 * Ensures sample / feature harness sources never import Play Install Referrer types.
 */
class SampleSourceGuardTest {
    @Test
    fun sampleSources_doNotReferenceInstallReferrerPackage() {
        val sampleRoot = locateSampleRoot()
        assertThat(sampleRoot.exists()).isTrue()

        val kotlinFiles =
            sampleRoot
                .walkTopDown()
                .filter { it.isFile && (it.extension == "kt" || it.extension == "java") }
                .toList()

        assertThat(kotlinFiles).isNotEmpty()

        val offenders =
            kotlinFiles.filter { file ->
                file.readText().contains("com.android.installreferrer")
            }

        assertThat(offenders).isEmpty()
    }

    private fun locateSampleRoot(): File {
        val candidates =
            listOf(
                File("sample/src/main"),
                File("../sample/src/main"),
                File("packages/sdk-android/sample/src/main"),
            )
        return candidates.firstOrNull { it.isDirectory }
            ?: File("../sample/src/main")
    }
}
