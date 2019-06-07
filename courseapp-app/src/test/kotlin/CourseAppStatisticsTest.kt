import com.authzee.kotlinguice4.getInstance
import com.google.inject.Guice
import il.ac.technion.cs.softwaredesign.CourseApp
import il.ac.technion.cs.softwaredesign.CourseAppInitializer
import il.ac.technion.cs.softwaredesign.CourseAppModule
import il.ac.technion.cs.softwaredesign.CourseAppStatistics
import il.ac.technion.cs.softwaredesign.storage.SecureStorageModule
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import java.util.*

class CourseAppStatisticsTest {
    private val injector = Guice.createInjector(CourseAppModule(), SecureStorageModule())
    private val courseAppInitializer = injector.getInstance<CourseAppInitializer>()

    init {
        courseAppInitializer.setup()
    }

    private val app = injector.getInstance<CourseApp>()
    private val statistics = injector.getInstance<CourseAppStatistics>()

    @Test
    internal fun `can query total users when no users exist yet`() {
        assertEquals(0, statistics.totalUsers())
    }

    @Test
    internal fun `can query logged in users when no users are logged in`() {
        assertEquals(0, statistics.loggedInUsers())
    }

    @Test
    internal fun `adding distinct users to the system properly increases total number of users`() {
        val token = app.login("sahar", "a very strong password")
        assertEquals(1, statistics.totalUsers())

        app.login("yuval", "a weak password")
        assertEquals(2, statistics.totalUsers())

        app.logout(token)

        assertEquals(2, statistics.totalUsers())
    }

    @Test
    internal fun `logging in & out changes the amount of logged in users accordingly`() {
        val token1 = app.login("sahar", "a very strong password")
        val token2 = app.login("yuval", "a weak password")

        assertEquals(2, statistics.loggedInUsers())

        app.logout(token1)
        assertEquals(1, statistics.loggedInUsers())

        app.logout(token2)
        assertEquals(0, statistics.loggedInUsers())

        app.login("sahar", "a very strong password")
        assertEquals(1, statistics.loggedInUsers())
    }

    @Test
    internal fun `querying top 10 when there are no channels should return an empty list`() {
        val list1 = statistics.top10ChannelsByUsers()
        val list2 = statistics.top10ActiveChannelsByUsers()
        val list3 = statistics.top10UsersByChannels()

        assertEquals(0, list1.size)
        assertEquals(0, list2.size)
        assertEquals(0, list3.size)
    }

    @Test
    internal fun `top 10 channel list does secondary sorting by creation order`() {
        val adminToken = app.login("admin", "admin")
        val nonAdminToken = app.login("matan", "4321")
        app.makeAdministrator(adminToken, "matan")

        app.channelJoin(adminToken, "#test")
        app.channelJoin(nonAdminToken, "#other")

        assertEquals(listOf("#test", "#other"), statistics.top10ChannelsByUsers())
    }

    @Test
    internal fun `top 10 channel list counts only logged in users`() {
        val adminToken = app.login("admin", "admin")
        val nonAdminToken = app.login("matan", "4321")
        app.makeAdministrator(adminToken, "matan")

        app.channelJoin(adminToken, "#test")
        app.channelJoin(nonAdminToken, "#other")
        app.logout(nonAdminToken)

        assertEquals(listOf("#test", "#other"), statistics.top10ActiveChannelsByUsers())
    }

    @Test
    internal fun `top 10 user list does secondary sorting by registration order`() {
        val adminToken = app.login("admin", "admin")
        val nonAdminToken = app.login("matan", "4321")
        app.makeAdministrator(adminToken, "matan")
        app.channelJoin(adminToken, "#test")
        app.channelJoin(nonAdminToken, "#other")

        assertEquals(listOf("admin", "matam"), statistics.top10UsersByChannels())
    }

    /**
     * Tests correctness of the following functions via load testing:
     * - [CourseAppStatistics.top10ChannelsByUsers]
     * - [CourseAppStatistics.top10ActiveChannelsByUsers]
     * - [CourseAppStatistics.top10UsersByChannels]
     * Assertions are made with [verifyQueriesCorrectness]
     */
    @Test
    internal fun `load test - top 10 queries return the expected results`() {
        // create a lot of users
        val users = app.performRandomUsersLogin()
        val admin = users[0]

        // create a lot of channels
        val channels = app.createRandomChannels(admin)

        // make some users join some channels & check correctness
        app.joinRandomChannels(users, channels)
        verifyQueriesCorrectness(statistics, users, channels)

        // log out with some portion of users & check correctness
        val loggedOutUsersIndices = app.performRandomLogout(users, channels)
        verifyQueriesCorrectness(statistics, users, channels)

        // log back in with some users & check correctness
        app.performRandomRelog(loggedOutUsersIndices, users, channels)
        verifyQueriesCorrectness(statistics, users, channels)

        // leave channels with some portion of users & check correctness
        app.leaveRandomChannels(users, channels)
        verifyQueriesCorrectness(statistics, users, channels)
    }

    @Test
    internal fun `The database calculates top10ChannelsByUsers correctly`() {
        val tokenList = LinkedList<String>()
        val userList = LinkedList<String>()
        val channelList = LinkedList<String>()
        val userCount = 50
        val channelCount = 11
        val token = app.login("admin", "admin")
        tokenList.add(token)
        userList.add("admin")
        for (i in 1 until userCount + 1) {
            userList.add("user$i")
            tokenList.add(app.login("user$i", "user$i"))
        }

        for (i in 1 until channelCount + 1) {
            channelList.add("#channel_$i")
            app.channelJoin(token, "#channel_$i")
        }
        println("-------------------------------")
        for (i in 1 until userCount + 1) {
            for (j in 1 until (2..(channelCount + 1)).shuffled().first()) {
                val random = (0 until channelCount).shuffled().first()
                app.channelJoin(tokenList[i], channelList[random])
            }
        }
        val memberCount = LinkedList<Pair<String, Long>>()
        for (i in 1 until channelCount + 1) {
            memberCount.add(Pair(channelList[i - 1], app.numberOfTotalUsersInChannel(token, channelList[i - 1])))
        }
        val list = memberCount.asSequence().sortedByDescending { it.second }.map { it.first }.toList().reversed().takeLast(10).reversed()
        assertEquals(list, statistics.top10ChannelsByUsers())
    }
}