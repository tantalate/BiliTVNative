package com.kirin.bilitv.core.settings

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class KidsContentFilterTest {
  @Test
  fun blankPatternShowsEveryName() {
    assertTrue(KidsContentFilter.matches("  ", "任意收藏夹"))
    assertTrue(KidsContentFilter.matchesAny("", listOf("默认分组")))
    assertTrue(KidsContentFilter.isValid(""))
  }

  @Test
  fun patternKeepsOnlyMatchingNames() {
    assertTrue(KidsContentFilter.matches("儿童|动画", "儿童向"))
    assertFalse(KidsContentFilter.matches("儿童|动画", "电影"))
    assertTrue(KidsContentFilter.matchesAny("儿", listOf("特别关注", "儿童")))
    assertFalse(KidsContentFilter.matchesAny("儿", listOf("默认分组")))
  }

  @Test
  fun invalidPatternHidesContent() {
    assertFalse(KidsContentFilter.isValid("["))
    assertFalse(KidsContentFilter.matches("[", "儿童"))
    assertFalse(KidsContentFilter.matchesAny("[", listOf("儿童")))
  }
}
