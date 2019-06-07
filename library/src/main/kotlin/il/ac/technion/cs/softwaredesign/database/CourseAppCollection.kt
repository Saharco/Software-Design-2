package il.ac.technion.cs.softwaredesign.database

import il.ac.technion.cs.softwaredesign.storage.SecureStorage
import il.ac.technion.cs.softwaredesign.utils.Hasher

/**
 * Implementation of [CollectionReference].
 *
 * This class is abstract - it can only be constructed via [Database] or [ExtendableDocumentReference]
 */
abstract class CourseAppCollection internal constructor(path: String, val storage: SecureStorage)
    : CollectionReference {
    private val path: String = "$path/"

    override fun document(name: String): ExtendableDocumentReference {
        val hasher = Hasher()
        return object : CourseAppExtendableDocument(
                path + hasher(name), storage) {}
    }
}