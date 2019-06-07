package il.ac.technion.cs.softwaredesign

import com.google.inject.Inject
import il.ac.technion.cs.softwaredesign.utils.DatabaseMapper

/**
 * Implementation of CourseApp querying functionality
 * @see CourseAppStatistics
 */
class CourseAppStatisticsImpl @Inject constructor(dbMapper: DatabaseMapper) : CourseAppStatistics {

    private val auth = AuthenticationManager(dbMapper)
    private val channelsManager = ChannelsManager(dbMapper)

    override fun totalUsers(): Long {
        return auth.getTotalUsers()
    }

    override fun loggedInUsers(): Long {
        return auth.getLoggedInUsers()
    }

    override fun top10ChannelsByUsers(): List<String> {
        return channelsManager.topKChannelsByUsers()
    }

    override fun top10ActiveChannelsByUsers(): List<String> {
        return channelsManager.topKChannelsByActiveUsers()
    }

    override fun top10UsersByChannels(): List<String> {
        return channelsManager.topKUsersByChannels()
    }
}