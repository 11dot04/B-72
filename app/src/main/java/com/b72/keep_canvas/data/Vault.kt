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

/**
 * The backup passphrase, remembered on this phone so a backup is one tap after the first. It is sealed with a key that lives in the hardware
 * keystore and cannot leave the phone; a new phone has to be told the passphrase once to restore.
 */
object RememberedPass {
    private const val ALIAS = "b72_backup_pass"

    private fun key(): javax.crypto.SecretKey {
        val ks = java.security.KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getKey(ALIAS, null) as? javax.crypto.SecretKey)?.let { return it }
        val g = javax.crypto.KeyGenerator.getInstance(android.security.keystore.KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        g.init(
            android.security.keystore.KeyGenParameterSpec.Builder(ALIAS, android.security.keystore.KeyProperties.PURPOSE_ENCRYPT or android.security.keystore.KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(android.security.keystore.KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(android.security.keystore.KeyProperties.ENCRYPTION_PADDING_NONE).build(),
        )
        return g.generateKey()
    }

    private fun prefs(ctx: android.content.Context) = ctx.getSharedPreferences("b72_backup", android.content.Context.MODE_PRIVATE)

    fun save(ctx: android.content.Context, pass: String) {
        runCatching {
            val c = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, key()) }
            val ct = c.doFinal(pass.toByteArray())
            prefs(ctx).edit().putString("pass", android.util.Base64.encodeToString(c.iv + ct, android.util.Base64.NO_WRAP)).apply()
        }
    }

    fun load(ctx: android.content.Context): String? = runCatching {
        val raw = android.util.Base64.decode(prefs(ctx).getString("pass", null) ?: return null, android.util.Base64.NO_WRAP)
        val c = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, raw.copyOfRange(0, 12))) }
        String(c.doFinal(raw, 12, raw.size - 12))
    }.getOrNull()

    fun forget(ctx: android.content.Context) { prefs(ctx).edit().remove("pass").apply() }
}
