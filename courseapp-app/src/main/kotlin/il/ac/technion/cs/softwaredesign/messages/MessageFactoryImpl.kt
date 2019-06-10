package il.ac.technion.cs.softwaredesign.messages

import java.util.concurrent.CompletableFuture

class MessageFactoryImpl : MessageFactory {
    override fun create(media: MediaType, contents: ByteArray): CompletableFuture<Message> {
        TODO()
//        val msg = MessageImpl()
    }
}