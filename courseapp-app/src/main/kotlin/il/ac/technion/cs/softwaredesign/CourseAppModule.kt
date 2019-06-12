package il.ac.technion.cs.softwaredesign

import com.authzee.kotlinguice4.KotlinModule
import com.google.inject.Provides
import com.google.inject.Singleton
import il.ac.technion.cs.softwaredesign.database.CachedStorage
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

    //TODO: check if this needs to change when submitting
    private val factory = SecureStorageFactoryMock()
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
        val storageMap = mutableMapOf<String, CompletableFuture<CachedStorage>>()

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

    private fun mapNewStorage(storageMap: MutableMap<String, CompletableFuture<CachedStorage>>, storageName: String) {
        storageMap[storageName] =
                factory.open(storageName.toByteArray()).thenApply { storage -> CachedStorage(storage) }
    }
}