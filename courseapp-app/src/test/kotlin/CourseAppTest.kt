import com.authzee.kotlinguice4.getInstance
import com.google.inject.Guice
import il.ac.technion.cs.softwaredesign.CourseApp
import il.ac.technion.cs.softwaredesign.CourseAppInitializer
import il.ac.technion.cs.softwaredesign.CourseAppModule
import il.ac.technion.cs.softwaredesign.exceptions.*
import il.ac.technion.cs.softwaredesign.storage.SecureStorageModule
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class CourseAppTest {

    private val injector = Guice.createInjector(CourseAppModule(), SecureStorageModule())
    private val courseAppInitializer = injector.getInstance<CourseAppInitializer>()

    init {
        courseAppInitializer.setup()
    }

    private val app = injector.getInstance<CourseApp>()

    @Test
    internal fun `user successfully logged in after login`() {
        val token = app.login("sahar", "a very strong password")

        assertEquals(app.isUserLoggedIn(token, "sahar"), true)
    }

    @Test
    internal fun `attempting login twice without logout should throw UserAlreadyLoggedInException`() {
        app.login("sahar", "a very strong password")

        assertThrows<UserAlreadyLoggedInException> {
            app.login("sahar", "a very strong password")
        }
    }

    @Test
    internal fun `creating two users with same username should throw NoSuchEntityException`() {
        app.login("sahar", "a very strong password")
        assertThrows<NoSuchEntityException> {
            app.login("sahar", "weak password")
        }
    }

    @Test
    internal fun `using token to check login session after self's login session expires should throw InvalidTokenException`() {
        val token = app.login("sahar", "a very strong password")
        app.login("yuval", "popcorn")
        app.logout(token)
        assertThrows<InvalidTokenException> {
            app.isUserLoggedIn(token, "yuval")
        }
    }

    @Test
    internal fun `logging out with an invalid token should throw InvalidTokenException`() {
        var token = "invalid token"
        assertThrows<InvalidTokenException> {
            app.logout(token)
        }

        token = app.login("sahar", "a very strong password")
        app.logout(token)

        assertThrows<InvalidTokenException> {
            app.logout(token)
        }
    }

    @Test
    internal fun `two different users should have different tokens`() {
        val token1 = app.login("sahar", "a very strong password")
        val token2 = app.login("yuval", "popcorn")
        assertTrue(token1 != token2)
    }

    @Test
    internal fun `checking if user is logged in when they are not should return false`() {
        val token = app.login("sahar", "a very strong password")
        val otherToken = app.login("yuval", "popcorn")
        app.logout(otherToken)
        assertEquals(app.isUserLoggedIn(token, "yuval"), false)
    }

    @Test
    internal fun `checking if user is logged in when they dont exist should return null`() {
        val token = app.login("sahar", "a very strong password")
        assertNull(app.isUserLoggedIn(token, "yuval"))
    }

    @Test
    internal fun `system can hold lots of distinct users and tokens`() {
        val strings = ArrayList<String>()
        populateWithRandomStrings(strings)
        val users = strings.distinct()
        val systemSize = users.size
        val tokens = HashSet<String>()

        for (i in 0 until systemSize) {
            // Dont care about exact values here: username & password are the same for each user
            val token = app.login(users[i], users[i])
            tokens.add(token)
        }

        assertEquals(tokens.size, users.size)

        for (token in tokens) {
            app.logout(token)
        }
    }

    @Test
    internal fun `first logged in user is an administrator by default`() {
        val token = app.login("sahar", "a very strong password")
        app.login("yuval", "weak password")

        app.makeAdministrator(token, "yuval")
    }

    @Test
    internal fun `administrator can appoint other users to be administrators`() {
        val token1 = app.login("sahar", "a very strong password")
        val token2 = app.login("yuval", "weak password")
        app.login("victor", "big")

        app.makeAdministrator(token1, "yuval")
        app.makeAdministrator(token2, "victor")
    }

    @Test
    internal fun `trying to appoint others to be administrators without a valid token should throw InvalidTokenException`() {
        app.login("sahar", "a very strong password")
        app.login("yuval", "weak password")

        assertThrows<InvalidTokenException> {
            app.makeAdministrator("badToken", "yuval")
        }
    }

    @Test
    internal fun `trying to appoint others to be administrators without authorization should throw UserNotAuthorizedException`() {
        app.login("sahar", "a very strong password")
        val nonAdminToken = app.login("yuval", "weak password")
        app.login("victor", "big")

        assertThrows<UserNotAuthorizedException> {
            app.makeAdministrator(nonAdminToken, "victor")
        }
    }

    @Test
    internal fun `trying to appoint a non-existing user to be an administrator should NoSuchEntityException`() {
        val adminToken = app.login("sahar", "a very strong password")

        assertThrows<NoSuchEntityException> {
            app.makeAdministrator(adminToken, "yuval")
        }
    }

    @Test
    internal fun `joining a channel with an invalid name should throw NameFormatException`() {
        val adminToken = app.login("sahar", "a very strong password")

        assertThrows<NameFormatException> {
            app.channelJoin(adminToken, "badName")
        }

        assertThrows<NameFormatException> {
            app.channelJoin(adminToken, "#ba\$dName")
        }

        assertThrows<NameFormatException> {
            app.channelJoin(adminToken, "#bad Name")
        }
    }

    @Test
    internal fun `creating a new channel without administrator authorization should throw UserNotAuthorizedException`() {
        app.login("sahar", "a very strong password")
        val notAdminToken = app.login("yuval", "weak password")

        assertThrows<UserNotAuthorizedException> {
            app.channelJoin(notAdminToken, "#TakeCare")
        }
    }

    @Test
    internal fun `administrator can successfully create new channels`() {
        val adminToken = app.login("sahar", "a very strong password")
        app.channelJoin(adminToken, "#TakeCare")
    }

    @Test
    internal fun `users can successfully join an existing channel`() {
        val adminToken = app.login("sahar", "a very strong password")
        val notAdminToken = app.login("yuval", "weak password")
        app.channelJoin(adminToken, "#TakeCare")
        app.channelJoin(notAdminToken, "#TakeCare")
    }

    @Test
    internal fun `attempting to leave a channel a user is not a member of should throw NoSuchEntityException`() {
        val adminToken = app.login("sahar", "a very strong password")
        val notAdminToken = app.login("yuval", "weak password")
        app.channelJoin(adminToken, "#TakeCare")
        assertThrows<NoSuchEntityException> {
            app.channelPart(notAdminToken, "#TakeCare")
        }
    }

    @Test
    internal fun `attempting to leave a channel that does not exist should throw NoSuchEntityException`() {
        val token = app.login("sahar", "a very strong password")
        assertThrows<NoSuchEntityException> {
            app.channelPart(token, "#TakeCare")
        }
    }

    @Test
    internal fun `channel is destroyed when last user leaves it`() {
        val adminToken = app.login("sahar", "a very strong password")
        val notAdminToken = app.login("yuval", "weak password")
        app.channelJoin(adminToken, "#TakeCare")
        app.channelPart(adminToken, "#TakeCare")

        assertThrows<UserNotAuthorizedException> {
            app.channelJoin(notAdminToken, "#TakeCare")
        }
    }

    @Test
    internal fun `joining a channel more than once has no effect`() {
        val adminToken = app.login("sahar", "a very strong password")
        for (i in 1..5) {
            app.channelJoin(adminToken, "#TakeCare")
        }
        app.channelPart(adminToken, "#TakeCare")

        assertThrows<NoSuchEntityException> {
            app.channelPart(adminToken, "#TakeCare")
        }
    }

    @Test
    internal fun `trying to appoint an operator as user who's neither operator nor administrator should throw UserNotAuthorizedException`() {
        val adminToken = app.login("sahar", "a very strong password")
        val notAdminToken = app.login("yuval", "a weak password")
        app.login("victor", "big")
        app.channelJoin(adminToken, "#TakeCare")

        assertThrows<UserNotAuthorizedException> {
            app.channelMakeOperator(notAdminToken, "#TakeCare", "victor")
        }
    }

    @Test
    internal fun `trying to appoint another user to an operator as an admin who's not an operator should throw UserNotAuthorizedException`() {
        val adminToken = app.login("sahar", "a very strong password")
        val otherToken = app.login("yuval", "a weak password")
        app.login("victor", "big")
        app.makeAdministrator(adminToken, "yuval")
        app.channelJoin(otherToken, "#TakeCare")

        assertThrows<UserNotAuthorizedException> {
            app.channelMakeOperator(adminToken, "#TakeCare", "victor")
        }
    }

    @Test
    internal fun `trying to appoint an operator as a user who's not a member of the channel should throw UserNotAuthorizedException`() {
        val adminToken = app.login("sahar", "a very strong password")
        val otherToken = app.login("yuval", "a weak password")
        app.makeAdministrator(adminToken, "yuval")
        app.channelJoin(otherToken, "#TakeCare")

        assertThrows<UserNotAuthorizedException> {
            app.channelMakeOperator(adminToken, "#TakeCare", "sahar")
        }
    }

    @Test
    internal fun `operator can appoint other channel members to be operators`() {
        val adminToken = app.login("sahar", "a very strong password")
        val otherToken = app.login("yuval", "a weak password")
        val lastToken = app.login("victor", "big")
        app.channelJoin(adminToken, "#TakeCare")
        app.channelJoin(otherToken, "#TakeCare")
        app.channelJoin(lastToken, "#TakeCare")

        assertThrows<UserNotAuthorizedException> {
            app.channelMakeOperator(otherToken, "#TakeCare", "victor")
        }
        app.channelMakeOperator(adminToken, "#TakeCare", "yuval")

        app.channelMakeOperator(otherToken, "#TakeCare", "victor")
    }

    @Test
    internal fun `attempting to kick a member from a channel without operator privileges should throw UserNotAuthorizedException`() {
        val adminToken = app.login("sahar", "a very strong password")
        val otherToken = app.login("yuval", "a weak password")
        app.makeAdministrator(adminToken, "yuval")

        app.channelJoin(otherToken, "#TakeCare")
        app.channelJoin(adminToken, "#TakeCare")

        assertThrows<UserNotAuthorizedException> {
            app.channelKick(adminToken, "#TakeCare", "yuval")
        }
    }

    @Test
    internal fun `attempting to kick from a channel a member that does not exist should throw NoSuchEntityException`() {
        val adminToken = app.login("sahar", "a very strong password")
        app.channelJoin(adminToken, "#TakeCare")

        assertThrows<NoSuchEntityException> {
            app.channelKick(adminToken, "#TakeCare", "yuval")
        }
    }

    @Test
    internal fun `attempting to kick from a channel a member who's not a member of the same channel should throw NoSuchEntityException`() {
        val adminToken = app.login("sahar", "a very strong password")
        val otherToken = app.login("yuval", "a weak password")
        app.makeAdministrator(adminToken, "yuval")

        app.channelJoin(adminToken, "#TakeCare")
        app.channelJoin(otherToken, "#TakeCare2")

        assertThrows<NoSuchEntityException> {
            app.channelKick(adminToken, "#TakeCare", "yuval")
        }
    }

    @Test
    internal fun `operator can kick members from a channel`() {
        val adminToken = app.login("sahar", "a very strong password")
        val otherToken = app.login("yuval", "a weak password")
        app.channelJoin(adminToken, "#TakeCare")
        app.channelJoin(otherToken, "#TakeCare")

        app.channelKick(adminToken, "#TakeCare", "yuval")

        app.isUserInChannel(adminToken, "#TakeCare", "yuval")?.let { assertFalse(it) }
    }

    @Test
    internal fun `attempting to kick a member from a channel with operator privileges for another channel should throw UserNotAuthorizedException`() {
        val adminToken = app.login("sahar", "a very strong password")
        val otherToken = app.login("yuval", "a weak password")
        app.makeAdministrator(adminToken, "yuval")

        app.channelJoin(adminToken, "#TakeCare")
        app.channelJoin(otherToken, "#TakeCare2")

        assertThrows<UserNotAuthorizedException> {
            app.channelKick(adminToken, "#TakeCare2", "yuval")
        }
    }

    @Test
    internal fun `member & admin can query the total number of members in the channel`() {
        val adminToken = app.login("sahar", "a very strong password")
        val token2 = app.login("yuval", "a weak password")
        val token3 = app.login("victor", "big")
        val channel = "#TakeCare"

        app.channelJoin(adminToken, channel)
        assertEquals(1, app.numberOfTotalUsersInChannel(adminToken, channel))

        app.channelJoin(token2, channel)
        assertEquals(2, app.numberOfTotalUsersInChannel(token2, channel))

        app.channelJoin(token3, channel)
        assertEquals(3, app.numberOfTotalUsersInChannel(token3, channel))

        app.channelPart(adminToken, channel)
        assertEquals(2, app.numberOfTotalUsersInChannel(adminToken, channel))

        app.channelPart(token3, channel)
        assertEquals(1, app.numberOfTotalUsersInChannel(adminToken, channel))
    }

    @Test
    internal fun `member & admin can query the number of active members in the channel`() {
        val adminToken = app.login("sahar", "a very strong password")
        val token2 = app.login("yuval", "a weak password")
        val channelOne = "#TakeCare"
        val channelTwo = "#TakeCare2"

        app.channelJoin(adminToken, channelOne)
        app.channelJoin(adminToken, channelTwo)

        app.channelJoin(token2, channelOne)
        app.channelJoin(token2, channelTwo)

        assertEquals(2, app.numberOfActiveUsersInChannel(adminToken, channelOne))
        assertEquals(2, app.numberOfActiveUsersInChannel(adminToken, channelTwo))

        app.channelPart(adminToken, channelOne)

        assertEquals(1, app.numberOfActiveUsersInChannel(adminToken, channelOne))
        assertEquals(2, app.numberOfActiveUsersInChannel(adminToken, channelTwo))

        app.logout(token2)

        assertEquals(0, app.numberOfActiveUsersInChannel(adminToken, channelOne))
        assertEquals(1, app.numberOfActiveUsersInChannel(adminToken, channelTwo))
    }
}