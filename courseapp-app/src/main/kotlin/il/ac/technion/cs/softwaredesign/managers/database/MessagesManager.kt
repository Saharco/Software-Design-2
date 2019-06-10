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
    private val usersMetadataRoot = dbMapper.getDatabase("course_app_database")
            .collection("users_metadata")

    fun addListener(token: String, callback: ListenerCallback): CompletableFuture<Unit> {
        return tokenToUser(token)
                .thenApply { tokenUsername ->
                    if (messageListeners[tokenUsername] == null)
                        messageListeners[tokenUsername] = mutableListOf(callback)
                    else
                        messageListeners[tokenUsername]?.add(callback)
                    tokenUsername
                }.thenCompose { tokenUsername ->
                    tryReadingPendingMessages(tokenUsername, callback)
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
        val messageToSend = message as MessageImpl
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
                    channelsRoot.document(channel)
                            .read("users_count")
                            .thenApply { Pair(tokenUsername, it!!.toLong()) }
                }.thenCompose { (tokenUsername, channelUsersCount) ->
                    messageToSend.sender = "$channel@$tokenUsername"
                    messageToSend.usersCount = channelUsersCount
                    val messageDoc = messagesRoot.document("${channel}_messages")
                    uploadMessage(messageToSend, messageDoc)
                            .thenApply { messageToSend }
                }.thenCompose { uploadedMessage ->
                    invokeChannelCallbacks(channel, messageListeners.keys.toList(), uploadedMessage)
                }
    }

    fun broadcast(token: String, message: Message): CompletableFuture<Unit> {
        val messageToSend = message as MessageImpl
        return tokenToUser(token)
                .thenCompose { tokenUsername ->
                    isAdmin(tokenUsername)
                            .thenApply { isAdmin ->
                                if (!isAdmin)
                                    throw UserNotAuthorizedException("only admin may send broadcast messages")
                                tokenUsername
                            }
                }.thenCompose {
                    usersMetadataRoot.document("users_data")
                            .read("users_count")
                            .thenApply { it!!.toLong() }
                }.thenCompose { usersCount ->
                    messageToSend.sender = "BROADCAST"
                    messageToSend.usersCount = usersCount
                    val messageDoc = messagesRoot.document("broadcast_messages")
                    uploadMessage(messageToSend, messageDoc)
                            .thenApply { messageToSend }
                }.thenCompose { uploadedMessage ->
                    invokeBroadcastCallbacks(messageListeners.keys.toList(), uploadedMessage)
                }
    }

    fun privateSend(token: String, user: String, message: Message): CompletableFuture<Unit> {
        val messageToSend = message as MessageImpl
        return tokenToUser(token)
                .thenCompose { tokenUsername ->
                    usersRoot.document(user)
                            .exists()
                            .thenApply { exists ->
                                if (!exists)
                                    throw NoSuchEntityException("$user does not exist")
                                tokenUsername
                            }
                }.thenCompose { tokenUsername ->
                    messageToSend.sender = "@$tokenUsername"
                    messageToSend.usersCount = 1
                    val messageDoc = usersRoot.document(user)
                    uploadMessage(messageToSend, messageDoc)
                            .thenApply { messageToSend }
                }.thenCompose { uploadedMessage ->
                    invokeUserCallbacks(user, uploadedMessage)
                }
    }

    fun fetchMessage(token: String, id: Long): CompletableFuture<Pair<String, Message>> {
        TODO()
    }

    /**
     * Invokes the callback for all broadcast, channel & private messages that the user *hasn't read*
     */
    private fun tryReadingPendingMessages(username: String, callback: ListenerCallback)
            : CompletableFuture<Unit> {
        return tryReadingBroadcastMessages(username, callback)
                .thenCompose { maxBroadcastId ->
                    tryReadingChannelMessages(username, callback)
                            .thenCompose { maxChannelId ->
                                tryReadingPrivateMessages(username, callback)
                                        .thenCompose { maxPrivateId ->
                                            val maxId = maxOf(maxBroadcastId, maxChannelId, maxPrivateId)
                                            usersRoot.document(username)
                                                    .set(Pair("last_message_read", maxId.toString()))
                                                    .update()
                                        }
                            }
                }
    }


    private fun tryReadingListOfMessages(username: String, callback: ListenerCallback,
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
    private fun tryReadingPrivateMessages(username: String, callback: ListenerCallback)
            : CompletableFuture<Long> {
        val msgsDocument = usersRoot.document(username)
        return tryReadingListOfMessages(username, callback, msgsDocument)
    }

    /**
     * Invokes the callback for all channel messages that the user *hasn't read*
     */
    private fun tryReadingChannelMessages(username: String, callback: ListenerCallback)
            : CompletableFuture<Long> {
        return usersRoot.document(username)
                .readList("channels")
                .thenCompose { channels ->
                    tryReadingChannelMessagesAux(channels, username, callback)
                }
    }

    private fun tryReadingChannelMessagesAux(channels: List<String>?, username: String,
                                             callback: ListenerCallback, index: Int = 0, currentMax: Long = 0)
            : CompletableFuture<Long> {
        if (channels == null || channels.size <= index)
            return CompletableFuture.completedFuture(currentMax)
        val msgsDocument = messagesRoot.document("${channels[index]}_messages")
        return tryReadingListOfMessages(username, callback, msgsDocument)
                .thenCompose { currentChannelMax ->
                    val newMax = maxOf(currentMax, currentChannelMax)
                    tryReadingChannelMessagesAux(channels, username, callback, index + 1, newMax)
                }
    }

    /**
     * Invokes the callback for all private messages that the user *hasn't read*
     */
    private fun tryReadingBroadcastMessages(username: String, callback: ListenerCallback)
            : CompletableFuture<Long> {
        val msgsDocument = messagesRoot.document("broadcast_messages")
        return tryReadingListOfMessages(username, callback, msgsDocument)
    }

    private fun invokeChannelCallbacks(channel: String, users: List<String>, message: MessageImpl, index: Int = 0)
            : CompletableFuture<Unit> {
        if (users.size <= index)
            return CompletableFuture.completedFuture(Unit)
        return isMemberOfChannel(users[index], channel)
                .thenCompose { result ->
                    if (result) {
                        invokeUserCallbacks(users[index], message)
                    } else {
                        CompletableFuture.completedFuture(Unit)
                    }
                }.thenCompose {
                    invokeChannelCallbacks(channel, users, message, index + 1)
                }
    }

    private fun invokeBroadcastCallbacks(users: List<String>, message: MessageImpl, index: Int = 0)
            : CompletableFuture<Unit> {
        if (users.size <= index)
            return CompletableFuture.completedFuture(Unit)
        return invokeUserCallbacks(users[index], message)
                .thenCompose {
                    invokeBroadcastCallbacks(users, message, index + 1)
                }
    }

    private fun invokeUserCallbacks(username: String, message: MessageImpl, index: Int = 0): CompletableFuture<Unit> {
        val userCallbacks = messageListeners[username]
        if (userCallbacks == null || userCallbacks.size <= index)
            return CompletableFuture.completedFuture(Unit)
        return userCallbacks[index](message.sender!!, message)
                .thenCompose { invokeUserCallbacks(username, message, index + 1) }
    }

    private fun uploadMessage(messageToSend: MessageImpl, messageDoc: DocumentReference): CompletableFuture<Unit> {
        return messageDoc.readList("messages")
                .thenApply {
                    val msgsList = deserializeToMessagesList(it)
                    msgsList.add(messageToSend)
                    msgsList
                }.thenCompose { msgsList ->
                    messageDoc.set("messages", serializeMessagesList(msgsList))
                            .update()
                }
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
     * Returns whether or not a given user is an administrator
     */
    private fun isAdmin(username: String): CompletableFuture<Boolean> {
        return usersRoot.document(username)
                .read("isAdmin")
                .thenApply { isAdmin ->
                    isAdmin == "true"
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