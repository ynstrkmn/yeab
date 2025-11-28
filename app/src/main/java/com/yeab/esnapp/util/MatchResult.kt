// kotlin
package com.yeab.esnapp.util

data class MatchResult(
    val imageId: String,
    val imageUrl: String?,
    val phash: String,
    val distance: Int,
    val percentage: Int
)