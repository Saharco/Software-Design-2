package il.ac.technion.cs.softwaredesign

import com.google.inject.Inject
import il.ac.technion.cs.softwaredesign.managers.database.AuthenticationManager
import il.ac.technion.cs.softwaredesign.managers.database.ChannelsManager
import il.ac.technion.cs.softwaredesign.messages.Message
import il.ac.technion.cs.softwaredesign.utils.DatabaseMapper
import java.util.concurrent.CompletableFuture

/**
 * Implementation of CourseApp functionality
 * @see CourseApp
 */
class CourseAppImpl @Inject constructor(dbMapper: DatabaseMapper) : CourseApp {
    private val auth = AuthenticationManager(dbMapper)
    private val channelsManager = ChannelsManager(dbMapper)

    override fun login(username: String, password: String): CompletableFuture<String> {
        return auth.performLogin(username, password)
    }

    override fun logout(token: String): CompletableFuture<Unit> {
        return auth.performLogout(token)
    }

    override fun isUserLoggedIn(token: String, username: String): CompletableFuture<Boolean?> {
        return auth.isUserLoggedIn(token, username)
    }

    override fun makeAdministrator(token: String, username: String): CompletableFuture<Unit> {
        return auth.makeAdministrator(token, username)
    }

    override fun channelJoin(token: String, channel: String): CompletableFuture<Unit> {
//        return channelsManager.channelJoin(token, channel)
        TODO()
    }

    override fun channelPart(token: String, channel: String): CompletableFuture<Unit> {
//        channelsManager.channelPart(token, channel)
        TODO()
    }

    override fun channelMakeOperator(token: String, channel: String, username: String): CompletableFuture<Unit> {
//        channelsManager.channelMakeOperator(token, channel, username)
        TODO()
    }

    override fun channelKick(token: String, channel: String, username: String): CompletableFuture<Unit> {
//        channelsManager.channelKick(token, channel, username)
        TODO()
    }

    override fun isUserInChannel(token: String, channel: String, username: String): CompletableFuture<Boolean?> {
//        return channelsManager.isUserInChannel(token, channel, username)
        TODO()
    }

    override fun numberOfActiveUsersInChannel(token: String, channel: String): CompletableFuture<Long> {
//        return channelsManager.numberOfActiveUsersInChannel(token, channel)
        TODO()
    }

    override fun numberOfTotalUsersInChannel(token: String, channel: String): CompletableFuture<Long> {
//        return channelsManager.numberOfTotalUsersInChannel(token, channel)
        TODO()
    }

    override fun addListener(token: String, callback: ListenerCallback): CompletableFuture<Unit> {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun removeListener(token: String, callback: ListenerCallback): CompletableFuture<Unit> {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun channelSend(token: String, channel: String, message: Message): CompletableFuture<Unit> {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun broadcast(token: String, message: Message): CompletableFuture<Unit> {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun privateSend(token: String, user: String, message: Message): CompletableFuture<Unit> {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun fetchMessage(token: String, id: Long): CompletableFuture<Pair<String, Message>> {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }
}