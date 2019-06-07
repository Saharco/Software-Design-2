package il.ac.technion.cs.softwaredesign.utils

import java.security.MessageDigest

/**
 *  Wrapper class for decrypting messages.
 *  Decryption is one-way, using the SHA-256 algorithm
 */
class Hasher {
    fun hash(message: String): String {
        val bytes = message.toByteArray()
        val md = MessageDigest.getInstance("SHA-256")
        val digest = md.digest(bytes)
        return digest.fold("", { str, it -> str + "%02x".format(it) })
    }

    /**
     * calls the [hash] method to encrypt a message
     */
    operator fun invoke(message: String): String {
        return hash(message)
    }
}