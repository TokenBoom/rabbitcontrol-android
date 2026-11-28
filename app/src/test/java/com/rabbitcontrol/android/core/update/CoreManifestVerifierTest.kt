package com.rabbitcontrol.android.core.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.security.KeyPairGenerator
import java.security.Signature
import java.security.spec.ECGenParameterSpec
import java.util.Base64

class CoreManifestVerifierTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun verifyManifestSignature_validatesEcdsaP256AndFailsClosed() {
        val kpg = KeyPairGenerator.getInstance("EC")
        kpg.initialize(ECGenParameterSpec("secp256r1"))
        val kp1 = kpg.generateKeyPair()
        val kp2 = kpg.generateKeyPair()

        val pub1B64 = Base64.getEncoder().encodeToString(kp1.public.encoded)
        val pub2B64 = Base64.getEncoder().encodeToString(kp2.public.encoded)
        val pemWrappedPub1 = "-----BEGIN PUBLIC KEY-----\n$pub1B64\n-----END PUBLIC KEY-----\n"

        val payload = """{"schemaVersion":1,"coreVersion":"0.1.19"}""".toByteArray(Charsets.UTF_8)
        val signer = Signature.getInstance("SHA256withECDSA")
        signer.initSign(kp1.private)
        signer.update(payload)
        val sigB64 = Base64.getEncoder().encodeToString(signer.sign())

        assertTrue(CoreManifestVerifier.isPublicKeyConfigured(pub1B64))
        assertTrue(CoreManifestVerifier.verifyManifestSignature(payload, sigB64, pub1B64))
        assertTrue(CoreManifestVerifier.verifyManifestSignature(payload, sigB64, pemWrappedPub1))

        // Tampered payload must fail
        val tampered = """{"schemaVersion":1,"coreVersion":"0.1.20"}""".toByteArray(Charsets.UTF_8)
        assertFalse(CoreManifestVerifier.verifyManifestSignature(tampered, sigB64, pub1B64))

        // Wrong key must fail
        assertFalse(CoreManifestVerifier.verifyManifestSignature(payload, sigB64, pub2B64))

        // Missing/empty public key or signature must fail closed
        assertFalse(CoreManifestVerifier.isPublicKeyConfigured(""))
        assertFalse(CoreManifestVerifier.verifyManifestSignature(payload, sigB64, ""))
        assertFalse(CoreManifestVerifier.verifyManifestSignature(payload, "", pub1B64))
    }

    @Test
    fun verifyFileIntegrity_checksSizeAndSha256() {
        val f = tempFolder.newFile("libtest.so")
        val content = "rabbitcontrol-native-binary".toByteArray(Charsets.UTF_8)
        f.writeBytes(content)
        val sha = CoreManifestVerifier.sha256Hex(content)

        assertTrue(CoreManifestVerifier.verifyFileIntegrity(f, sha, content.size.toLong()))
        assertFalse(CoreManifestVerifier.verifyFileIntegrity(f, sha, (content.size + 1).toLong()))
        assertFalse(CoreManifestVerifier.verifyFileIntegrity(f, "0".repeat(64), content.size.toLong()))
    }

    @Test
    fun parseManifest_acceptsValidAndRejectsUnsafePaths() {
        val validJson = """
            {
              "schemaVersion": 1,
              "coreVersion": "0.1.19",
              "tagName": "core-v0.1.19",
              "coreGitSha": "abc1234",
              "abiVersion": 1,
              "minAppVersion": "0.2.0",
              "publishedAt": "2026-11-26T12:00:00Z",
              "releaseNotes": "- Fix QUIC pacing",
              "abis": {
                "arm64-v8a": {
                  "fileName": "librabbitcontrol_android-arm64-v8a.so",
                  "sha256": "${"a".repeat(64)}",
                  "sizeBytes": 4512340
                }
              }
            }
        """.trimIndent()
        val parsed = CoreManifest.parse(validJson)
        assertEquals("0.1.19", parsed.coreVersion)
        assertEquals(1, parsed.abiVersion)
        assertEquals("librabbitcontrol_android-arm64-v8a.so", parsed.abis["arm64-v8a"]?.fileName)

        val traversalJson = validJson.replace(
            "librabbitcontrol_android-arm64-v8a.so",
            "../evil.so",
        )
        val threw = runCatching { CoreManifest.parse(traversalJson) }.isFailure
        assertTrue(threw)
    }
}
