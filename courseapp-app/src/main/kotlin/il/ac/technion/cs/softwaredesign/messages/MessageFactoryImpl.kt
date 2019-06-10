package il.ac.technion.cs.softwaredesign.messages

import com.google.inject.Inject
import il.ac.technion.cs.softwaredesign.utils.DatabaseMapper
import java.time.LocalDateTime
import java.util.concurrent.CompletableFuture

class MessageFactoryImpl @Inject constructor(dbMapper: DatabaseMapper) : MessageFactory {

    private val messagesRoot = dbMapper.getDatabase("course_app_database")
            .collection("messages_metadata")
            .document("messages_counters")

    override fun create(media: MediaType, contents: ByteArray): CompletableFuture<Message> {
        return messagesRoot.read("constructed_messages_count")
                .thenApply { serialId ->
                    serialId?.toLong()?.plus(1) ?: 1
                }.thenCompose { serialId ->
                    messagesRoot.set(Pair("constructed_messages_count", serialId.toString()))
                            .update()
                            .thenApply { serialId }
                }.thenApply { serialId ->
                    MessageImpl(serialId, media, contents, LocalDateTime.now())
                }
    }
}