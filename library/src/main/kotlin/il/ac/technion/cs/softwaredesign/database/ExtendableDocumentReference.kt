package il.ac.technion.cs.softwaredesign.database


/**
 * Reference to a document inside the database that may also contain other collections.
 * @see DocumentReference
 */
interface ExtendableDocumentReference : DocumentReference {
    /**
     * Access a collection inside this document. It will be created if it does not exist
     *
     * @param name: name of the collection
     * @return the collection's reference
     */
    fun collection(name: String): CollectionReference
}
