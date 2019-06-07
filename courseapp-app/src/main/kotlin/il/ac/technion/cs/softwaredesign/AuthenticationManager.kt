package il.ac.technion.cs.softwaredesign

import il.ac.technion.cs.softwaredesign.database.Database
import il.ac.technion.cs.softwaredesign.database.DocumentReference
import il.ac.technion.cs.softwaredesign.exceptions.InvalidTokenException
import il.ac.technion.cs.softwaredesign.exceptions.NoSuchEntityException
import il.ac.technion.cs.softwaredesign.exceptions.UserAlreadyLoggedInException
import il.ac.technion.cs.softwaredesign.exceptions.UserNotAuthorizedException
import il.ac.technion.cs.softwaredesign.utils.DatabaseMapper
import java.time.LocalDateTime

/**
 * Manages users in a database: this class wraps authentication functionality.
 * Provides common database operations regarding users and login session tokens
 *
 * @see CourseApp
 * @see Database
 *
 * @param dbMapper: mapper object that contains the app's open databases
 *
 */
class AuthenticationManager(private val dbMapper: DatabaseMapper) {


    private val usersRoot = dbMapper.getDatabase("users")
            .collection("all_users")
    private val tokensRoot = dbMapper.getDatabase("users")
            .collection("tokens")
    private val channelsRoot = dbMapper.getDatabase("channels")
            .collection("all_channels")
    private val metadataRoot = dbMapper.getDatabase("users")
            .collection("metadata")

    fun performLogin(username: String, password: String): String {
        val userDocument = usersRoot.document(username)
        val storedPassword = userDocument.read("password")

        if (storedPassword != null && storedPassword != password)
            throw NoSuchEntityException("incorrect password")
        if (userDocument.read("token") != null)
            throw UserAlreadyLoggedInException()

        val token = generateToken(username)
        userDocument.set(Pair("token", token))

        updateLoginData(userDocument, storedPassword, password)

        tokensRoot.document(token)
                .set(Pair("username", username))
                .write()

        return token
    }

    fun performLogout(token: String) {
        val tokenDocument = tokensRoot.document(token)
        val username = tokenDocument.read("username")
                ?: throw InvalidTokenException("token does not match any active user")

        tokenDocument.delete()

        val userDocument = usersRoot.document(username)
        updateLogoutData(userDocument)
    }

    fun isUserLoggedIn(token: String, username: String): Boolean? {
        if (!tokensRoot.document(token)
                        .exists())
            throw InvalidTokenException("token does not match any active user")

        if (!usersRoot.document(username)
                        .exists())
            return null

        val otherToken = usersRoot.document(username)
                .read("token")
        return otherToken != null
    }


    fun makeAdministrator(token: String, username: String) {
        val tokenUsername = tokensRoot.document(token)
                .read("username")
                ?: throw InvalidTokenException("token does not match any active user")

        usersRoot.document(tokenUsername)

                .read("isAdmin") ?: throw UserNotAuthorizedException("no admin permission")

        if (!usersRoot.document(username)
                        .exists())
            throw NoSuchEntityException("given user does not exist")

        usersRoot.document(username)
                .set(Pair("isAdmin", "true"))
                .update()
    }

    fun getTotalUsers(): Long {
        return metadataRoot.document("users_data")
                .read("users_count")?.toLong() ?: 0
    }

    fun getLoggedInUsers(): Long {
        return metadataRoot.document("users_data")
                .read("online_users_count")?.toLong() ?: 0
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
                                enteredPassword: String) {
        if (storedPassword == null) {
            userDocument.set(Pair("password", enteredPassword))
                    .set(Pair("creation_time", LocalDateTime.now().toString()))

            val usersCountDocument = metadataRoot.document("users_data")
            val usersCount = usersCountDocument.read("users_count")?.toInt()?.plus(1)
                    ?: 1

            if (usersCount == 1) userDocument.set(Pair("isAdmin", "true"))

            usersCountDocument.set(Pair("users_count", usersCount.toString()))
                    .update()
        }
        val usersCountDocument = metadataRoot.document("users_data")
        val onlineUsersCount = usersCountDocument.read("online_users_count")?.toInt()?.plus(1)
                ?: 1
        usersCountDocument.set(Pair("online_users_count", onlineUsersCount.toString()))
                .update()

        val channels = userDocument.readList("channels")?.toMutableList()
                ?: mutableListOf()
        for (channel in channels) {
            val newOnlineUsersCount = channelsRoot.document(channel)
                    .read("online_users_count")?.toLong()?.plus(1) ?: 1
            channelsRoot.document(channel)
                    .set(Pair("online_users_count", newOnlineUsersCount.toString()))
                    .update()
        }

        userDocument.update()
    }

    /**
     * Updates the following information:
     *  - number of logged in users in the system,
     *  - number of users in the system,
     *  - number of logged in users in each channel that the user is a member of,
     *  - invalidating user token
     */
    private fun updateLogoutData(userDocument: DocumentReference) {
        val usersCountDocument = metadataRoot.document("users_data")
        val onlineUsersCount = usersCountDocument.read("online_users_count")?.toInt()?.minus(1)
                ?: 0
        usersCountDocument.set(Pair("online_users_count", onlineUsersCount.toString()))
                .update()

        val channels = userDocument.readList("channels")?.toMutableList()
                ?: mutableListOf()
        for (channel in channels) {
            val newOnlineUsersCount = channelsRoot.document(channel)
                    .read("online_users_count")?.toLong()?.minus(1) ?: 0
            channelsRoot.document(channel)
                    .set(Pair("online_users_count", newOnlineUsersCount.toString()))
                    .update()
        }

        userDocument.delete(listOf("token"))
    }
}