package il.ac.technion.cs.softwaredesign

import com.google.inject.Inject
import il.ac.technion.cs.softwaredesign.managers.database.AuthenticationManager
import il.ac.technion.cs.softwaredesign.managers.database.ChannelsManager
import il.ac.technion.cs.softwaredesign.managers.database.MessagesManager
import il.ac.technion.cs.softwaredesign.utils.DatabaseMapper
import java.util.concurrent.CompletableFuture

/**
 * Implementation of CourseApp querying functionality
 * @see CourseAppStatistics
 */
class CourseAppStatisticsImpl @Inject constructor(dbMapper: DatabaseMapper) : CourseAppStatistics {

    private val auth = AuthenticationManager(dbMapper)
    private val channelsManager = ChannelsManager(dbMapper)
    private val messagesManager = MessagesManager(dbMapper)

    override fun totalUsers(): CompletableFuture<Long> {
        return auth.getTotalUsers()
    }

    override fun loggedInUsers(): CompletableFuture<Long> {
        return auth.getLoggedInUsers()
    }

    override fun pendingMessages(): CompletableFuture<Long> {
        return messagesManager.pendingMessages()
    }

    override fun channelMessages(): CompletableFuture<Long> {
        return messagesManager.channelMessages()
    }

    override fun top10ChannelsByUsers(): CompletableFuture<List<String>> {
        return channelsManager.topKChannelsByUsers()
    }

    override fun top10ActiveChannelsByUsers(): CompletableFuture<List<String>> {
        return channelsManager.topKChannelsByActiveUsers()
    }

    override fun top10UsersByChannels(): CompletableFuture<List<String>> {
        return channelsManager.topKUsersByChannels()
    }

    override fun top10ChannelsByMessages(): CompletableFuture<List<String>> {
        return messagesManager.topKChannelsByMessages()
    }
}