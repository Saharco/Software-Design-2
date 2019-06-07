package il.ac.technion.cs.softwaredesign

import com.google.inject.Inject
import il.ac.technion.cs.softwaredesign.managers.database.AuthenticationManager
import il.ac.technion.cs.softwaredesign.managers.database.ChannelsManager
import il.ac.technion.cs.softwaredesign.utils.DatabaseMapper

/**
 * Implementation of CourseApp functionality
 * @see CourseApp
 */
class CourseAppImpl @Inject constructor(dbMapper: DatabaseMapper) : CourseApp {

    private val auth = AuthenticationManager(dbMapper)
    private val channelsManager = ChannelsManager(dbMapper)


    override fun login(username: String, password: String): String {
        return auth.performLogin(username, password)
    }

    override fun logout(token: String) {
        auth.performLogout(token)
    }

    override fun isUserLoggedIn(token: String, username: String): Boolean? {
        return auth.isUserLoggedIn(token, username)
    }

    override fun makeAdministrator(token: String, username: String) {
        auth.makeAdministrator(token, username)
    }

    override fun channelJoin(token: String, channel: String) {
        channelsManager.channelJoin(token, channel)
    }

    override fun channelPart(token: String, channel: String) {
        channelsManager.channelPart(token, channel)
    }

    override fun channelMakeOperator(token: String, channel: String, username: String) {
        channelsManager.channelMakeOperator(token, channel, username)
    }

    override fun channelKick(token: String, channel: String, username: String) {
        channelsManager.channelKick(token, channel, username)
    }

    override fun isUserInChannel(token: String, channel: String, username: String): Boolean? {
        return channelsManager.isUserInChannel(token, channel, username)
    }

    override fun numberOfActiveUsersInChannel(token: String, channel: String): Long {
        return channelsManager.numberOfActiveUsersInChannel(token, channel)
    }

    override fun numberOfTotalUsersInChannel(token: String, channel: String): Long {
        return channelsManager.numberOfTotalUsersInChannel(token, channel)
    }
}