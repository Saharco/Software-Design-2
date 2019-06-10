package il.ac.technion.cs.softwaredesign.messages

import java.time.LocalDateTime

class MessageImpl(override val id: Long, override val media: MediaType, override val contents: ByteArray,
                  override val created: LocalDateTime, override var received: LocalDateTime? = null) : Message