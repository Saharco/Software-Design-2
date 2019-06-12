import com.authzee.kotlinguice4.KotlinModule
import il.ac.technion.cs.softwaredesign.CourseAppModule
import il.ac.technion.cs.softwaredesign.mocks.SecureStorageFactoryMock
import il.ac.technion.cs.softwaredesign.storage.SecureStorageFactory

class CourseAppTestModule: KotlinModule() {
    override fun configure() {
        bind<SecureStorageFactory>().toInstance(SecureStorageFactoryMock())
        install(CourseAppModule())
    }
}