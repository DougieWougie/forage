package me.dougals.forage.data.net

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class WebUrlTest {
    @Test fun onlyHttpAndHttpsWithAHostAreWebUrls() {
        listOf("https://example.com/a?b=1", "http://example.com", "HTTPS://Example.com/x", "http://192.168.1.1:8080/")
            .forEach { assertTrue(isWebUrl(it), it) }
        listOf(
            "", "   ", "file:///etc/passwd", "javascript:alert(1)", "intent://x#Intent;scheme=http;end", "content://a/b",
            "ftp://example.com/f", "market://details?id=x", "tel:123", "http://", "example.com/feed", "data:text/html,hi",
        ).forEach { assertFalse(isWebUrl(it), it) }
    }
}
