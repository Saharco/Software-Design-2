package il.ac.technion.cs.softwaredesign.utils

import il.ac.technion.cs.softwaredesign.database.Database
import il.ac.technion.cs.softwaredesign.storage.SecureStorage

/**
 * Wrapper class that maps database names to their respective databases and storage name to their respective storages
 * @param dbMap: String->Database map
 * @param storageMap: String->SecureStorage map
 */
class DatabaseMapper(private val dbMap: Map<String, Database>,
                     private val storageMap: Map<String, SecureStorage>) {

    fun getDatabase(dbName: String): Database {
        return dbMap.getValue(dbName)
    }

    fun getStorage(storageName: String): SecureStorage {
        return storageMap.getValue(storageName)
    }
}