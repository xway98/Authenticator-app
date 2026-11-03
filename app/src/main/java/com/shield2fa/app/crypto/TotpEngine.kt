package com.shield2fa.app.crypto

import android.net.Uri
import com.shield2fa.app.model.AccountItem
import java.nio.ByteBuffer
import java.util.Locale
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import kotlin.math.floor
import kotlin.math.pow

object TotpEngine {

    private const val BASE32_CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567"

    fun generateTotp(
        secretBase32: String,
        period: Int = 30,
        digits: Int = 6,
        algorithm: String = "SHA1",
        timeSeconds: Long = System.currentTimeMillis() / 1000L
    ): String {
        val cleanSecret = secretBase32.replace(" ", "").replace("-", "").uppercase(Locale.US)
        val keyBytes = decodeBase32(cleanSecret)
        val counter = floor(timeSeconds.toDouble() / period.toDouble()).toLong()

        val macAlgorithm = when (algorithm.uppercase(Locale.US)) {
            "SHA256" -> "HmacSHA256"
            "SHA512" -> "HmacSHA512"
            else -> "HmacSHA1"
        }

        val mac = Mac.getInstance(macAlgorithm)
        mac.init(SecretKeySpec(keyBytes, macAlgorithm))

        val buffer = ByteBuffer.allocate(8)
        buffer.putLong(counter)
        val hash = mac.doFinal(buffer.array())

        val offset = (hash[hash.size - 1].toInt() and 0x0F)
        val binary = ((hash[offset].toInt() and 0x7F) shl 24) or
                ((hash[offset + 1].toInt() and 0xFF) shl 16) or
                ((hash[offset + 2].toInt() and 0xFF) shl 8) or
                (hash[offset + 3].toInt() and 0xFF)

        val otp = binary % (10.0.pow(digits.toDouble()).toInt())
        return otp.toString().padStart(digits, '0')
    }

    fun getRemainingSeconds(period: Int = 30): Int {
        val currentSecond = (System.currentTimeMillis() / 1000L) % period
        return (period - currentSecond).toInt()
    }

    fun getProgress(period: Int = 30): Float {
        return getRemainingSeconds(period).toFloat() / period.toFloat()
    }

    fun decodeBase32(base32: String): ByteArray {
        val clean = base32.trimEnd('=')
        var buffer = 0
        var bitsLeft = 0
        val output = ArrayList<Byte>()

        for (c in clean) {
            val charIndex = BASE32_CHARS.indexOf(c)
            if (charIndex < 0) continue
            buffer = (buffer shl 5) or charIndex
            bitsLeft += 5
            if (bitsLeft >= 8) {
                bitsLeft -= 8
                output.add(((buffer shr bitsLeft) and 0xFF).toByte())
            }
        }
        return output.toByteArray()
    }

    fun parseOtpAuthUri(uriString: String): AccountItem? {
        return try {
            val uri = Uri.parse(uriString)
            if (uri.scheme?.lowercase(Locale.ROOT) != "otpauth") return null
            if (uri.host?.lowercase(Locale.ROOT) != "totp") return null

            val path = uri.path?.removePrefix("/") ?: ""
            var issuer = uri.getQueryParameter("issuer") ?: ""
            var accountName = path

            if (path.contains(":")) {
                val parts = path.split(":", limit = 2)
                if (issuer.isBlank()) issuer = parts[0].trim()
                accountName = parts[1].trim()
            }

            if (issuer.isBlank()) {
                issuer = "Service"
            }

            val secret = uri.getQueryParameter("secret") ?: return null
            val digits = uri.getQueryParameter("digits")?.toIntOrNull() ?: 6
            val period = uri.getQueryParameter("period")?.toIntOrNull() ?: 30
            val algorithm = uri.getQueryParameter("algorithm") ?: "SHA1"

            AccountItem(
                issuer = issuer,
                accountName = accountName,
                secret = secret,
                digits = digits,
                period = period,
                algorithm = algorithm
            )
        } catch (e: Exception) {
            null
        }
    }
}
