package io.rabbitcontrol.android.core.update

import java.io.File
import java.io.FileInputStream
import java.security.KeyFactory
import java.security.MessageDigest
import java.security.Signature
import java.security.spec.X509EncodedKeySpec
import java.util.Base64

object CoreManifestVerifier {
    private const val EC_ALGORITHM = "EC"
    private const val SIGNATURE_ALGORITHM = "SHA256withECDSA"

    fun normalizePublicKeyBase64(raw: String): String =
        raw.replace("-----BEGIN PUBLIC KEY-----", "")
            .replace("-----END PUBLIC KEY-----", "")
            .replace(Regex("\\s+"), "")
            .trim()

    fun isPublicKeyConfigured(publicKeyBase64: String): Boolean {
        val normalized = normalizePublicKeyBase64(publicKeyBase64)
        if (normalized.isEmpty()) return false
        return runCatching {
            val keyBytes = Base64.getDecoder().decode(normalized)
            KeyFactory.getInstance(EC_ALGORITHM).generatePublic(X509EncodedKeySpec(keyBytes))
        }.isSuccess
    }

    /**
     * Verifies an ASN.1 DER ECDSA P-256 (SHA256withECDSA) signature over exact manifest bytes.
     * Fails closed (returns false) if the public key or signature is missing or invalid.
     */
    fun verifyManifestSignature(
        manifestBytes: ByteArray,
        signatureBase64: String,
        publicKeyBase64: String,
    ): Boolean {
        if (manifestBytes.isEmpty()) return false
        val normalizedKey = normalizePublicKeyBase64(publicKeyBase64)
        val cleanSig = signatureBase64.replace(Regex("\\s+"), "").trim()
        if (normalizedKey.isEmpty() || cleanSig.isEmpty()) return false

        return runCatching {
            val keyBytes = Base64.getDecoder().decode(normalizedKey)
            val sigBytes = Base64.getDecoder().decode(cleanSig)
            val publicKey = KeyFactory.getInstance(EC_ALGORITHM)
                .generatePublic(X509EncodedKeySpec(keyBytes))
            val verifier = Signature.getInstance(SIGNATURE_ALGORITHM)
            verifier.initVerify(publicKey)
            verifier.update(manifestBytes)
            verifier.verify(sigBytes)
        }.getOrDefault(false)
    }

    fun sha256Hex(bytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
        return digest.toHexLower()
    }

    fun sha256Hex(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(16 * 1024)
        FileInputStream(file).use { input ->
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().toHexLower()
    }

    fun verifyFileIntegrity(
        file: File,
        expectedSha256: String,
        expectedSizeBytes: Long,
    ): Boolean {
        if (!file.isFile) return false
        if (expectedSizeBytes > 0L && file.length() != expectedSizeBytes) return false
        val normalizedExpected = expectedSha256.trim().lowercase()
        if (normalizedExpected.length != 64) return false
        return runCatching {
            sha256Hex(file) == normalizedExpected
        }.getOrDefault(false)
    }

    private fun ByteArray.toHexLower(): String {
        val hexChars = "0123456789abcdef"
        val out = StringBuilder(size * 2)
        for (b in this) {
            val v = b.toInt() and 0xFF
            out.append(hexChars[v ushr 4])
            out.append(hexChars[v and 0x0F])
        }
        return out.toString()
    }
}
