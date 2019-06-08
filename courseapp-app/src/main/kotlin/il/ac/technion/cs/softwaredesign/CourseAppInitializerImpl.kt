package il.ac.technion.cs.softwaredesign

import com.google.inject.Inject
import il.ac.technion.cs.softwaredesign.utils.DatabaseMapper
import java.util.concurrent.CompletableFuture

/**
 * Implementation of CourseAppInitializer, which initializes the data-store from an empty state
 * @see CourseAppInitializer
 */
class CourseAppInitializerImpl @Inject constructor(private val dbMapper: DatabaseMapper) :
        CourseAppInitializer {
    override fun setup(): CompletableFuture<Unit> {
        return dbMapper.getDatabase("users")
                .collection("metadata")
                .document("users_data")
                .set(Pair("users_count", "0"))
                .set(Pair("creation_counter", "0"))
                .write()
                .thenCompose {
                    dbMapper.getDatabase("channels")
                            .collection("metadata")
                            .document("channels_data")
                            .set(Pair("creation_counter", "0"))
                            .write()
                }
    }
}