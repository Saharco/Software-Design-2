package il.ac.technion.cs.softwaredesign.managers.database

import il.ac.technion.cs.softwaredesign.CourseAppImpl
import il.ac.technion.cs.softwaredesign.database.Database
import il.ac.technion.cs.softwaredesign.exceptions.InvalidTokenException
import il.ac.technion.cs.softwaredesign.exceptions.NameFormatException
import il.ac.technion.cs.softwaredesign.exceptions.NoSuchEntityException
import il.ac.technion.cs.softwaredesign.exceptions.UserNotAuthorizedException
import il.ac.technion.cs.softwaredesign.utils.DatabaseMapper
import treeTopK
import updateTree
import java.time.LocalDateTime
import java.util.concurrent.CompletableFuture


/**
 * Manages channels in the app: this class wraps channels functionality
 *
 * @see CourseAppImpl
 * @see Database
 *
 * @param dbMapper: mapper object that contains the app's open databases
 *
 */
class ChannelsManager(private val dbMapper: DatabaseMapper) {

    private val usersRoot = dbMapper.getDatabase("users")
            .collection("all_users")
    private val tokensRoot = dbMapper.getDatabase("users")
            .collection("tokens")
    private val channelsRoot = dbMapper.getDatabase("channels")
            .collection("all_channels")
    private val metadataDocument = dbMapper.getDatabase("channels")
            .collection("metadata").document("channels_data")

    private val channelsByUsers = dbMapper.getStorage("channels_by_users")
    private val channelsByActiveUsers = dbMapper.getStorage("channels_by_active_users")
    private val usersByChannels = dbMapper.getStorage("users_by_channels")

    fun channelJoin(token: String, channel: String): CompletableFuture<Unit> {

        /**
         * verifies that the user isn't already a member of the channel,
         * checks for admin privilege if channel is new,
         * adds channel to user's channels list,
         * creates channel if its new,
         * updates total users of channel,
         * updates online users of channel,
         * updates trees
         */

        return tokenToUser(token)
                .thenApply { tokenUsername ->
                    if (!validChannelName(channel))
                        throw NameFormatException("invalid channel name: $channel")
                    else
                        tokenUsername
                }.thenCompose { tokenUsername ->
                    usersRoot.document(tokenUsername)
                            .readList("channels")
                            .thenApply { userChannels ->
                                userChannels?.toMutableList() ?: mutableListOf()
                            }.thenApply { userChannels ->
                                if (!userChannels.contains(channel)) {
                                    createNewChannelIfNeeded(channel, tokenUsername)
                                            .thenCompose { isNewChannel ->
                                                addChannelToUserList(tokenUsername, userChannels, channel)
                                                        .thenCompose {
                                                            if (!isNewChannel) {
                                                                updateChannelUsersCount(channel, tokenUsername)
                                                                        .thenCompose {
                                                                            updateChannelOnlineUsersCountIfOnline(
                                                                                    channel, tokenUsername)
                                                                        }
                                                            }
                                                        }
                                            }
                                }
                            }
                }

        val tokenUsername = tokenToUser(token)
        if (!validChannelName(channel)) throw NameFormatException("invalid channel name: $channel")

        val userChannels = usersRoot.document(tokenUsername)
                .readList("channels").join()?.toMutableList() ?: mutableListOf()

        // finish if user attempts to join a channel they're already members of
        if (userChannels.contains(channel)) return

        var newChannelFlag = false
        if (!channelsRoot.document(channel).exists().join()) {
            if (!isAdmin(tokenUsername))
                throw UserNotAuthorizedException("only an administrator may create a new channel")

            createNewChannel(channel, tokenUsername)
            newChannelFlag = true
        }

        userChannels.add(channel)

        usersRoot.document(tokenUsername)
                .set("channels", userChannels)
                .set(Pair("channels_count", userChannels.size.toString()))
                .update()

        var usersCount = 1
        var onlineUsersCount = 1
        if (!newChannelFlag) {
            usersCount = channelsRoot.document(channel)
                    .read("users_count").join()?.toInt()?.plus(1) ?: 1
            channelsRoot.document(channel)
                    .set(Pair("users_count", usersCount.toString()))
                    .update()

            onlineUsersCount = channelsRoot.document(channel)
                    .read("online_users_count").join()?.toInt()?.plus(1) ?: 1
            channelsRoot.document(channel)
                    .set(Pair("online_users_count", onlineUsersCount.toString()))
                    .update()
        }

        val channelCreationCounter = channelsRoot.document(channel)
                .read("creation_counter").join()?.toInt() ?: 0
        val userCreationCounter = usersRoot.document(tokenUsername)
                .read("creation_counter").join()?.toInt() ?: 0
        val usersChannelsCount = userChannels.size

        updateTree(channelsByUsers, channel, usersCount, usersCount - 1,
                channelCreationCounter)
        updateTree(channelsByActiveUsers, channel, onlineUsersCount, onlineUsersCount - 1,
                channelCreationCounter)
        updateTree(usersByChannels, tokenUsername, usersChannelsCount,
                usersChannelsCount - 1, userCreationCounter)
    }

    private fun addChannelToUserList(username: String, userChannels: MutableList<String>, channel: String)
            : CompletableFuture<Unit> {
        userChannels.add(channel)

        return usersRoot.document(username)
                .set("channels", userChannels)
                .set(Pair("channels_count", userChannels.size.toString()))
                .update()
    }

    fun channelPart(token: String, channel: String): CompletableFuture<Unit> {
        return tokenToUser(token)
                .thenCompose { tokenUsername ->
                    verifyChannelExists(channel).thenApply { tokenUsername }
                }.thenCompose { tokenUsername ->
                    isMemberOfChannel(tokenUsername, channel)
                            .thenApply { isMember ->
                                if (!isMember)
                                    throw NoSuchEntityException("user is not a member of the channel")
                            }.thenCompose {
                                expelChannelMember(tokenUsername, channel)
                            }
                }
    }

    fun channelMakeOperator(token: String, channel: String, username: String): CompletableFuture<Unit> {
        return tokenToUser(token)
                .thenCompose { tokenUsername ->
                    verifyChannelExists(channel).thenApply { tokenUsername }
                }.thenCompose { tokenUsername ->
                    verifyOperatorPromoterPrivilege(channel, tokenUsername, username)
                }.thenCompose {
                    channelsRoot.document(channel)
                            .readList("operators")
                }.thenApply { operators ->
                    operators?.toMutableList() ?: mutableListOf()
                }.thenCompose { operators ->
                    operators.add(username)
                    channelsRoot.document(channel)
                            .set("operators", operators)
                            .update()
                }
    }

    fun channelKick(token: String, channel: String, username: String): CompletableFuture<Unit> {
        return tokenToUser(token)
                .thenCompose { tokenUsername ->
                    verifyChannelExists(channel).thenApply { tokenUsername }
                }.thenCompose { tokenUsername ->
                    isOperator(tokenUsername, channel)
                            .thenApply { isOperator ->
                                if (!isOperator)
                                    throw UserNotAuthorizedException("must have operator privileges")
                            }.thenCompose {
                                isMemberOfChannel(username, channel)
                                        .thenApply { isMember ->
                                            if (!isMember)
                                                throw NoSuchEntityException(
                                                        "provided username is not a member of this channel")
                                        }.thenCompose {
                                            expelChannelMember(username, channel)
                                        }
                            }
                }
    }

    fun isUserInChannel(token: String, channel: String, username: String): CompletableFuture<Boolean?> {
        return verifyValidAndPrivilegedToQuery(token, channel)
                .thenCompose {
                    usersRoot.document(username)
                            .exists()
                }.thenCompose { exists ->
                    if (!exists)
                        CompletableFuture.completedFuture(null as Boolean?)
                    else
                        @Suppress("UNCHECKED_CAST")
                        isMemberOfChannel(username, channel) as CompletableFuture<Boolean?>
                }
    }

    fun numberOfActiveUsersInChannel(token: String, channel: String): CompletableFuture<Long> {
        return verifyValidAndPrivilegedToQuery(token, channel)
                .thenCompose {
                    channelsRoot.document(channel)
                            .read("online_users_count")
                            .thenApply { usersCount -> usersCount?.toLong() ?: 0 }
                }
    }

    fun numberOfTotalUsersInChannel(token: String, channel: String): CompletableFuture<Long> {
        return verifyValidAndPrivilegedToQuery(token, channel)
                .thenCompose {
                    channelsRoot.document(channel)
                            .read("users_count")
                            .thenApply { usersCount -> usersCount?.toLong() ?: 0 }
                }
    }

    fun topKChannelsByUsers(k: Int = 10): CompletableFuture<List<String>> {
        //FIXME
//        return treeTopK(channelsByUsers, k)
        return CompletableFuture.completedFuture(treeTopK(channelsByUsers, k))
    }


    fun topKChannelsByActiveUsers(k: Int = 10): CompletableFuture<List<String>> {
        //FIXME
//        return treeTopK(channelsByActiveUsers, k)
        return CompletableFuture.completedFuture(treeTopK(channelsByActiveUsers, k))
    }


    fun topKUsersByChannels(k: Int = 10): CompletableFuture<List<String>> {
        //FIXME
//        return treeTopK(usersByChannels, k)
        return CompletableFuture.completedFuture(treeTopK(usersByChannels, k))
    }

    /**
     * Creates a new channel with a given operator for the channel
     */
    private fun createNewChannelIfNeeded(channel: String, operatorUsername: String): CompletableFuture<Boolean> {
        return channelsRoot.document(channel)
                .exists()
                .thenCompose { exists ->
                    if (exists)
                        CompletableFuture.completedFuture(false)
                    else {
                        isAdmin(operatorUsername)
                                .thenApply { isAdmin ->
                                    if (!isAdmin)
                                        throw UserNotAuthorizedException("only an administrator may create a new channel")
                                }.thenCompose {
                                    // creation of new channel

                                    metadataDocument.read("creation_counter")
                                            .thenApply { oldCreationCounter ->
                                                oldCreationCounter?.toInt()?.plus(1) ?: 1
                                            }.thenCompose { creationCounter ->
                                                metadataDocument.set(Pair("creation_counter", creationCounter.toString()))
                                                        .update()
                                                        .thenApply { creationCounter }
                                            }.thenCompose { creationCounter ->
                                                channelsRoot.document(channel)
                                                        .set("operators", listOf(operatorUsername))
                                                        .set(Pair("users_count", "1"))
                                                        .set(Pair("online_users_count", "1"))
                                                        .set(Pair("creation_time", LocalDateTime.now().toString()))
                                                        .set(Pair("creation_counter", creationCounter.toString()))
                                                        .write()
                                                        .thenApply { true }
                                            }
                                }
                    }
                }
    }

    /**
     * Verifies the token & channel for *querying operations*
     *
     * @throws InvalidTokenException If the auth [token] is invalid.
     * @throws NoSuchEntityException If [channel] does not exist.
     * @throws UserNotAuthorizedException If [token] identifies a user who is not an administrator and is not a member
     */
    private fun verifyValidAndPrivilegedToQuery(token: String, channel: String): CompletableFuture<Unit> {
        return verifyChannelExists(channel)
                .thenCompose { tokenToUser(token) }
                .thenCompose { tokenUsername ->
                    isAdmin(tokenUsername)
                            .thenCompose { isAdmin ->
                                if (isAdmin)
                                    CompletableFuture.completedFuture(Unit)
                                else
                                    isMemberOfChannel(tokenUsername, channel)
                                            .thenApply { isMember ->
                                                if (!isMember)
                                                    throw UserNotAuthorizedException(
                                                            "must be an admin or a member of the channel")
                                            }
                            }
                }
    }

    /**
     * Makes sure that a given channel exists
     *
     * @throws NoSuchEntityException if the channel does not exist
     */
    private fun verifyChannelExists(channel: String): CompletableFuture<Unit> {
        return channelsRoot.document(channel)
                .exists()
                .thenApply { exists ->
                    if (!exists)
                        throw NoSuchEntityException("given channel does not exist")
                }
    }

    /**
     * Verifies that a user can appoint another use to be an operator of a channel
     *
     * @param channel: name of the channel
     * @param tokenUsername: username of the promoter
     * @param username: username of the user who's to be promoted
     *
     * @throws UserNotAuthorizedException if [tokenUsername] does not have the privilege to promote [username] to be an operator of the channel
     */
    private fun verifyOperatorPromoterPrivilege(channel: String, tokenUsername: String, username: String):
            CompletableFuture<Unit> {
        return isAdmin(tokenUsername)
                .thenCompose { isAdmin ->
                    isOperator(tokenUsername, channel)
                            .thenApply { isOperator ->
                                if (!isAdmin && !isOperator)
                                    throw UserNotAuthorizedException("user is not an operator / administrator")

                                if (isAdmin && !isOperator && tokenUsername != username)
                                    throw UserNotAuthorizedException("administrator who's not an operator cannot " +
                                            "appoint other users to be operators")
                            }.thenCompose {
                                isMemberOfChannel(tokenUsername, channel)
                                        .thenApply { isMember ->
                                            if (!isMember)
                                                throw UserNotAuthorizedException("user is not a member in the channel")
                                        }
                            }
                }
    }

    /**
     * Kicks a user from a channel, along with all necessary updates:
     *
     *  removes channel from user's channels' list,
     *  removes operator from channel's operators list if the user is an operator for this channel,
     *  decreases users count of channel by 1,
     *  decreases online users count of channel by 1 if user is logged in,
     *  deletes channel if it is empty,
     *  updates query trees
     *
     */
    private fun expelChannelMember(username: String, channel: String): CompletableFuture<Unit> {
        return removeChannelFromUserList(username, channel)
                .thenCompose { removeOperatorFromChannel(channel, username) }
                .thenCompose { userChannelsCount ->
                    updateChannelUsersCount(channel, change = -1)
                            .thenApply { totalUsers -> Pair(userChannelsCount, totalUsers) }
                }.thenCompose { pair ->
                    updateChannelOnlineUsersCountIfOnline(channel, username, change = -1)
                            .thenApply { onlineUsers -> Triple(pair.first, pair.second, onlineUsers) }
                }.thenCompose { triple ->
                    if (triple.second == 0)
                        deleteChannel(channel)
                                .thenApply { triple }
                    else
                        CompletableFuture.completedFuture(triple)
                }.thenCompose { triple ->
                    decreaseTrees(channel, username, triple.first, triple.second, triple.third)
                }
    }

    private fun decreaseTrees(channel: String, username: String, userChannelsCount: Int, channelTotalUsers: Int,
                              channelOnlineUsers: Int): CompletableFuture<Unit> {
        return channelsRoot.document(channel)
                .read("creation_counter")
                .thenApply { channelCreationCounter ->
                    channelCreationCounter?.toInt() ?: 0
                }.thenCompose { channelCreationCounter ->
                    usersRoot.document(username)
                            .read("creation_counter")
                            .thenApply { userCreationCounter ->
                                Pair(channelCreationCounter, userCreationCounter?.toInt() ?: 0)
                            }
                }.thenCompose { pair ->
                    usersRoot.document(username)
                            .read("token")
                            .thenApply { token ->
                                if (token != null)
                                    Triple(pair.first, pair.second, channelOnlineUsers + 1)
                                else
                                    Triple(pair.first, pair.second, channelOnlineUsers)
                            }
                }.thenApply { triple ->
                    //FIXME - trees should also work with futures
                    updateTree(channelsByUsers, channel, channelTotalUsers, channelTotalUsers + 1,
                            triple.first, channelTotalUsers <= 0)
                    updateTree(channelsByActiveUsers, channel, channelOnlineUsers, triple.third, triple.first)
                    updateTree(usersByChannels, username, userChannelsCount, userChannelsCount + 1,
                            triple.second)
                }
    }

    /**
     * Deletes a channel document from the database
     */
    private fun deleteChannel(channel: String): CompletableFuture<Unit> {
        return channelsRoot.document(channel)
                .delete()
    }

    /**
     * Removes a channel from a user's list of channels
     *
     * @return amount of channels the user is a member of after removal
     */
    private fun removeChannelFromUserList(username: String, channel: String): CompletableFuture<Int> {
        return usersRoot.document(username)
                .readList("channels")
                .thenApply { userChannels ->
                    userChannels?.toMutableList() ?: mutableListOf()
                }.thenCompose { userChannels ->
                    userChannels.remove(channel)
                    usersRoot.document(username)
                            .set("channels", userChannels)
                            .set(Pair("channels_count", userChannels.size.toString()))
                            .update()
                            .thenApply { userChannels.size }
                }
    }

    /**
     * Updates a channel's users count
     *
     * @return amount of users in the channel after the update
     */
    private fun updateChannelUsersCount(channel: String, change: Int = 1): CompletableFuture<Int> {
        return channelsRoot.document(channel)
                .read("users_count")
                .thenApply { oldUsersCount ->
                    oldUsersCount?.toInt()?.plus(change) ?: 0
                }.thenCompose { newUsersCount ->
                    channelsRoot.document(channel)
                            .set(Pair("users_count", newUsersCount.toString()))
                            .update()
                            .thenApply { newUsersCount }
                }
    }


    /**
     * Updates a channel's online users count
     *
     * @return amount of online users in the channel after the update
     */
    private fun updateChannelOnlineUsersCountIfOnline(channel: String, username: String, change: Int = 1)
            : CompletableFuture<Int> {

        var effectiveChange = 0
        val isOnlineFuture = usersRoot.document(username)
                .read("token")
                .thenApply { token ->
                    if (token != null)
                        effectiveChange = change
                }

        return isOnlineFuture.thenCompose {
            channelsRoot.document(channel)
                    .read("online_users_count")
                    .thenApply { oldOnlineUsersCount ->
                        oldOnlineUsersCount?.toInt()?.plus(effectiveChange) ?: 0
                    }.thenCompose { newOnlineUsersCount ->
                        channelsRoot.document(channel)
                                .set(Pair("online_users_count", newOnlineUsersCount.toString()))
                                .update()
                                .thenApply { newOnlineUsersCount }
                    }
        }
    }

    /**
     * Removes a user from the list of operators for this channel, if the user is indeed an operator for the channel.
     *
     * @return amount of operators in the channel after removal
     */
    private fun removeOperatorFromChannel(channel: String, operatorToDelete: String)
            : CompletableFuture<Int> {
        return channelsRoot.document(channel)
                .readList("operators")
                .thenApply { operators ->
                    operators?.toMutableList() ?: mutableListOf()
                }.thenCompose { operators ->
                    if (!operators.contains(operatorToDelete))
                        CompletableFuture.completedFuture(operators.size)
                    else {
                        operators.remove(operatorToDelete)
                        channelsRoot.document(channel)
                                .set("operators", operators)
                                .update()
                                .thenApply { operators.size }
                    }
                }
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
     * Returns whether or not a given user is an operator of a given channel
     */
    private fun isOperator(username: String, channel: String): CompletableFuture<Boolean> {
        return channelsRoot.document(channel)
                .readList("operators")
                .thenApply { channelModerators ->
                    channelModerators?.contains(username) ?: false
                }
    }

    /**
     * Checks channels' names validity.
     * A channel's name is valid only if the following hold:
     *  - first letter is '#'
     *  - contains *only* a-z, A-Z, 0-9, '#' or '_' characters
     *
     * @param channel: name of the channel
     * @return true if the channel's name is valid, false otherwise
     */
    private fun validChannelName(channel: String): Boolean {
        if (channel[0] != '#') return false
        val validCharPool = ('a'..'z') + ('A'..'Z') + ('0'..'9') + '#' + '_'
        for (c in channel)
            if (!validCharPool.contains(c)) return false
        return true
    }
}