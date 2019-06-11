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

        private const val serialVersionUID = 43L
        private val charset = Charsets.UTF_8

        fun deserialize(string: String): MessageImpl {
            return ObjectInputStream(ByteArrayInputStream(string.toByteArray())).readObject() as MessageImpl
        }
    }

    constructor(msg: MessageImpl)
            : this(msg.id, msg.media, msg.contents, msg.created, msg.received, msg.sender, msg.usersCount)

    fun serialize(): String {
        val outputStream = ByteArrayOutputStream()
        ObjectOutputStream(outputStream).writeObject(this)
        return outputStream.toString(charset)
    }

    fun isDonePending(): Boolean {
        return usersCount == 0L
    }
}