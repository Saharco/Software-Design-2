import com.authzee.kotlinguice4.getInstance
import com.google.inject.Guice
import il.ac.technion.cs.softwaredesign.*
import il.ac.technion.cs.softwaredesign.exceptions.InvalidTokenException
import il.ac.technion.cs.softwaredesign.exceptions.NoSuchEntityException
import il.ac.technion.cs.softwaredesign.messages.MediaType
import il.ac.technion.cs.softwaredesign.messages.MessageFactory
import il.ac.technion.cs.softwaredesign.storage.SecureStorageModule
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.util.concurrent.CompletableFuture

class CourseAppMessagesTest {

    private val injector = Guice.createInjector(CourseAppModule(), SecureStorageModule())
    private val messageFactory = injector.getInstance<MessageFactory>()
    private val app = injector.getInstance<CourseApp>()
    private val statistics = injector.getInstance<CourseAppStatistics>()

    init {
        injector.getInstance<CourseAppInitializer>().setup().join()
    }

    @Test
    internal fun `trying to attach a listener with an invalid token throws InvalidTokenException`() {
        val listener = mockk<ListenerCallback>()
        every { listener(any(), any()) }.returns(CompletableFuture.completedFuture(Unit))
        assertThrows<InvalidTokenException> {
            app.addListener("bad token", listener).joinException()
        }
    }

    @Test
    internal fun `trying to remove a listener that is not registered should throw NoSuchEntityException`() {
        val listener = mockk<ListenerCallback>()
        every { listener(any(), any()) }.returns(CompletableFuture.completedFuture(Unit))
        val token = app.login("Admin", "a very strong password").join()
        assertThrows<NoSuchEntityException> {
            app.removeListener(token, listener).joinException()
        }
    }

    @Test
    internal fun `user listener is called when a private message is sent`() {
        val listener = mockk<ListenerCallback>()
        every { listener(any(), any()) }.returns(CompletableFuture.completedFuture(Unit))
        val (adminToken, message) = app.login("Admin", "a very strong password")
                .thenCompose { adminToken ->
                    app.login("Sahar", "a very weak password")
                            .thenApply { Pair(adminToken, it) }
                }.thenCompose { (adminToken, nonAdminToken) ->
                    app.addListener(nonAdminToken, listener)
                            .thenApply { adminToken }
                }.thenCompose { adminToken ->
                    messageFactory.create(MediaType.STICKER, "Smiley".toByteArray())
                            .thenApply { Pair(adminToken, it) }
                }.join()

        assertEquals(0, statistics.pendingMessages().join())
        app.privateSend(adminToken, "Sahar", message).join()
        assertEquals(0, statistics.pendingMessages().join())

        verify {
            listener(match { it == "@Admin" },
                    match { it.contents contentEquals "Smiley".toByteArray() })
        }
    }

    @Test
    internal fun `user listeners are called when a channel message is sent`() {
        val listener1 = mockk<ListenerCallback>()
        every { listener1(any(), any()) }.returns(CompletableFuture.completedFuture(Unit))

        val listener2 = mockk<ListenerCallback>()
        every { listener2(any(), any()) }.returns(CompletableFuture.completedFuture(Unit))
        val (adminToken, message) = app.login("Admin", "a very strong password")
                .thenCompose { adminToken ->
                    app.channelJoin(adminToken, "#TakeCare")
                            .thenApply { adminToken }
                }.thenCompose { adminToken ->
                    app.login("Sahar", "a very weak password")
                            .thenApply { Pair(adminToken, it) }
                }.thenCompose { (adminToken, nonAdminToken) ->
                    app.channelJoin(nonAdminToken, "#TakeCare")
                            .thenApply { Pair(adminToken, nonAdminToken) }
                }.thenCompose { (adminToken, nonAdminToken) ->
                    app.addListener(nonAdminToken, listener1)
                            .thenApply { adminToken }
                }.thenCompose { adminToken ->
                    app.login("Yuval", "pizza")
                            .thenApply { Pair(adminToken, it) }
                }.thenCompose { (adminToken, nonAdminToken) ->
                    app.channelJoin(nonAdminToken, "#TakeCare")
                            .thenApply { Pair(adminToken, nonAdminToken) }
                }.thenCompose { (adminToken, nonAdminToken) ->
                    app.addListener(nonAdminToken, listener2)
                            .thenApply { adminToken }
                }.thenCompose { adminToken ->
                    messageFactory.create(MediaType.STICKER, "Smiley".toByteArray())
                            .thenApply { Pair(adminToken, it) }
                }.join()

        assertEquals(0, statistics.pendingMessages().join())
        app.channelSend(adminToken, "#TakeCare", message).join()
        assertEquals(0, statistics.pendingMessages().join())

        verify {
            listener1(match { it == "#TakeCare@Admin" },
                    match { it.contents contentEquals "Smiley".toByteArray() })
        }

        verify {
            listener2(match { it == "#TakeCare@Admin" },
                    match { it.contents contentEquals "Smiley".toByteArray() })
        }
    }

    @Test
    internal fun `user listeners are called when a broadcast message is sent`() {
        val listener1 = mockk<ListenerCallback>()
        every { listener1(any(), any()) }.returns(CompletableFuture.completedFuture(Unit))

        val listener2 = mockk<ListenerCallback>()
        every { listener2(any(), any()) }.returns(CompletableFuture.completedFuture(Unit))

        val listener3 = mockk<ListenerCallback>()
        every { listener3(any(), any()) }.returns(CompletableFuture.completedFuture(Unit))

        val (adminToken, message) = app.login("Admin", "a very strong password")
                .thenCompose { adminToken ->
                    app.addListener(adminToken, listener1)
                            .thenApply { adminToken }
                }.thenCompose { adminToken ->
                    app.login("Sahar", "a very weak password")
                            .thenApply { Pair(adminToken, it) }
                }.thenCompose { (adminToken, nonAdminToken) ->
                    app.addListener(nonAdminToken, listener2)
                            .thenApply { adminToken }
                }.thenCompose { adminToken ->
                    app.login("Yuval", "pizza")
                            .thenApply { Pair(adminToken, it) }
                }.thenCompose { (adminToken, nonAdminToken) ->
                    app.addListener(nonAdminToken, listener3)
                            .thenApply { adminToken }
                }.thenCompose { adminToken ->
                    messageFactory.create(MediaType.STICKER, "Smiley".toByteArray())
                            .thenApply { Pair(adminToken, it) }
                }.join()

        assertEquals(0, statistics.pendingMessages().join())
        app.broadcast(adminToken, message).join()
        assertEquals(0, statistics.pendingMessages().join())

        verify {
            listener1(match { it == "BROADCAST" },
                    match { it.contents contentEquals "Smiley".toByteArray() })
        }

        verify {
            listener2(match { it == "BROADCAST" },
                    match { it.contents contentEquals "Smiley".toByteArray() })
        }

        verify {
            listener3(match { it == "BROADCAST" },
                    match { it.contents contentEquals "Smiley".toByteArray() })
        }
    }

    @Test
    internal fun `user listener is called when attached after a private message was sent`() {
        val listener = mockk<ListenerCallback>()
        every { listener(any(), any()) }.returns(CompletableFuture.completedFuture(Unit))
        val (adminToken, nonAdminToken, message) = app.login("Admin", "a very strong password")
                .thenCompose { adminToken ->
                    app.login("Sahar", "a very weak password")
                            .thenApply { Pair(adminToken, it) }
                }.thenCompose { (adminToken, nonAdminToken) ->
                    messageFactory.create(MediaType.STICKER, "Smiley".toByteArray())
                            .thenApply { Triple(adminToken, nonAdminToken, it) }
                }.join()

        assertEquals(0, statistics.pendingMessages().join())
        app.privateSend(adminToken, "Sahar", message).join()
        assertEquals(1, statistics.pendingMessages().join())

        app.addListener(nonAdminToken, listener).join()

        assertEquals(0, statistics.pendingMessages().join())

        verify {
            listener(match { it == "@Admin" },
                    match { it.contents contentEquals "Smiley".toByteArray() })
        }
    }

    @Test
    internal fun `user listener is called when attached after a channel message was sent`() {

    }


    @Test
    internal fun `user listener is called when attached after a broadcast message was sent`() {

    }

    @Test
    internal fun `broadcast message is pending until all users have received it`() {

    }

    @Test
    internal fun `channel message is never removed`() {

    }

    @Test
    internal fun `trying to send a message to a channel that does not exist should throw NoSuchEntityException`() {

    }

    @Test
    internal fun `trying to send a message to a channel that the user is not a member of should throw UserNotAuthorizedException`() {

    }

    @Test
    internal fun `trying to send a broadcast message without admin privileges should throw UserNotAuthorizedException`() {

    }

    @Test
    internal fun `user can read a channel message that predates them joining the channel`() {

    }

    @Test
    internal fun `trying to fetch a channel message that does not exist should throw NoSuchEntityException`() {

    }

    @Test
    internal fun `trying to fetch a channel message that is actually a different type of message should throw NoSuchEntityException`() {

    }

    @Test
    internal fun `trying to fetch a channel message from a channel that the user is not a member of should throw UserNotAuthorizedException`() {

    }
}