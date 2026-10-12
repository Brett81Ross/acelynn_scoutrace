package com.cactusbyte.scouttrace

/** Shared input is transient, user-triggered, and never used as a web URL or executable code. */
object SharedTextReview {
    const val MAX_CHARS = 4000

    fun normalize(input: CharSequence?): String? {
        val text = input?.toString()?.take(MAX_CHARS) ?: return null
        return text.takeIf { it.isNotBlank() }
    }
}
