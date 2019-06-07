package il.ac.technion.cs.softwaredesign

import com.authzee.kotlinguice4.KotlinModule
import com.google.inject.Provides
import com.google.inject.Singleton
import il.ac.technion.cs.softwaredesign.database.CourseAppDatabaseFactory
import il.ac.technion.cs.softwaredesign.database.Database
import il.ac.technion.cs.softwaredesign.mocks.SecureStorageFactoryMock
import il.ac.technion.cs.softwaredesign.storage.SecureStorage
import il.ac.technion.cs.softwaredesign.utils.DatabaseMapper

class CourseAppModule : KotlinModule() {

    private val factory = SecureStorageFactoryMock() //TODO: change this when submitting
    private val dbFactory = CourseAppDatabaseFactory(factory)

    override fun configure() {
        /*
        This binding should be changed to the actual remote storage factory class in courseapp-test.
        For this package's purposes: we will use a *mock* implementation (no persistent storage) in
        order to properly test the CourseApp functionality
         */
//        bind<SecureStorageFactory>().to<SecureStorageFactoryMock>()

        /*
        These bindings inject our implementation for the provided CourseApp interfaces
         */
        bind<CourseApp>().to<CourseAppImpl>()
        bind<CourseAppStatistics>().to<CourseAppStatisticsImpl>()
        bind<CourseAppInitializer>().to<CourseAppInitializerImpl>()
    }

    @Provides
    @Singleton
    fun courseAppProvider(): DatabaseMapper {
        val dbMap = mutableMapOf<String, Database>()
        val storageMap = mutableMapOf<String, SecureStorage>()

        mapNewDatabase(dbMap, "users")
        mapNewDatabase(dbMap, "channels")

        mapNewStorage(storageMap, "channels_by_users")
        mapNewStorage(storageMap, "channels_by_active_users")
        mapNewStorage(storageMap, "users_by_channels")
        return DatabaseMapper(dbMap, storageMap)
    }

    private fun mapNewDatabase(dbMap: MutableMap<String, Database>, dbName: String) {
        dbMap[dbName] = dbFactory.open(dbName)
    }

    private fun mapNewStorage(storageMap: MutableMap<String, SecureStorage>, storageName: String) {
        storageMap[storageName] = factory.open(storageName.toByteArray())
    }
}