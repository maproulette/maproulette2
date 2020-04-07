# MapRoulette Testing

MapRoulette testing is split into 3 different types:
- Unit Testing
- Integration Testing
- [Postman Testing](../postman/README.md)

Unit testing is obviously the simplest of the three and uses a lot of mock objects to accomplish it's goal of testing specific functionality of different objects.

Integration testing takes the form of starting up a application server and connecting to an actual database. The database information is configured in the [TestDatabase](org/maproulette/framework/util/TestDatabase.scala), by default it will try to connect to localhost:5432/mr_test using a OSM:OSM username password combination. So modifying that is pretty easy if you need to. It will only require an empty database and will run all the evolutions on the database before testing and clean it up after it is complete. When writing integration tests, you can extend the [FrameworkHelper](org/maproulette/framework/util/FrameworkHelper.scala) class that will create a separate project structure for your test. However it is important to understand that you are sharing the database with all the other tests, so there is possible interaction with the tests that you need to be careful about. This would mostly be around objects that are created system wide, like Users and Projects for instance. Generally these tests will give you a good indication of how the system works as a whole. Mocks can be used in conjunction within your tests, however most of the time is not required.

Postman testing is a series of API calls that are made to a live server to make sure the responses are valid. You can find the Postman tests in the file [maproulette2.postman_collection.json](../postman/maproulette2.postman_collection.json). You can take that file and import it into Postman and run them locally.

### Travis

MapRoulette's build is integrated into Travis which runs through the Unit and Integration tests, however will not run any Postman tests. So those for the most part should be run manually.
