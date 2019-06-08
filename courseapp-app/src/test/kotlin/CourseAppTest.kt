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
        courseAppInitializer.setup().join()
    }

    private val app = injector.getInstance<CourseApp>()

    @Test
    internal fun `user successfully logged in after login`() {
        val token = app.login("sahar", "a very strong password").join()

        assertEquals(app.isUserLoggedIn(token, "sahar").join(), true)
    }

    @Test
    internal fun `attempting login twice without logout should throw UserAlreadyLoggedInException`() {
        app.login("sahar", "a very strong password").join()

        assertThrows<UserAlreadyLoggedInException> {
            app.login("sahar", "a very strong password").joinException()
        }
    }

    @Test
    internal fun `creating two users with same username should throw NoSuchEntityException`() {
        app.login("sahar", "a very strong password").join()
        assertThrows<NoSuchEntityException> {
            app.login("sahar", "weak password").joinException()
        }
    }

    @Test
    internal fun `using token to check login session after self's login session expires should throw InvalidTokenException`() {
        val token = app.login("sahar", "a very strong password").join()
        app.login("yuval", "popcorn").join()
        app.logout(token).join()
        assertThrows<InvalidTokenException> {
            app.isUserLoggedIn(token, "yuval").joinException()
        }
    }

    @Test
    internal fun `logging out with an invalid token should throw InvalidTokenException`() {
        var token = "invalid token"
        assertThrows<InvalidTokenException> {
            app.logout(token).joinException()
        }

        token = app.login("sahar", "a very strong password").join()
        app.logout(token).join()

        assertThrows<InvalidTokenException> {
            app.logout(token).joinException()
        }
    }

    @Test
    internal fun `two different users should have different tokens`() {
        val token1 = app.login("sahar", "a very strong password").join()
        val token2 = app.login("yuval", "popcorn").join()
        assertTrue(token1 != token2)
    }

    @Test
    internal fun `checking if user is logged in when they are not should return false`() {
        val token = app.login("sahar", "a very strong password").join()
        val otherToken = app.login("yuval", "popcorn").join()
        app.logout(otherToken).join()
        assertEquals(app.isUserLoggedIn(token, "yuval").join(), false)
    }

    @Test
    internal fun `checking if user is logged in when they dont exist should return null`() {
        val token = app.login("sahar", "a very strong password").join()
        assertNull(app.isUserLoggedIn(token, "yuval").join())
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
            val token = app.login(users[i], users[i]).join()
            tokens.add(token)
        }

        assertEquals(tokens.size, users.size)

        for (token in tokens) {
            app.logout(token).join()
        }
    }

    @Test
    internal fun `first logged in user is an administrator by default`() {
        val token = app.login("sahar", "a very strong password").join()
        app.login("yuval", "weak password").join()

        app.makeAdministrator(token, "yuval").join()
    }

    @Test
    internal fun `administrator can appoint other users to be administrators`() {
        val token1 = app.login("sahar", "a very strong password").join()
        val token2 = app.login("yuval", "weak password").join()
        app.login("victor", "big").join()

        app.makeAdministrator(token1, "yuval").join()
        app.makeAdministrator(token2, "victor").join()
    }

    @Test
    internal fun `trying to appoint others to be administrators without a valid token should throw InvalidTokenException`() {
        app.login("sahar", "a very strong password").join()
        app.login("yuval", "weak password").join()

        assertThrows<InvalidTokenException> {
            app.makeAdministrator("badToken", "yuval").joinException()
        }
    }

    @Test
    internal fun `trying to appoint others to be administrators without authorization should throw UserNotAuthorizedException`() {
        app.login("sahar", "a very strong password").join()
        val nonAdminToken = app.login("yuval", "weak password").join()
        app.login("victor", "big").join()

        assertThrows<UserNotAuthorizedException> {
            app.makeAdministrator(nonAdminToken, "victor").joinException()
        }
    }

    @Test
    internal fun `trying to appoint a non-existing user to be an administrator should NoSuchEntityException`() {
        val adminToken = app.login("sahar", "a very strong password").join()

        assertThrows<NoSuchEntityException> {
            app.makeAdministrator(adminToken, "yuval").joinException()
        }
    }

    @Test
    internal fun `joining a channel with an invalid name should throw NameFormatException`() {
        val adminToken = app.login("sahar", "a very strong password").join()

        assertThrows<NameFormatException> {
            app.channelJoin(adminToken, "badName").joinException()
        }

        assertThrows<NameFormatException> {
            app.channelJoin(adminToken, "#ba\$dName").joinException()
        }

        assertThrows<NameFormatException> {
            app.channelJoin(adminToken, "#bad Name").joinException()
        }
    }

    @Test
    internal fun `creating a new channel without administrator authorization should throw UserNotAuthorizedException`() {
        app.login("sahar", "a very strong password").join()
        val notAdminToken = app.login("yuval", "weak password").join()

        assertThrows<UserNotAuthorizedException> {
            app.channelJoin(notAdminToken, "#TakeCare").joinException()
        }
    }

    @Test
    internal fun `administrator can successfully create new channels`() {
        val adminToken = app.login("sahar", "a very strong password").join()
        app.channelJoin(adminToken, "#TakeCare").join()
    }

    @Test
    internal fun `users can successfully join an existing channel`() {
        val adminToken = app.login("sahar", "a very strong password").join()
        val notAdminToken = app.login("yuval", "weak password").join()
        app.channelJoin(adminToken, "#TakeCare").join()
        app.channelJoin(notAdminToken, "#TakeCare").join()
    }

    @Test
    internal fun `attempting to leave a channel a user is not a member of should throw NoSuchEntityException`() {
        val adminToken = app.login("sahar", "a very strong password").join()
        val notAdminToken = app.login("yuval", "weak password").join()
        app.channelJoin(adminToken, "#TakeCare").join()
        assertThrows<NoSuchEntityException> {
            app.channelPart(notAdminToken, "#TakeCare").joinException()
        }
    }

    @Test
    internal fun `attempting to leave a channel that does not exist should throw NoSuchEntityException`() {
        val token = app.login("sahar", "a very strong password").join()
        assertThrows<NoSuchEntityException> {
            app.channelPart(token, "#TakeCare").joinException()
        }
    }

    @Test
    internal fun `channel is destroyed when last user leaves it`() {
        val adminToken = app.login("sahar", "a very strong password").join()
        val notAdminToken = app.login("yuval", "weak password").join()
        app.channelJoin(adminToken, "#TakeCare").join()
        app.channelPart(adminToken, "#TakeCare").join()

        assertThrows<UserNotAuthorizedException> {
            app.channelJoin(notAdminToken, "#TakeCare").joinException()
        }
    }

    @Test
    internal fun `joining a channel more than once has no effect`() {
        val adminToken = app.login("sahar", "a very strong password").join()
        for (i in 1..5) {
            app.channelJoin(adminToken, "#TakeCare").join()
        }
        app.channelPart(adminToken, "#TakeCare").join()

        assertThrows<NoSuchEntityException> {
            app.channelPart(adminToken, "#TakeCare").joinException()
        }
    }

    @Test
    internal fun `trying to appoint an operator as user who's neither operator nor administrator should throw UserNotAuthorizedException`() {
        val adminToken = app.login("sahar", "a very strong password").join()
        val notAdminToken = app.login("yuval", "a weak password").join()
        app.login("victor", "big").join()
        app.channelJoin(adminToken, "#TakeCare").join()

        assertThrows<UserNotAuthorizedException> {
            app.channelMakeOperator(notAdminToken, "#TakeCare", "victor").joinException()
        }
    }

    @Test
    internal fun `trying to appoint another user to an operator as an admin who's not an operator should throw UserNotAuthorizedException`() {
        val adminToken = app.login("sahar", "a very strong password").join()
        val otherToken = app.login("yuval", "a weak password").join()
        app.login("victor", "big").join()
        app.makeAdministrator(adminToken, "yuval").join()
        app.channelJoin(otherToken, "#TakeCare").join()

        assertThrows<UserNotAuthorizedException> {
            app.channelMakeOperator(adminToken, "#TakeCare", "victor").joinException()
        }
    }

    @Test
    internal fun `trying to appoint an operator as a user who's not a member of the channel should throw UserNotAuthorizedException`() {
        val adminToken = app.login("sahar", "a very strong password").join()
        val otherToken = app.login("yuval", "a weak password").join()
        app.makeAdministrator(adminToken, "yuval").join()
        app.channelJoin(otherToken, "#TakeCare").join()

        assertThrows<UserNotAuthorizedException> {
            app.channelMakeOperator(adminToken, "#TakeCare", "sahar").joinException()
        }
    }

    @Test
    internal fun `operator can appoint other channel members to be operators`() {
        val adminToken = app.login("sahar", "a very strong password").join()
        val otherToken = app.login("yuval", "a weak password").join()
        val lastToken = app.login("victor", "big").join()
        app.channelJoin(adminToken, "#TakeCare").join()
        app.channelJoin(otherToken, "#TakeCare").join()
        app.channelJoin(lastToken, "#TakeCare").join()

        assertThrows<UserNotAuthorizedException> {
            app.channelMakeOperator(otherToken, "#TakeCare", "victor").joinException()
        }
        app.channelMakeOperator(adminToken, "#TakeCare", "yuval").join()

        app.channelMakeOperator(otherToken, "#TakeCare", "victor").join()
    }

    @Test
    internal fun `attempting to kick a member from a channel without operator privileges should throw UserNotAuthorizedException`() {
        val adminToken = app.login("sahar", "a very strong password").join()
        val otherToken = app.login("yuval", "a weak password").join()
        app.makeAdministrator(adminToken, "yuval").join()

        app.channelJoin(otherToken, "#TakeCare").join()
        app.channelJoin(adminToken, "#TakeCare").join()

        assertThrows<UserNotAuthorizedException> {
            app.channelKick(adminToken, "#TakeCare", "yuval").joinException()
        }
    }

    @Test
    internal fun `attempting to kick from a channel a member that does not exist should throw NoSuchEntityException`() {
        val adminToken = app.login("sahar", "a very strong password").join()
        app.channelJoin(adminToken, "#TakeCare").join()

        assertThrows<NoSuchEntityException> {
            app.channelKick(adminToken, "#TakeCare", "yuval").joinException()
        }
    }

    @Test
    internal fun `attempting to kick from a channel a member who's not a member of the same channel should throw NoSuchEntityException`() {
        val adminToken = app.login("sahar", "a very strong password").join()
        val otherToken = app.login("yuval", "a weak password").join()
        app.makeAdministrator(adminToken, "yuval").join()

        app.channelJoin(adminToken, "#TakeCare").join()
        app.channelJoin(otherToken, "#TakeCare2").join()

        assertThrows<NoSuchEntityException> {
            app.channelKick(adminToken, "#TakeCare", "yuval").joinException()
        }
    }

    @Test
    internal fun `operator can kick members from a channel`() {
        val adminToken = app.login("sahar", "a very strong password").join()
        val otherToken = app.login("yuval", "a weak password").join()
        app.channelJoin(adminToken, "#TakeCare").join()
        app.channelJoin(otherToken, "#TakeCare").join()

        app.channelKick(adminToken, "#TakeCare", "yuval").join()

        app.isUserInChannel(adminToken, "#TakeCare", "yuval").join()?.let { assertFalse(it) }
    }

    @Test
    internal fun `attempting to kick a member from a channel with operator privileges for another channel should throw UserNotAuthorizedException`() {
        val adminToken = app.login("sahar", "a very strong password").join()
        val otherToken = app.login("yuval", "a weak password").join()
        app.makeAdministrator(adminToken, "yuval").join()

        app.channelJoin(adminToken, "#TakeCare").join()
        app.channelJoin(otherToken, "#TakeCare2").join()

        assertThrows<UserNotAuthorizedException> {
            app.channelKick(adminToken, "#TakeCare2", "yuval").joinException()
        }
    }

    @Test
    internal fun `member & admin can query the total number of members in the channel`() {
        val adminToken = app.login("sahar", "a very strong password").join()
        val token2 = app.login("yuval", "a weak password").join()
        val token3 = app.login("victor", "big").join()
        val channel = "#TakeCare"

        app.channelJoin(adminToken, channel).join()
        assertEquals(1, app.numberOfTotalUsersInChannel(adminToken, channel).join())

        app.channelJoin(token2, channel).join()
        assertEquals(2, app.numberOfTotalUsersInChannel(token2, channel).join())

        app.channelJoin(token3, channel).join()
        assertEquals(3, app.numberOfTotalUsersInChannel(token3, channel).join())

        app.channelPart(adminToken, channel).join()
        assertEquals(2, app.numberOfTotalUsersInChannel(adminToken, channel).join())

        app.channelPart(token3, channel).join()
        assertEquals(1, app.numberOfTotalUsersInChannel(adminToken, channel).join())
    }

    @Test
    internal fun `member & admin can query the number of active members in the channel`() {
        val adminToken = app.login("sahar", "a very strong password").join()
        val token2 = app.login("yuval", "a weak password").join()
        val channelOne = "#TakeCare"
        val channelTwo = "#TakeCare2"

        app.channelJoin(adminToken, channelOne).join()
        app.channelJoin(adminToken, channelTwo).join()

        app.channelJoin(token2, channelOne).join()
        app.channelJoin(token2, channelTwo).join()

        assertEquals(2, app.numberOfActiveUsersInChannel(adminToken, channelOne).join())
        assertEquals(2, app.numberOfActiveUsersInChannel(adminToken, channelTwo).join())

        app.channelPart(adminToken, channelOne).join()

        assertEquals(1, app.numberOfActiveUsersInChannel(adminToken, channelOne).join())
        assertEquals(2, app.numberOfActiveUsersInChannel(adminToken, channelTwo).join())

        app.logout(token2).join()

        assertEquals(0, app.numberOfActiveUsersInChannel(adminToken, channelOne).join())
        assertEquals(1, app.numberOfActiveUsersInChannel(adminToken, channelTwo).join())
    }
}