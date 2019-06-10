package il.ac.technion.cs.softwaredesign.messages

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.ObjectInputStream
import java.io.ObjectOutputStream
import java.io.Serializable
import java.time.LocalDateTime


class MessageImpl(override val id: Long, override val media: MediaType, override val contents: ByteArray,
                  override val created: LocalDateTime, override var received: LocalDateTime? = null,
                  var sender: String? = null, var usersCount: Long = 0) : Message, Serializable {

    companion object {
        fun deserialize(string: String): MessageImpl {
            return ObjectInputStream(ByteArrayInputStream(string.toByteArray())).readObject() as MessageImpl
        }
    }

    fun serialize(): String {
        val outputStream = ByteArrayOutputStream()
        ObjectOutputStream(outputStream).writeObject(this)
        return outputStream.toString()
    }

    fun isDonePending(): Boolean {
        return usersCount == 0L
    }
}