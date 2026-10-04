package com.kirin.bilitv.core.settings

import java.security.MessageDigest
import java.security.SecureRandom

object KidsPin {
  const val Length = 4

  fun newSalt(): String {
    val bytes = ByteArray(SaltBytes)
    SecureRandom().nextBytes(bytes)
    return bytes.joinToString(separator = "") { byte -> "%02x".format(byte) }
  }

  fun hash(pin: String, salt: String): String {
    val digest = MessageDigest.getInstance("SHA-256")
      .digest("$salt:$pin".toByteArray(Charsets.UTF_8))
    return digest.joinToString(separator = "") { byte -> "%02x".format(byte) }
  }

  fun matches(pin: String, salt: String, expectedHash: String): Boolean {
    if (pin.length != Length || salt.isBlank() || expectedHash.isBlank()) {
      return false
    }
    return hash(pin, salt) == expectedHash
  }

  private const val SaltBytes = 16
}
