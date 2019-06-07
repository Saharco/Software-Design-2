import il.ac.technion.cs.softwaredesign.CourseApp
import il.ac.technion.cs.softwaredesign.CourseAppStatistics
import org.junit.jupiter.api.Assertions
import java.util.*

data class User(val username: String, val password: String,
                var token: String? = null,
                val channels: MutableList<String> = mutableListOf(),
                var isAdmin: Boolean = false)

data class Channel(val name: String, var totalUsersCount: Int = 0, var onlineUsersCount: Int = 0)

/**
 * Chooses a random amount min<=x<max such that random users will leave random channels x times in total
 */
fun CourseApp.leaveRandomChannels(users: ArrayList<User>,
                                  channels: ArrayList<Channel>, min: Int = 0, max: Int = 100) {
    var leaveAmount = kotlin.random.Random.nextInt(min, max)
    while (leaveAmount > 0) {
        val userIndex = kotlin.random.Random.nextInt(users.size)
        val channelIndex = kotlin.random.Random.nextInt(users[userIndex].channels.size)

        if (users[userIndex].token == null ||
                !users[userIndex].channels.contains(channels[channelIndex].name)) continue

        channelPart(users[userIndex].token!!, channels[channelIndex].name)

        users[userIndex].channels.remove(channels[channelIndex].name)
        channels[channelIndex].totalUsersCount--
        channels[channelIndex].onlineUsersCount--
        leaveAmount--
    }
}

/**
 * Chooses a random amount min<=x<max such that x logged out users will re-log
 * @param loggedOutUsers: list of indices of the logged out users. Re-logging users' indices will be removed from this list
 * @param users: list of app's users
 * @param channels: list app's channels
 */
fun CourseApp.performRandomRelog(loggedOutUsers: ArrayList<Int>, users: ArrayList<User>,
                                 channels: ArrayList<Channel>, min: Int = 0, max: Int = 40) {
    val relogAmount = kotlin.random.Random.nextInt(min, Math.min(max, loggedOutUsers.size))
    for (i in 0..relogAmount) {
        val loggedOutUserIndex = kotlin.random.Random.nextInt(loggedOutUsers.size)
        val user = users[loggedOutUserIndex]

        user.token = login(user.username, user.password)

        for (channel in channels)
            if (user.channels.contains(channel.name))
                channel.onlineUsersCount++

        loggedOutUsers.removeAt(loggedOutUserIndex)
    }
}

/**
 * Chooses a random amount min<=x<max such that x users will log out.
 * @return the indices of the users that logged out
 */
fun CourseApp.performRandomLogout(users: ArrayList<User>,
                                  channels: ArrayList<Channel>, min: Int = 0,
                                  max: Int = 60): ArrayList<Int> {
    val loggedOutUsersIndices = ArrayList<Int>()
    var logoutAmount = kotlin.random.Random.nextInt(min, max)
    while (logoutAmount > 0) {
        val chosenUserIndex = kotlin.random.Random.nextInt(0, users.size)
        if (users[chosenUserIndex].token == null) continue

        logout(users[chosenUserIndex].token!!)

        users[chosenUserIndex].token = null
        for (channel in channels)
            if (users[chosenUserIndex].channels.contains(channel.name))
                channel.onlineUsersCount--

        loggedOutUsersIndices.add(chosenUserIndex)
        logoutAmount--
    }
    return loggedOutUsersIndices
}

/**
 * Construct a maximum heap from a given collection of items
 * @param collection: collection of items
 * @param comparator: compares two items in the collection
 * @param removePredicate: predicate such that once an i-th maximum does not fulfil it:
 *  rest of the items will not be inserted to the heap
 * @param limit: maximum amount of items in the new heap
 * @return the constructed maximum heap, with a size upto [limit]
 */
fun <T> createMaxHeap(collection: Collection<T>, comparator: Comparator<T>,
                      removePredicate: (T) -> Boolean = { false },
                      limit: Int = 10): PriorityQueue<T> {
    val bigHeap = PriorityQueue<T>(comparator)
    bigHeap.addAll(collection)
    val heap = PriorityQueue<T>(comparator)
    var i = limit
    while (i > 0 && bigHeap.isNotEmpty()) {
        val element = bigHeap.poll()
        if (removePredicate(element))
            break
        heap.add(element)
        i--
    }
    return heap
}

/**
 * Chooses a random amount min<=x<max such that random users will join random channels x times in total
 */
fun CourseApp.joinRandomChannels(users: ArrayList<User>,
                                 channels: ArrayList<Channel>, min: Int = 100, max: Int = 500) {
    var joinCount = kotlin.random.Random.nextInt(min, max)
    while (joinCount > 0) {
        val chosenUserIndex = kotlin.random.Random.nextInt(0, users.size)
        val chosenChannelIndex = kotlin.random.Random.nextInt(0, channels.size)
        val chosenChannelName = channels[chosenChannelIndex].name
        if (users[chosenUserIndex].channels.contains(chosenChannelName) ||
                users[chosenUserIndex].token == null) continue

        channelJoin(users[chosenUserIndex].token!!, chosenChannelName)

        channels[chosenChannelIndex].totalUsersCount++
        channels[chosenChannelIndex].onlineUsersCount++
        users[chosenUserIndex].channels.add(chosenChannelName)
        joinCount--
    }
}

/**
 * Create channels with random names
 * @param admin: admin user in the app
 * @param channelsAmount: amount of channels to be created
 * @return list of all created channels
 */
fun CourseApp.createRandomChannels(admin: User, channelsAmount: Int = 50):
        ArrayList<Channel> {
    val adminToken = admin.token!!
    val channels = ArrayList<Channel>()
    for (i in 0..channelsAmount) {
        val name = UUID.randomUUID().toString()
        channelJoin(adminToken, name)
        channels[i] = Channel(name)
    }
    return channels
}

/**
 * Creates & logs in users with a random username+password. All created users' passwords are equal to their username
 * @param usersAmount: amount of users to be created & signed up
 * @return list of all created users
 */
fun CourseApp.performRandomUsersLogin(usersAmount: Int = 100): ArrayList<User> {
    val users = ArrayList<User>()
    for (i in 0..usersAmount) {
        val name = UUID.randomUUID().toString() // this is both the username & the password
        users[i] = User(name, name, login(name, name))
    }
    return users
}

/**
 * Verifies the correctness of [CourseAppStatistics] top10 methods
 * @param statistics: course app statistics instance
 * @param users: the users of the app
 * @param channels: the channels of the app
 */
fun verifyQueriesCorrectness(statistics: CourseAppStatistics, users: ArrayList<User>,
                             channels: ArrayList<Channel>) {

    val compareChannelsByUsers = Comparator<Channel> { ch1, ch2 ->
        ch1.totalUsersCount - ch2.totalUsersCount
    }
    val compareChannelsByOnlineUsers = Comparator<Channel> { ch1, ch2 ->
        ch1.onlineUsersCount - ch2.onlineUsersCount
    }
    val compareUsersByChannels = Comparator<User> { user1, user2 ->
        user1.channels.size - user2.channels.size
    }

    val channelByUsersPredicate: (Channel) -> Boolean = { it.totalUsersCount > 0 }
    val channelByOnlineUsersPredicate: (Channel) -> Boolean = { it.onlineUsersCount > 0 }

    val expectedTop10ChannelsByUsers = createMaxHeap(channels, compareChannelsByUsers,
            channelByUsersPredicate)
    val expectedTop10ChannelsByActiveUsers = createMaxHeap(channels, compareChannelsByOnlineUsers,
            channelByOnlineUsersPredicate)
    val expectedTop10UsersByChannels = createMaxHeap(users, compareUsersByChannels)

    Assertions.assertEquals(expectedTop10ChannelsByUsers, statistics.top10ChannelsByUsers())
    Assertions.assertEquals(expectedTop10ChannelsByActiveUsers, statistics.top10ActiveChannelsByUsers())
    Assertions.assertEquals(expectedTop10UsersByChannels, statistics.top10UsersByChannels())
}