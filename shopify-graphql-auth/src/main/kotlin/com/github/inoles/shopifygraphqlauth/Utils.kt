package com.github.inoles.shopifygraphqlauth

import kotlinx.datetime.Instant

fun parseUTCDateTime(dateTimeString: String): Instant =
    try {
        Instant.parse(dateTimeString)
    } catch (e: Exception) {
        throw IllegalArgumentException("Invalid UTC date format", e)
    }
