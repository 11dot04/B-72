package com.b72.keep_canvas.data

import java.io.EOFException
import java.io.InputStream
import java.io.OutputStream
import java.security.GeneralSecurityException
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * Encrypts a backup with a passphrase before it leaves the phone: AES-256-GCM in 1 MB pieces (so a big photo backup never has to fit
 * in memory), key from the passphrase by PBKDF2-SHA256. Each piece is authenticated and the last one is marked, so a wrong passphrase,
 * a changed byte or a cut-off file is caught, not silently restored. Lose the passphrase and the backup cannot be opened by anyone.
 */
object Vault {
    private val MAGIC = "B72E1".toByteArray()
    private const val CHUNK = 1 shl 20
    private const val ROUNDS = 200_000

    private fun key(pass: String, salt: ByteArray): SecretKeySpec {
        val k = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(PBEKeySpec(pass.toCharArray(), salt, ROUNDS, 256)).encoded
        return SecretKeySpec(k, "AES")
    }

    private fun nonce(prefix: ByteArray, i: Int) = prefix + byteArrayOf((i ushr 24).toByte(), (i ushr 16).toByte(), (i ushr 8).toByte(), i.toByte())

    private fun fill(input: InputStream, n: Int): ByteArray {
        val b = ByteArray(n)
        var got = 0
        while (got < n) { val r = input.read(b, got, n - got); if (r < 0) break; got += r }
        return if (got == n) b else b.copyOf(got)
    }

    private fun readInt(input: InputStream): Int {
        val b = fill(input, 4)
        if (b.isEmpty()) return -1
        if (b.size < 4) throw EOFException()
        return ((b[0].toInt() and 255) shl 24) or ((b[1].toInt() and 255) shl 16) or ((b[2].toInt() and 255) shl 8) or (b[3].toInt() and 255)
    }

    private fun writeInt(out: OutputStream, v: Int) { out.write(byteArrayOf((v ushr 24).toByte(), (v ushr 16).toByte(), (v ushr 8).toByte(), v.toByte())) }

    fun encrypt(pass: String, input: InputStream, out: OutputStream) {
        val rnd = SecureRandom()
        val salt = ByteArray(16).also { rnd.nextBytes(it) }
        val prefix = ByteArray(8).also { rnd.nextBytes(it) }
        val key = key(pass, salt)
        out.write(MAGIC); out.write(salt); out.write(prefix)
        var cur = fill(input, CHUNK)
        var i = 0
        while (true) {
            val next = if (cur.size == CHUNK) fill(input, CHUNK) else ByteArray(0)
            val last = next.isEmpty()
            val c = Cipher.getInstance("AES/GCM/NoPadding")
            c.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(128, nonce(prefix, i)))
            c.updateAAD(byteArrayOf(if (last) 1 else 0))
            val ct = c.doFinal(cur)
            writeInt(out, ct.size); out.write(ct)
            if (last) break
            cur = next; i++
        }
        out.flush()
    }

    /** False when the passphrase is wrong, the file is not one of ours, or it was damaged. */
    fun decrypt(pass: String, input: InputStream, out: OutputStream): Boolean = try {
        if (!fill(input, MAGIC.size).contentEquals(MAGIC)) false
        else {
            val salt = fill(input, 16); val prefix = fill(input, 8)
            val key = key(pass, salt)
            var len = readInt(input)
            var i = 0
            var ok = len >= 0
            while (len >= 0) {
                val ct = fill(input, len)
                if (ct.size < len) { ok = false; break }
                val nextLen = readInt(input)
                val c = Cipher.getInstance("AES/GCM/NoPadding")
                c.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, nonce(prefix, i)))
                c.updateAAD(byteArrayOf(if (nextLen < 0) 1 else 0))
                out.write(c.doFinal(ct))
                len = nextLen; i++
            }
            out.flush()
            ok
        }
    } catch (e: GeneralSecurityException) { false } catch (e: java.io.IOException) { false }
}
