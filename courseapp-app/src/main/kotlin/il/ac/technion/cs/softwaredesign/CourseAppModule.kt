package il.ac.technion.cs.softwaredesign

import com.authzee.kotlinguice4.KotlinModule
import com.google.inject.Inject
import com.google.inject.Provides
import com.google.inject.Singleton
import il.ac.technion.cs.softwaredesign.database.CachedStorage
import il.ac.technion.cs.softwaredesign.database.CourseAppDatabaseFactory
import il.ac.technion.cs.softwaredesign.database.Database
import il.ac.technion.cs.softwaredesign.database.DatabaseFactory
import il.ac.technion.cs.softwaredesign.messages.MessageFactory
import il.ac.technion.cs.softwaredesign.messages.MessageFactoryImpl
import il.ac.technion.cs.softwaredesign.storage.SecureStorageFactory
import il.ac.technion.cs.softwaredesign.utils.DatabaseMapper
import java.util.concurrent.CompletableFuture

class CourseAppModule : KotlinModule() {

    override fun configure() {
        bind<CourseApp>().to<CourseAppImpl>()
        bind<CourseAppStatistics>().to<CourseAppStatisticsImpl>()
        bind<CourseAppInitializer>().to<CourseAppInitializerImpl>()
        bind<MessageFactory>().to<MessageFactoryImpl>()
    }

    @Provides
    @Singleton
    @Inject
    fun dbMapperProvider(factory: SecureStorageFactory): DatabaseMapper {
        val dbFactory = CourseAppDatabaseFactory(factory)
        val dbMap = mutableMapOf<String, CompletableFuture<Database>>()
        val storageMap = mutableMapOf<String, CompletableFuture<CachedStorage>>()

        mapNewDatabase(dbFactory, dbMap, "course_app_database")

        mapNewStorage(factory, storageMap, "channels_by_users")
        mapNewStorage(factory, storageMap, "channels_by_active_users")
        mapNewStorage(factory, storageMap, "users_by_channels")
        mapNewStorage(factory, storageMap, "channels_by_messages")
        return DatabaseMapper(dbMap, storageMap)
    }

    private fun mapNewDatabase(dbFactory: DatabaseFactory, dbMap: MutableMap<String, CompletableFuture<Database>>,
                               dbName: String) {
        dbMap[dbName] = dbFactory.open(dbName)
    }

    private fun mapNewStorage(factory: SecureStorageFactory,
                              storageMap: MutableMap<String, CompletableFuture<CachedStorage>>, storageName: String) {
        storageMap[storageName] =
                factory.open(storageName.toByteArray()).thenApply { storage -> CachedStorage(storage) }
    }
}