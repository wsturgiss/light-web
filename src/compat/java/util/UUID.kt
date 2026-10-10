package java.util

import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/** `java.util.UUID`, as tools use it: random ids, as strings. */
@OptIn(ExperimentalUuidApi::class)
class UUID private constructor(private val uuid: Uuid) {
    override fun toString() = uuid.toString()
    override fun equals(other: Any?) = other is UUID && other.uuid == uuid
    override fun hashCode() = uuid.hashCode()

    companion object {
        fun randomUUID() = UUID(Uuid.random())
        fun fromString(s: String) = UUID(Uuid.parse(s))
    }
}
