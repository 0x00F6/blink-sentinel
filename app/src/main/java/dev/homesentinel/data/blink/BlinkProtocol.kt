package dev.homesentinel.data.blink

import dev.homesentinel.domain.model.BlinkException
import dev.homesentinel.domain.model.BlinkStatus
import dev.homesentinel.domain.model.BlinkSystem
import org.json.JSONObject
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64

object BlinkProtocol {
    fun pkce(): Pair<String, String> {
        val encode = Base64.getUrlEncoder().withoutPadding()
        val verifier = encode.encodeToString(ByteArray(32).also { SecureRandom().nextBytes(it) })
        return verifier to
            encode.encodeToString(
                MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray(Charsets.US_ASCII)),
            )
    }

    fun json(body: String): JSONObject =
        try {
            JSONObject(body)
        } catch (_: Exception) {
            throw BlinkException(
                BlinkException.Kind.PROTOCOL,
                "Unable to parse the Blink response: the API may have changed",
            )
        }

    fun csrf(html: String): String {
        val script =
            Regex(
                "<script\\b(?=[^>]*\\bid=[\"']oauth-args[\"'])[^>]*>(.*?)</script>",
                RegexOption.DOT_MATCHES_ALL,
            ).find(html)
                ?.groupValues
                ?.get(1)
                ?: throw BlinkException(
                    BlinkException.Kind.PROTOCOL,
                    "The Blink sign-in page has changed",
                )
        return json(script).optString("csrf-token").takeIf { it.isNotBlank() }
            ?: throw BlinkException(
                BlinkException.Kind.PROTOCOL,
                "Blink sign-in: missing CSRF token",
            )
    }

    fun twoFactor(
        code: Int,
        body: String,
    ): Boolean {
        if (code == 412) return true
        if (code != 202) return false
        val j =
            try {
                JSONObject(body)
            } catch (_: Exception) {
                return false
            }
        return j.has("tsv_state") || j.has("tsv_methods") || j.has("next_time_in_secs")
    }

    fun systems(j: JSONObject): List<BlinkSystem> {
        val networks =
            j.optJSONArray("networks")
                ?: throw BlinkException(
                    BlinkException.Kind.PROTOCOL,
                    "Missing Blink systems list",
                )
        return (0 until networks.length()).map { i ->
            val n = networks.getJSONObject(i)
            val armed = n.opt("armed")
            BlinkSystem(
                id(n.opt("id")),
                n.optString("name", "Blink system"),
                when (armed) {
                    true -> BlinkStatus.ARMED
                    false -> BlinkStatus.DISARMED
                    else -> BlinkStatus.UNKNOWN
                },
            )
        }
    }

    fun id(value: Any?): String =
        value.toString().also {
            if (!it.matches(Regex("[0-9]+"))) {
                throw BlinkException(BlinkException.Kind.PROTOCOL, "Invalid Blink identifier")
            }
        }
}
