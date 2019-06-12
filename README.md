# CourseApp: Assignment 2

## Authors
* Sahar Cohen, 206824088
* Yuval Nahon, 206866832

### Previous assignment
This assignment uses the code from the submission by: 206824088-206866832

## Notes

### Implementation Summary

#### Futures
Every method that invokes SecureStorage's open/read/write methods now returns a future immediately, works asynchronously and *without blocking with join()*.

#### Database Abstraction
Provides a convenient file system hierarchy for remote storage. Split into five interfaces:

* **DatabaseFactory**: opens new / existing databases. Implemented in CourseAppDatabaseFactory.
* **Database**: akin to a *file system*: references its root. Used solely for navigating to nested collections.
* **CollectionReference**: akin to a *folder* in a file system. Used solely for holding documents.
* **DocumentReference**: akin to a *file* in a file system. Values are set in a key-value fashion. Terminal operations include: write/read/update/delete etc.
* **ExtendableDocumentReference**: a DocumentReference that can contain its own collections.

**Example use:**

```kotlin
// Factory pattern for opening databases
val db = CourseAppDatabaseFactory( /* injected SecureStorageFactory */ )
   .open("cool database")
   .join()

// Fluent API for interactions with the database.
// Overloaded versions of methods to provide comfortable use in many common cases
db.collection("users")
  .document("sahar")
  .set(Pair("lastname", "cohen"))
  .set("email accounts", listOf("example@gmail.com", "undergrad@campus.technion.ac.il"))
  .write()
  .thenCompose {
      db.collection("users")
                  .document("sahar")
                  .read("lastname") // = "cohen"
  }.get()
                  
// Many more methods!
```

#### Cached Storage
**CachedStorage** is a wrapper class that implements the *SecureStorage* interface.
It caches recently written values inside a **LimitedCacheMap** (a HashMap wrapper with limtied capacity), and thus achieves **extremely better performance** on read operations: the value will be read from the map instead of the concrete, slow remote storage instance. Of course, the data is still persistant: written values are written to the remote storage, so the data-store is reboot-proof.

#### CourseApp Database Managers
The database is utilized by the following classes for managing database operations within the app:
* **AuthenticationManager**: provides the required operations on users in the app.
* **ChannelsManager**: provides the required operations on channels in the app.
* **MessagesManager**: provides the required operations on messages in the app. 

#### Data Strcutures
* **List**: the database can store entire lists under a single field. (De)serialization done with JSON.
* **AVL Tree**: self balancing binary tree that receives a Secure Storage instance on which to operate on (not the database abstraction for efficiency reasons). Key (reference) to the tree's root is stored under a designated "root" key. (De)serialization done with JSON. Global functions are provided and serve as entry points to query / update the tree. Tree logic (rotations, etc) described in detail here: https://en.wikipedia.org/wiki/AVL_tree
* **LimitedCacheMap**: wrapper for HashMap which is initiallized with some maximum capacity. If more elements are added than the maximum capacity allows: the underlying map is reset to an empty state.

#### Technical Details
* Tokens are generated by chaining the username with the current time (in miliseconds).
* Document names are encrypted with the SHA-256 one-way encryption algorithm.
* Deletions in the database are *logical* deletions. So, when a document / document's field is deleted, a byte array block of "0" is chained to it. Likewise, an "activated" segment is prefixed by a byte array block of "1". The database abstraction preserves this invariant.
* Managers store metadata under a "metadata" collection (e.g. online users, total users).
* Each of the four top10 query operations in CourseAppStatistics operate on a separate SecureStorage instance.
* Messages are serialized/deserialized using the *ObjectDeserializer* class.

**Database structure**: Trees excluded, CourseApp only utilizes a single SecureStorage instance in the "course_app_database" Database. The database's root is split into 4 collections: "all_users", "tokens", "all_channels" and "all_messages".
* "all_users": the following fields are stored for each {username} document: password, token, channels (list of channels that the user is part of), isAdmin ("true" iff user is an administrator), creation_count (unique id which keeps track of the user's creation time compared to other users in the system), messages (list of pending private messages for this user), last_message_read (id of the latest message read by the user). Additionally, the collection contains a "metadata" document that keeps track of the users' count, online users' count and creation time.
* "tokens": the following field is stored for each {token} document: username (of the associated user)
* "all_channels": the following fields are stored for each {channel} document: users_count (number of users in the channel), online_users_count (number of online users in the channel), operators (list of operators), messages_count (amount of channels in the channel), creation_count (unique id which keeps track of the channel's creation time compared to other channels in the system). Additionally, the collection contains a "metadata" document that keeps track of the channels' count and creation time.
* "all_messages": the collection contains two documents: "broadcast_messages" and "channel_messages". Each of them contaisn a "messages" document, which is a list of messages of the respective type. Additionally, the collection contains a "metadata" document that keeps track of the amount of messages in the system.


### Built With
* Gradle for dependencies management
* JUnit - unit testing framework
* Guice - dependency injection framework: https://github.com/google/guice
* MockK - mocking framework: https://github.com/mockk/mockk
* Gson for (de)serialization: https://github.com/google/gson
* Kotlin Logger for logging: https://github.com/MicroUtils/kotlin-logging

### Testing Summary
The following components were thoroughly tested:
* **Database**: tested in CourseAppDatabaseTest. All five components that make up the complete implementation of the database are tested together here, as well as the cached storage. The components work together and you can't do anything useful without all of them at once, hence they're tested together (this is a design choice. e.g. a document instance cannot be initialized without a collection instance which cannot be initialized without a database instance etc).
* **CourseApp**: tested in CourseAppAuthenticationTest, CourseAppChannelsTest, CourseAppMessagesTest. All the manager classes that make up the complete functionality of CourseApp are tested together here. The managers are tested together with CourseApp here for the same reason above.
* **CourseAppStatistics**: tested in CourseAppStatisticsTest. The "top 10" methods' correctness is tested via extensive load testing, and utilizes classes, methods & extension methods provided by utils.kt.
* **AVLTree**: tested in AVLTreeTest.

In total, there are over 100 tests that span nearly 100% code coverage across all the classes we've implemented!!

The tests utilize the *SecureStorageFactoryMock* and *SecureStorageMock* classes to mock the missing behavior of the remote storage (along with its slow read operations). GUice is used to provide the constructor parameter (database mapper) for CourseApp and CourseAppInitializer, and bind the interfaces to the implementations we wrote.

### Difficulties

#### HW 0
We had no prior experience with programming in Kotlin & using MockK so we were pretty clueless at the start. Fortunately, they proved to be very easy and intuitive to use. Our main problem was trying to figure out the database's design. At first, we went with a short and easy API that "did the job", but it felt very "C-style" and low-level, so we scrapped it. When moving on to a better API - we faced the problem of reserved special character "/" in specifying file paths. We decided to used encryption as stated above in order to solve this issue, and made sure to test that it worked. Along the way we got to learn more about Kotlin's standard library.

#### HW 1
We feel like we got the hang of Kotlin quite fast from the previous assignment, but implementing the tree proved to be the greatest obstacle in our development process, specifically the delete method. We faced our first obstacle when trying to fit the previous assignment's code to work with the new skeleton: this refactoring process took many hours (configuring Guice, creating factory for database instances & mocks, general refactoring). It took us quite a while to realize that JSON is a helpful tool for our implementation, and we utilized it to add support for storing lists inside the database. From there, the implementation of CourseApp was quite straight-forward, thanks to proper planning (until we got to the tree part...).

#### HW 2
Refactoring the previous assignment's code to fit the new semantics of CompletableFuture required the vast majority of the work. We basically had to re-write everything nearly from scratch: even the structure of every method was greatly changed. We used this opportunity to extract many more methods outside (no more large methods): mostly to keep the code as much readable as we could with the new verbose pattern. We've also had some issues with the implementation of the messaging functionality due to ambiguity. Implementing the messages required careful planning in advance: we've learned that the hard way :)

### Feedback
Small complaint: the PDF / documentation weren't clear enough about the expected behavior of most of the messaging-related methods. Other than that, it was a great exercise
