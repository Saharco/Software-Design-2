package il.ac.technion.cs.softwaredesign

import com.authzee.kotlinguice4.KotlinModule
import com.google.inject.Provides
import com.google.inject.Singleton
import il.ac.technion.cs.softwaredesign.database.CourseAppDatabaseFactory
import il.ac.technion.cs.softwaredesign.database.Database
import il.ac.technion.cs.softwaredesign.messages.Message
import il.ac.technion.cs.softwaredesign.messages.MessageFactory
import il.ac.technion.cs.softwaredesign.messages.MessageFactoryImpl
import il.ac.technion.cs.softwaredesign.messages.MessageImpl
import il.ac.technion.cs.softwaredesign.mocks.SecureStorageFactoryMock
import il.ac.technion.cs.softwaredesign.storage.SecureStorage
import il.ac.technion.cs.softwaredesign.utils.DatabaseMapper
import java.util.concurrent.CompletableFuture

class CourseAppModule : KotlinModule() {

    /*
     * Replace with these two when running the tests with mocks:
     *
     * --- TO THE STAFF: ---
     *  we are pretty sure this is what you meant, but this means that at the time of submission:
     *  our tests do not run anymore because we don't have the real SecureStorage implementation
     * ---------------------
     *
     * private val factory = SecureStorageFactoryMock()
     * private val dbFactory = CourseAppDatabaseFactory(factory)
     *
     */

    private val factory = SecureStorageFactoryMock() //TODO: change this when submitting
    private val dbFactory = CourseAppDatabaseFactory(factory)

    override fun configure() {
        bind<CourseApp>().to<CourseAppImpl>()
        bind<CourseAppStatistics>().to<CourseAppStatisticsImpl>()
        bind<CourseAppInitializer>().to<CourseAppInitializerImpl>()
        bind<MessageFactory>().to<MessageFactoryImpl>()
    }

    @Provides
    @Singleton
    fun dbMapperProvider(): DatabaseMapper {
        val dbMap = mutableMapOf<String, CompletableFuture<Database>>()
        val storageMap = mutableMapOf<String, CompletableFuture<SecureStorage>>()

        mapNewDatabase(dbMap, "course_app_database")

        mapNewStorage(storageMap, "channels_by_users")
        mapNewStorage(storageMap, "channels_by_active_users")
        mapNewStorage(storageMap, "users_by_channels")
        mapNewStorage(storageMap, "channels_by_messages")
        return DatabaseMapper(dbMap, storageMap)
    }

    private fun mapNewDatabase(dbMap: MutableMap<String, CompletableFuture<Database>>, dbName: String) {
        dbMap[dbName] = dbFactory.open(dbName)
    }

    private fun mapNewStorage(storageMap: MutableMap<String, CompletableFuture<SecureStorage>>, storageName: String) {
        storageMap[storageName] = factory.open(storageName.toByteArray())
    }
}