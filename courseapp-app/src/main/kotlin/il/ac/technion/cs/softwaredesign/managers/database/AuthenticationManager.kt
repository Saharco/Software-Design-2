package il.ac.technion.cs.softwaredesign.managers.database

import il.ac.technion.cs.softwaredesign.CourseAppImpl
import il.ac.technion.cs.softwaredesign.database.Database
import il.ac.technion.cs.softwaredesign.database.DocumentReference
import il.ac.technion.cs.softwaredesign.exceptions.InvalidTokenException
import il.ac.technion.cs.softwaredesign.exceptions.NoSuchEntityException
import il.ac.technion.cs.softwaredesign.exceptions.UserAlreadyLoggedInException
import il.ac.technion.cs.softwaredesign.exceptions.UserNotAuthorizedException
import il.ac.technion.cs.softwaredesign.utils.DatabaseMapper
import updateTree
import java.time.LocalDateTime
import java.util.concurrent.CompletableFuture

/**
 * Manages users in a database: this class wraps authentication functionality.
 * Provides common database operations regarding users and login session tokens
 *
 * @see CourseAppImpl
 * @see Database
 *
 * @param dbMapper: mapper object that contains the app's open databases
 *
 */
class AuthenticationManager(private val dbMapper: DatabaseMapper) {


    private val usersRoot = dbMapper.getDatabase("course_app_database")
            .collection("all_users")
    private val tokensRoot = dbMapper.getDatabase("course_app_database")
            .collection("tokens")
    private val channelsRoot = dbMapper.getDatabase("course_app_database")
            .collection("all_channels")
    private val metadataRoot = dbMapper.getDatabase("course_app_database")
            .collection("users_metadata")
    private val usersByChannelsStorage = dbMapper.getStorage("users_by_channels")
    private val channelsByActiveUsersStorage = dbMapper.getStorage("channels_by_active_users")

    fun performLogin(username: String, password: String): CompletableFuture<String> {
        val userDocument = usersRoot.document(username)
        return userDocument.read("password").thenApply { storedPassword ->

            if (storedPassword != null && storedPassword != password)
                throw NoSuchEntityException("incorrect password")
            else
                storedPassword
        }.thenCompose { storedPassword ->

            userDocument.read("token").thenApply { storedToken ->
                if (storedToken != null)
                    throw UserAlreadyLoggedInException("please logout before logging in again")
            }.thenApply {
                val token = generateToken(username)
                userDocument.set(Pair("token", token))
                Pair(storedPassword, token)
            }.thenCompose { pair ->
                updateLoginData(userDocument, pair.first, password, username)
                        .thenApply { pair.second }
            }.thenCompose { token ->
                tokensRoot.document(token)
                        .set(Pair("username", username))
                        .write()
                        .thenApply { token }
            }.thenApply { token ->
                token
            }
        }
    }

    fun performLogout(token: String): CompletableFuture<Unit> {
        val tokenDocument = tokensRoot.document(token)
        return tokenDocument.read("username")
                .thenApply { username ->
                    username ?: throw InvalidTokenException("token does not match any active user")
                }.thenCompose { username ->
                    tokenDocument.delete()
                            .thenApply { username }
                }.thenCompose { username ->
                    updateLogoutData(usersRoot.document(username))
                }
    }

    fun isUserLoggedIn(token: String, username: String): CompletableFuture<Boolean?> {
        return tokensRoot.document(token)
                .exists()
                .thenApply { exists ->
                    if (!exists)
                        throw InvalidTokenException("token does not match any active user")
                }.thenCompose {
                    usersRoot.document(username)
                            .exists()
                }.thenCompose { exists ->
                    if (!exists)
                        CompletableFuture.completedFuture(null as Boolean?)
                    else {
                        usersRoot.document(username)
                                .read("token")
                                .thenApply { otherToken ->
                                    otherToken != null
                                }
                    }
                }
    }


    fun makeAdministrator(token: String, username: String): CompletableFuture<Unit> {
        return tokensRoot.document(token)
                .read("username")
                .thenApply { tokenUsername ->
                    tokenUsername ?: throw InvalidTokenException("token does not match any active user")
                }.thenCompose { tokenUsername ->
                    usersRoot.document(tokenUsername)
                            .read("isAdmin")
                }.thenApply { isAdmin ->
                    isAdmin ?: throw UserNotAuthorizedException("no admin permission")
                }.thenCompose {
                    usersRoot.document(username)
                            .exists()
                }.thenApply { exists ->
                    if (!exists)
                        throw NoSuchEntityException("given user does not exist")
                }.thenCompose {
                    usersRoot.document(username)
                            .set(Pair("isAdmin", "true"))
                            .update()
                }
    }

    fun getTotalUsers(): CompletableFuture<Long> {
        return metadataRoot.document("users_data")
                .read("users_count")
                .thenApply { usersCount ->
                    usersCount?.toLong() ?: 0
                }
    }

    fun getLoggedInUsers(): CompletableFuture<Long> {
        return metadataRoot.document("users_data")
                .read("online_users_count")
                .thenApply { onlineUsersCount ->
                    onlineUsersCount?.toLong() ?: 0
                }
    }

    /**
     * Generates a unique token from a given username
     */
    private fun generateToken(username: String): String {
        return "$username+${LocalDateTime.now()}"
    }

    /**
     * Updates the following information:
     *  - number of logged in users in the system,
     *  - number of users in the system,
     *  - number of logged in users in each channel that the user is a member of,
     *  - administrator privilege is given to the user if they're the first user in the system,
     *  - password is written for the user if it's their first time logging in,
     *  - creation time is written for the user if it's their first time logging in
     *
     *  @param userDocument: fetched document of the user who's logging in
     *  @param storedPassword: the password currently stored in the user's document
     *  @param enteredPassword: the password entered by the user
     *
     */
    private fun updateLoginData(userDocument: DocumentReference, storedPassword: String?,
                                enteredPassword: String, username: String): CompletableFuture<Unit> {
        var future = CompletableFuture.completedFuture(Unit)

        if (storedPassword == null) {
            future = future.thenCompose {
                metadataRoot.document("users_data")
                        .read("creation_counter")
            }.thenApply { oldCounter ->
                oldCounter?.toInt()?.plus(1) ?: 1
            }.thenCompose { newCounter ->
                metadataRoot.document("users_data")
                        .set(Pair("creation_counter", newCounter.toString()))
                        .update()
                        .thenApply { newCounter }
            }.thenCompose { newCounter ->
                userDocument.set(Pair("password", enteredPassword))
                        .set(Pair("creation_time", LocalDateTime.now().toString()))
                        .set(Pair("creation_counter", newCounter.toString()))
                metadataRoot.document("users_data")
                        .read("users_count")
                        .thenApply { oldUsersCount -> Pair(newCounter, oldUsersCount?.toInt()?.plus(1) ?: 1) }
            }.thenCompose { pair ->
                if (pair.second == 1)
                    userDocument.set(Pair("isAdmin", "true"))
                metadataRoot.document("users_data")
                        .set(Pair("users_count", pair.second.toString()))
                        .update()
                        .thenApply { pair.first }
            }.thenApply { newCounter ->
                updateTree(usersByChannelsStorage, username, 0, 0, newCounter)
            }
        }

        val usersCountDocument = metadataRoot.document("users_data")

        return future.thenCompose {
            usersCountDocument.read("online_users_count")
        }.thenApply { oldOnlineUsersCount ->
            oldOnlineUsersCount?.toInt()?.plus(1) ?: 1
        }.thenCompose { newOnlineUsersCount ->
            usersCountDocument.set(Pair("online_users_count", newOnlineUsersCount.toString()))
                    .update()
        }.thenCompose {
            userDocument.readList("channels")
        }.thenApply { channels ->
            channels ?: listOf()
        }.thenCompose { channels ->
            updateUserChannels(channels, updateCount = 1)
        }.thenCompose {
            userDocument.update()
        }
    }

    private fun updateUserChannels(channels: List<String>, updateCount: Int, index: Int = 0):
            CompletableFuture<Unit> {
        if (channels.size <= index)
            return CompletableFuture.completedFuture(Unit)
        val channel = channels[index]

        return channelsRoot.document(channel)
                .read("online_users_count")
                .thenApply { oldOnlineUsersCount ->
                    oldOnlineUsersCount?.toInt()?.plus(updateCount) ?: 0
                }.thenCompose { newOnlineUsersCount ->
                    channelsRoot.document(channel)
                            .set(Pair("online_users_count", newOnlineUsersCount.toString()))
                            .update()
                            .thenApply { newOnlineUsersCount }
                }.thenCompose { newOnlineUsersCount ->
                    channelsRoot.document(channel).read("creation_counter")
                            .thenApply { creationCounter ->
                                Pair(newOnlineUsersCount, creationCounter!!.toInt())
                            }
                }.thenApply { pair ->
                    updateTree(channelsByActiveUsersStorage, channel,
                            pair.first, pair.first - updateCount, pair.second)
                }.thenCompose {
                    updateUserChannels(channels, updateCount, index + 1)
                }
    }

    /**
     * Updates the following information:
     *  - number of logged in users in the system,
     *  - number of users in the system,
     *  - number of logged in users in each channel that the user is a member of,
     *  - invalidating user token
     */
    private fun updateLogoutData(userDocument: DocumentReference): CompletableFuture<Unit> {
        val usersCountDocument = metadataRoot.document("users_data")
        return usersCountDocument.read("online_users_count")
                .thenApply { oldOnlineUsers ->
                    oldOnlineUsers?.toInt()?.minus(1) ?: 0
                }.thenCompose { newOnlineUsers ->
                    usersCountDocument.set(Pair("online_users_count", newOnlineUsers.toString()))
                            .update()
                }.thenCompose {
                    userDocument.readList("channels")
                }.thenApply { channels ->
                    channels ?: listOf()
                }.thenCompose { channels ->
                    updateUserChannels(channels, updateCount = -1)
                }.thenCompose {
                    userDocument.delete(listOf("token"))
                }
    }
}