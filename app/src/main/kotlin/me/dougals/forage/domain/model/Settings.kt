package me.dougals.forage.domain.model

enum class ReaderFont { SERIF, SANS }

data class AppSettings(
    val syncMode: SyncMode = SyncMode.LOCAL,
    val readerFont: ReaderFont = ReaderFont.SERIF,
    val fontScale: Float = 1.0f,
) {
    companion object {
        const val MIN_SCALE = 0.8f
        const val MAX_SCALE = 2.0f
    }
}
