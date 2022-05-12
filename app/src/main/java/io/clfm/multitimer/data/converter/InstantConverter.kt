package io.clfm.multitimer.data.converter

import androidx.room.TypeConverter
import java.time.Instant

/**
 * Encapsulates [TypeConverter] methods for converting an [Instant] to or from its database representation.
 */
class InstantConverter {

    @TypeConverter
    fun fromInstant(instant: Instant?): Long? {
        return instant?.toEpochMilli()
    }

    @TypeConverter
    fun toInstant(instantMillis: Long?): Instant? {
        return instantMillis?.let { Instant.ofEpochMilli(it) }
    }

}
