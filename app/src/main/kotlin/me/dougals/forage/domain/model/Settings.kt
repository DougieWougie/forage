package me.dougals.forage.domain.model

enum class ReaderFont { SERIF, SANS, DYSLEXIC }

data class AppSettings(
    val syncMode: SyncMode = SyncMode.LOCAL,
    val readerFont: ReaderFont = ReaderFont.SERIF,
    val fontScale: Float = 1.0f,
    /** Off by default: feeds and article pages must resolve to public addresses (SSRF guard). */
    val allowLocalNetwork: Boolean = false,
) {
    companion object {
        const val MIN_SCALE = 0.8f
        const val MAX_SCALE = 2.0f
    }
}
