package il.ac.technion.cs.softwaredesign.utils

import il.ac.technion.cs.softwaredesign.database.Database
import il.ac.technion.cs.softwaredesign.storage.SecureStorage
import java.util.concurrent.CompletableFuture

/**
 * Wrapper class that maps database names to their respective databases and storage name to their respective storages
 * @param dbMap: String->Database map
 * @param storageMap: String->SecureStorage map
 */
class DatabaseMapper(private val dbMap: Map<String, CompletableFuture<Database>>,
                     private val storageMap: Map<String, CompletableFuture<SecureStorage>>) {

    fun getDatabase(dbName: String): Database {
        return dbMap.getValue(dbName).get()
    }

    fun getStorage(storageName: String): SecureStorage {
        return storageMap.getValue(storageName).get()
    }
}