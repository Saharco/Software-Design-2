package il.ac.technion.cs.softwaredesign.managers.database

import il.ac.technion.cs.softwaredesign.CourseAppImpl
import il.ac.technion.cs.softwaredesign.ListenerCallback
import il.ac.technion.cs.softwaredesign.database.Database
import il.ac.technion.cs.softwaredesign.database.DocumentReference
import il.ac.technion.cs.softwaredesign.exceptions.InvalidTokenException
import il.ac.technion.cs.softwaredesign.exceptions.NoSuchEntityException
import il.ac.technion.cs.softwaredesign.exceptions.UserNotAuthorizedException
import il.ac.technion.cs.softwaredesign.messages.Message
import il.ac.technion.cs.softwaredesign.messages.MessageImpl
import il.ac.technion.cs.softwaredesign.utils.DatabaseMapper
import java.util.concurrent.CompletableFuture

/**
 * Manages messages in the app: this class wraps messaging functionality
 *
 * @see CourseAppImpl
 * @see Database
 *
 * @param dbMapper: mapper object that contains the app's open databases
 *
 */
class MessagesManager(private val dbMapper: DatabaseMapper) {
    private val messageListeners = HashMap<String, MutableList<ListenerCallback>>()
    private val dbName = "course_app_database"

    private val messagesRoot = dbMapper.getDatabase(dbName)
            .collection("all_messages")
    private val usersRoot = dbMapper.getDatabase(dbName)
            .collection("all_users")
    private val tokensRoot = dbMapper.getDatabase(dbName)
            .collection("tokens")
    private val channelsRoot = dbMapper.getDatabase(dbName)
            .collection("all_channels")
    private val metadataDocument = dbMapper.getDatabase(dbName)
            .collection("channels_metadata").document("channels_data")

    fun addListener(token: String, callback: ListenerCallback): CompletableFuture<Unit> {
        return tokenToUser(token)
                .thenApply { tokenUsername ->
                    if (messageListeners[tokenUsername] == null)
                        messageListeners[tokenUsername] = mutableListOf(callback)
                    else
                        messageListeners[tokenUsername]?.add(callback)
                    tokenUsername
                }.thenCompose { tokenUsername ->
                    tryReadingPendingMessages(tokenUsername, token, callback)
                }
    }

    fun removeListener(token: String, callback: ListenerCallback): CompletableFuture<Unit> {
        return tokenToUser(token)
                .thenApply {
                    if (messageListeners[token] == null || !messageListeners[token]!!.contains(callback))
                        throw NoSuchEntityException("given callback is not registered ")
                    else
                        messageListeners[token]!!.remove(callback)
                }.thenApply { }
    }

    fun channelSend(token: String, channel: String, message: Message): CompletableFuture<Unit> {
        return tokenToUser(token)
                .thenCompose { tokenUsername ->
                    channelsRoot.document(channel)
                            .exists()
                            .thenApply { exists ->
                                if (!exists)
                                    throw NoSuchEntityException("channel does not exist")
                                tokenUsername
                            }
                }.thenCompose { tokenUsername ->
                    isMemberOfChannel(tokenUsername, channel)
                            .thenApply { isMember ->
                                if (!isMember)
                                    throw UserNotAuthorizedException("user is not a member of given channel")
                                else
                                    tokenUsername
                            }
                }.thenCompose { tokenUsername ->

                }
    }

    fun broadcast(token: String, message: Message): CompletableFuture<Unit> {
        TODO()
    }

    fun privateSend(token: String, user: String, message: Message): CompletableFuture<Unit> {
        TODO()
    }

    fun fetchMessage(token: String, id: Long): CompletableFuture<Pair<String, Message>> {
        TODO()
    }

    /**
     * Invokes the callback for all broadcast, channel & private messages that the user *hasn't read*
     */
    private fun tryReadingPendingMessages(username: String, token: String, callback: ListenerCallback)
            : CompletableFuture<Unit> {
        return tryReadingBroadcastMessages(username, token, callback)
                .thenCompose { maxBroadcastId ->
                    tryReadingChannelMessages(username, token, callback)
                            .thenCompose { maxChannelId ->
                                tryReadingPrivateMessages(username, token, callback)
                                        .thenCompose { maxPrivateId ->
                                            val maxId = maxOf(maxBroadcastId, maxChannelId, maxPrivateId)
                                            usersRoot.document(username)
                                                    .set(Pair("last_message_read", maxId.toString()))
                                                    .update()
                                        }
                            }
                }
    }


    private fun tryReadingListOfMessages(username: String, token: String, callback: ListenerCallback,
                                         msgsListDoc: DocumentReference): CompletableFuture<Long> {
        return msgsListDoc.readList("messages")
                .thenApply { stringList ->
                    deserializeToMessagesList(stringList)
                }.thenCompose { msgsList ->
                    usersRoot.document(username)
                            .read("last_message_read")
                            .thenApply { lastIdRead -> Pair(msgsList, lastIdRead?.toLong() ?: 0) }
                }.thenCompose { (msgsList, lastIdRead) ->
                    tryReadingListOfMessagesAux(msgsListDoc, callback, msgsList, lastIdRead)
                }
    }

    private fun tryReadingListOfMessagesAux(msgsDoc: DocumentReference, callback: ListenerCallback,
                                            msgsList: MutableList<MessageImpl>, lastIdRead: Long, index: Int = 0,
                                            currentMax: Long = lastIdRead): CompletableFuture<Long> {
        if (msgsList.size <= index) {
            return msgsDoc.set("messages", serializeMessagesList(msgsList))
                    .update()
                    .thenApply { currentMax }
        }

        if (msgsList[index].id <= lastIdRead)
            return tryReadingListOfMessagesAux(
                    msgsDoc, callback, msgsList, lastIdRead, index + 1, currentMax)

        // user should read this message
        val pendingMessage = msgsList[index]
        return callback(pendingMessage.sender!!, pendingMessage)
                .thenApply {
                    if (pendingMessage.isDonePending()) {
                        msgsList.removeAt(index)
                        true
                    } else {
                        false
                    }
                }.thenCompose { toRewrite ->
                    val nextIndex = if (toRewrite) index else index + 1
                    val nextMax = maxOf(currentMax, pendingMessage.id)
                    tryReadingListOfMessagesAux(msgsDoc, callback, msgsList, lastIdRead, nextIndex, nextMax)
                }
    }

    /**
     * Invokes the callback for all broadcast messages that the user *hasn't read*
     */
    private fun tryReadingPrivateMessages(username: String, token: String, callback: ListenerCallback)
            : CompletableFuture<Long> {
        val msgsDocument = usersRoot.document(username)
        return tryReadingListOfMessages(username, token, callback, msgsDocument)
    }

    /**
     * Invokes the callback for all channel messages that the user *hasn't read*
     */
    private fun tryReadingChannelMessages(username: String, token: String, callback: ListenerCallback)
            : CompletableFuture<Long> {
        return usersRoot.document(username)
                .readList("channels")
                .thenCompose { channels ->
                    tryReadingChannelMessagesAux(channels, username, token, callback)
                }
    }

    private fun tryReadingChannelMessagesAux(channels: List<String>?, username: String, token: String,
                                             callback: ListenerCallback, index: Int = 0, currentMax: Long = 0)
            : CompletableFuture<Long> {
        if (channels == null || channels.size <= index)
            return CompletableFuture.completedFuture(currentMax)
        val msgsDocument = messagesRoot.document("broadcast_messages")
        return tryReadingListOfMessages(username, token, callback, msgsDocument)
                .thenCompose { currentChannelMax ->
                    val newMax = maxOf(currentMax, currentChannelMax)
                    tryReadingChannelMessagesAux(channels, username, token, callback, index + 1, newMax)
                }
    }

    /**
     * Invokes the callback for all private messages that the user *hasn't read*
     */
    private fun tryReadingBroadcastMessages(username: String, token: String, callback: ListenerCallback)
            : CompletableFuture<Long> {
        val msgsDocument = messagesRoot.document("broadcast_messages")
        return tryReadingListOfMessages(username, token, callback, msgsDocument)
    }

    /**
     * @param stringList: list of serialized messages
     * @return list of messages that were deserialized from [stringList]
     */
    private fun deserializeToMessagesList(stringList: List<String>?): MutableList<MessageImpl> {
        return stringList?.asSequence()
                ?.map { MessageImpl.deserialize(it) }
                ?.toMutableList() ?: mutableListOf()
    }


    private fun serializeMessagesList(msgsList: MutableList<MessageImpl>): MutableList<String> {
        return msgsList.asSequence()
                .map { it.serialize() }
                .toMutableList()
    }

    /**
     * Returns whether a given user is a member of a given channel or not
     */
    private fun isMemberOfChannel(username: String, channel: String): CompletableFuture<Boolean> {
        return usersRoot.document(username)
                .readList("channels")
                .thenApply { channels ->
                    channels != null && channels.contains(channel)
                }
    }

    /**
     * Translates a token to its corresponding user
     *
     * @throws InvalidTokenException if the token does not belong to any user
     */
    private fun tokenToUser(token: String): CompletableFuture<String> {
        return tokensRoot.document(token)
                .read("username")
                .thenApply { tokenUsername ->
                    tokenUsername ?: throw InvalidTokenException("token does not match any active user")
                }
    }
}