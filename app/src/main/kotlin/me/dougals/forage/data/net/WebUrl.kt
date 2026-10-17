package me.dougals.forage.data.net

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/** True only for real http(s) URLs with a host. Gate for anything handed to an intent or fetched on a feed's say-so. */
fun isWebUrl(url: String): Boolean = url.trim().toHttpUrlOrNull() != null
