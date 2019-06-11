package il.ac.technion.cs.softwaredesign.messages

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.ObjectInputStream
import java.io.ObjectOutputStream
import java.io.Serializable
import java.time.LocalDateTime

class MessageImpl(override val id: Long, override val media: MediaType, override val contents: ByteArray,
                  override val created: LocalDateTime, var usersCount: Long = 0,
                  override var received: LocalDateTime? = null, var sender: String? = null) : Message, Serializable {

    companion object {

        private const val serialVersionUID = 43L
        private val charset = Charsets.UTF_8

        fun deserialize(string: String): MessageImpl {
            val bais = ByteArrayInputStream(string.toByteArray(charset("ISO-8859-1")))
            val ois = ObjectInputStream(bais)
            return ois.readObject() as MessageImpl
        }
    }

    constructor(msg: MessageImpl)
            : this(msg.id, msg.media, msg.contents, msg.created, msg.usersCount, msg.received, msg.sender)

    fun serialize(): String {
        val baos = ByteArrayOutputStream()
        val oos = ObjectOutputStream(baos)
        oos.writeObject(this)
        oos.close()
        return baos.toString("ISO-8859-1")
    }

    fun isDonePending(): Boolean {
        return usersCount == 0L
    }
}