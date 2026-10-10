package androidx.datastore.core

import kotlinx.coroutines.flow.Flow

/** `androidx.datastore.core.DataStore`: a value you read as a Flow and change atomically. */
interface DataStore<T> {
    val data: Flow<T>
    suspend fun updateData(transform: suspend (t: T) -> T): T
}
