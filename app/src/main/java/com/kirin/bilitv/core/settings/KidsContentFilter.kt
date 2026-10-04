package com.kirin.bilitv.core.settings

object KidsContentFilter {
  fun isValid(pattern: String): Boolean {
    val trimmed = pattern.trim()
    if (trimmed.isEmpty()) {
      return true
    }
    return runCatching { Regex(trimmed) }.isSuccess
  }

  fun matches(pattern: String, name: String): Boolean {
    val trimmed = pattern.trim()
    if (trimmed.isEmpty()) {
      return true
    }
    val regex = runCatching { Regex(trimmed) }.getOrNull() ?: return false
    return regex.containsMatchIn(name)
  }

  fun matchesAny(pattern: String, names: List<String>): Boolean {
    val trimmed = pattern.trim()
    if (trimmed.isEmpty()) {
      return true
    }
    if (!isValid(trimmed)) {
      return false
    }
    return names.any { name -> matches(trimmed, name) }
  }
}
