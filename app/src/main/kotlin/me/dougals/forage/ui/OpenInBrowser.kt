package me.dougals.forage.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import me.dougals.forage.data.net.isWebUrl

/** Only http(s) URLs are ever handed to an intent; a missing browser is not an error. */
fun Context.openInBrowser(url: String) {
    if (!isWebUrl(url)) return
    try {
        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (_: ActivityNotFoundException) {
        // no browser installed; nothing sensible to do
    }
}
