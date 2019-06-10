package il.ac.technion.cs.softwaredesign.managers.database

import il.ac.technion.cs.softwaredesign.CourseAppImpl
import il.ac.technion.cs.softwaredesign.ListenerCallback
import il.ac.technion.cs.softwaredesign.database.Database
import il.ac.technion.cs.softwaredesign.exceptions.InvalidTokenException
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
    private val usersRoot = dbMapper.getDatabase("course_app_database")
            .collection("all_users")
    private val tokensRoot = dbMapper.getDatabase("course_app_database")
            .collection("tokens")
    private val channelsRoot = dbMapper.getDatabase("course_app_database")
            .collection("all_channels")
    private val metadataDocument = dbMapper.getDatabase("course_app_database")
            .collection("channels_metadata").document("channels_data")

    fun addListener(token: String, callback: ListenerCallback): CompletableFuture<Unit> {
        return tokenToUser(token)
                .thenApply { tokenUsername ->
                    if (messageListeners[tokenUsername] == null)
                        messageListeners[tokenUsername] = mutableListOf(callback)
                    else
                        messageListeners[tokenUsername]?.add(callback)
                }.thenCompose { CompletableFuture.completedFuture(Unit) }
    }

    fun removeListener(token: String, callback: ListenerCallback): CompletableFuture<Unit> {

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