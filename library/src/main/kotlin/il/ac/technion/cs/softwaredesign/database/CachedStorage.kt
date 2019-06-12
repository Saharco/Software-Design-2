package il.ac.technion.cs.softwaredesign.database

import il.ac.technion.cs.softwaredesign.database.datastructures.LimitedCacheMap
import il.ac.technion.cs.softwaredesign.storage.SecureStorage
import java.util.concurrent.CompletableFuture

class CachedStorage(private val storage: SecureStorage, limit: Int = 18000): SecureStorage {

    private val cache = LimitedCacheMap<String, ByteArray?>(limit)

    var cacheLimit: Int = limit
        set(newLimit) {
            cache.limit = newLimit
            field = newLimit
        }

    override fun read(key: ByteArray): CompletableFuture<ByteArray?> {
        val keyString = String(key)
        if (cache.containsKey(keyString))
            return CompletableFuture.completedFuture(cache[keyString])
        return storage.read(key)
                .thenApply { readValue ->
                    cache[keyString] = readValue
                    readValue
                }
    }

    override fun write(key: ByteArray, value: ByteArray): CompletableFuture<Unit> {
        return storage.write(key, value).thenApply {
            cache[String(key)] = value
        }
    }
}