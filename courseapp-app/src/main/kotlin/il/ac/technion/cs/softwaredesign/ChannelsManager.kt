package il.ac.technion.cs.softwaredesign

import il.ac.technion.cs.softwaredesign.database.Database
import il.ac.technion.cs.softwaredesign.exceptions.InvalidTokenException
import il.ac.technion.cs.softwaredesign.exceptions.NameFormatException
import il.ac.technion.cs.softwaredesign.exceptions.NoSuchEntityException
import il.ac.technion.cs.softwaredesign.exceptions.UserNotAuthorizedException
import il.ac.technion.cs.softwaredesign.utils.DatabaseMapper
import treeTopK
import updateTree
import java.time.LocalDateTime


/**
 * Manages channels in the app: this class wraps channels functionality
 *
 * @see CourseApp
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

    private val channelsByUsers = dbMapper.getStorage("channels_by_users")
    private val channelsByActiveUsers = dbMapper.getStorage("channels_by_active_users")
    private val usersByChannels = dbMapper.getStorage("users_by_channels")

    fun channelJoin(token: String, channel: String) {
        val tokenUsername = tokenToUser(token)
        if (!validChannelName(channel)) throw NameFormatException("invalid channel name")

        val userChannels = usersRoot.document(tokenUsername)
                .readList("channels")?.toMutableList() ?: mutableListOf()

        // finish if user attempts to join a channel they're already members of
        if (userChannels.contains(channel)) return

        var newChannelFlag = false
        if (!channelsRoot.document(channel).exists()) {
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
                    .read("users_count")?.toInt()?.plus(1) ?: 1
            channelsRoot.document(channel)
                    .set(Pair("users_count", usersCount.toString()))
                    .update()

            onlineUsersCount = channelsRoot.document(channel)
                    .read("online_users_count")?.toInt()?.plus(1) ?: 1
            channelsRoot.document(channel)
                    .set(Pair("online_users_count", onlineUsersCount.toString()))
                    .update()
        }

        val channelCreationTime = channelsRoot.document(channel)
                .read("creation_time")!!
        val userCreationTime = usersRoot.document(tokenUsername)
                .read("creation_time")!!
        val usersChannelsCount = userChannels.size

        updateTree(channelsByUsers, channel, usersCount, usersCount - 1,
                channelCreationTime)
        updateTree(channelsByActiveUsers, channel, onlineUsersCount, onlineUsersCount - 1,
                channelCreationTime)
        updateTree(usersByChannels, tokenUsername, usersChannelsCount,
                usersChannelsCount - 1, userCreationTime)
    }

    fun channelPart(token: String, channel: String) {
        val tokenUsername = tokenToUser(token)
        verifyChannelExists(channel)

        if (!isMemberOfChannel(tokenUsername, channel))
            throw NoSuchEntityException("user is not a member of the channel")

        expelChannelMember(tokenUsername, channel)
    }

    fun channelMakeOperator(token: String, channel: String, username: String) {
        val tokenUsername = tokenToUser(token)
        verifyChannelExists(channel)

        val isUserAdministrator = isAdmin(tokenUsername)
        val isUserOperator = isOperator(tokenUsername, channel)

        if (!isUserAdministrator && !isUserOperator)
            throw UserNotAuthorizedException("user is not an operator / administrator")

        if (isUserAdministrator && !isUserOperator && tokenUsername != username)
            throw UserNotAuthorizedException("administrator who's not an operator cannot appoint" +
                    "other users to be operators")

        if (!isMemberOfChannel(tokenUsername, channel))
            throw UserNotAuthorizedException("user is not a member in the channel")

        val otherUserExists = usersRoot.document(username)
                .exists()

        if (!otherUserExists || !isMemberOfChannel(username, channel))
            throw NoSuchEntityException("given username is not a member in the channel")

        // all requirements are filled: appoint user to channel operator

        val operators = channelsRoot.document(channel)
                .readList("operators")?.toMutableList() ?: mutableListOf()
        operators.add(username)

        channelsRoot.document(channel)
                .set("operators", operators)
                .update()
    }

    fun channelKick(token: String, channel: String, username: String) {
        val tokenUsername = tokenToUser(token)
        verifyChannelExists(channel)

        if (!isOperator(tokenUsername, channel))
            throw UserNotAuthorizedException("must have operator privileges")

        if (!isMemberOfChannel(username, channel))
            throw NoSuchEntityException("provided username is not a member of this channel")

        expelChannelMember(username, channel)
    }

    fun isUserInChannel(token: String, channel: String, username: String): Boolean? {
        verifyValidAndPrivilegedToQuery(token, channel)

        if (!usersRoot.document(username)
                        .exists())
            return null
        return isMemberOfChannel(username, channel)
    }

    fun numberOfActiveUsersInChannel(token: String, channel: String): Long {
        verifyValidAndPrivilegedToQuery(token, channel)

        return channelsRoot.document(channel)
                .read("online_users_count")?.toLong() ?: 0

    }

    fun numberOfTotalUsersInChannel(token: String, channel: String): Long {
        verifyValidAndPrivilegedToQuery(token, channel)

        return channelsRoot.document(channel)
                .read("users_count")!!.toLong()
    }

    fun topKChannelsByUsers(k: Int = 10): List<String> {
        return treeTopK(channelsByUsers, k)
    }


    fun topKChannelsByActiveUsers(k: Int = 10): List<String> {
        return treeTopK(channelsByActiveUsers, k)
    }


    fun topKUsersByChannels(k: Int = 10): List<String> {
        return treeTopK(usersByChannels, k)
    }

    /**
     * Creates a new channel with a given operator for the channel
     */
    private fun createNewChannel(channel: String, operatorUsername: String) {
        channelsRoot.document(channel)
                .set("operators", listOf(operatorUsername))
                .set(Pair("users_count", "1"))
                .set(Pair("online_users_count", "1"))
                .set(Pair("creation_time", LocalDateTime.now().toString()))
                .write()
    }

    /**
     * Verifies the token & channel for *querying operations*
     *
     * @throws InvalidTokenException If the auth [token] is invalid.
     * @throws NoSuchEntityException If [channel] does not exist.
     * @throws UserNotAuthorizedException If [token] identifies a user who is not an administrator and is not a member
     */
    private fun verifyValidAndPrivilegedToQuery(token: String, channel: String) {
        val tokenUsername = tokenToUser(token)
        verifyChannelExists(channel)
        if (!isAdmin(tokenUsername) && !isMemberOfChannel(tokenUsername, channel))
            throw UserNotAuthorizedException("must be an admin or a member of the channel")
    }

    private fun expelChannelMember(username: String, channel: String) {
        val userChannels = usersRoot.document(username)
                .readList("channels")?.toMutableList() ?: mutableListOf()
        userChannels.remove(channel)
        usersRoot.document(username)
                .set("channels", userChannels)
                .set(Pair("channels_count", userChannels.size.toString()))
                .update()

        val operators = channelsRoot.document(channel)
                .readList("operators")?.toMutableList() ?: mutableListOf()

        if (operators.contains(username)) {
            operators.remove(username)
            channelsRoot.document(channel)
                    .set("operators", operators)
                    .update()
        }

        val usersCount = channelsRoot.document(channel)
                .read("users_count")!!.toInt() - 1
        var onlineUsersCount = 0
        var channelDeleted = false
        if (usersCount == 0) {
            // the last user has left the channel: delete the channel
            channelsRoot.document(channel)
                    .delete()
            channelDeleted = true
        }

        var isUserLoggedIn = false
        if (!channelDeleted) {
            if (usersRoot.document(username)
                            .read("token") != null) {
                isUserLoggedIn = true
                onlineUsersCount = channelsRoot.document(channel)
                        .read("online_users_count")?.toInt()?.minus(1) ?: 0

                channelsRoot.document(channel)
                        .set(Pair("online_users_count", onlineUsersCount.toString()))
                        .update()
            }

            channelsRoot.document(channel)
                    .set(Pair("users_count", usersCount.toString()))
                    .update()
        }

        val channelCreationTime = channelsRoot.document(channel)
                .read("creation_time") ?: "deleted channel anyway"
        val userCreationTime = usersRoot.document(username)
                .read("creation_time")!!
        val usersChannelsCount = userChannels.size

        val prevOnlineUsersCount = if (isUserLoggedIn) onlineUsersCount + 1 else onlineUsersCount
        updateTree(channelsByUsers, channel, usersCount, usersCount + 1,
                channelCreationTime, usersCount <= 0)
        updateTree(channelsByActiveUsers, channel, onlineUsersCount, prevOnlineUsersCount,
                channelCreationTime,
                onlineUsersCount <= 0)
        updateTree(usersByChannels, username, usersChannelsCount,
                usersChannelsCount + 1, userCreationTime)
    }

    /**
     * Returns whether a given user is a member of a given channel or not
     */
    private fun isMemberOfChannel(username: String, channel: String): Boolean {
        val channelsList = usersRoot.document(username)
                .readList("channels")
        return channelsList != null && channelsList.contains(channel)
    }

    /**
     * Makes sure that a given channel exists
     *
     * @throws NoSuchEntityException if the channel does not exist
     */
    private fun verifyChannelExists(channel: String) {
        if (!channelsRoot.document(channel)
                        .exists())
            throw NoSuchEntityException("given channel does not exist")
    }

    /**
     * Translates a token to its corresponding user
     *
     * @throws InvalidTokenException if the token does not belong to any user
     */
    private fun tokenToUser(token: String): String {
        return tokensRoot.document(token)
                .read("username")
                ?: throw InvalidTokenException("token does not match any active user")
    }

    /**
     * Returns whether or not a given user is an administrator
     */
    private fun isAdmin(username: String): Boolean {
        return usersRoot.document(username)
                .read("isAdmin")
                .equals("true")
    }

    /**
     * Returns whether or not a given user is an operator of a given channel
     */
    private fun isOperator(username: String, channel: String): Boolean {
        val channelModerators = channelsRoot.document(channel)
                .readList("operators") ?: return false
        return channelModerators.contains(username)
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