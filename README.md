# MapRoulette API
Welcome to New MapRoulette, the powerful & popular bug fixing tool (or is it a game?) for OpenStreetMap.

This README deals with development related topics only. If you are interested in contributing to OpenStreetMap by fixing some bugs through MapRoulette, just head over to [the MapRoulette web site](http://maproulette.org) and get started - it should be pretty self explanatory.

That said, read on if you want to contribute to MapRoulette development and are ready to deploy your local instance.

## Contributing

Please fork the project and submit a pull request. See [Postman Docs](postman/README.md) for information on API Testing.

### Frameworks used

New MapRoulette is built upon the Play Framework using Scala. You can find more information about the Play Framework at https://www.playframework.com
It uses the following core technologies:

* Postgres 9.5 with PostGIS 2.2.1
* Play Framework 2.7.1 with Scala 2.12.8

## Deploying MapRoulette

### Server

### Local for test and development

#### Register Dev App with OpenStreetMap

Before beginning, you'll need to register an app with OpenStreetMap to get a consumer key and secret key. For development and testing, you may wish to do this on the [OSM dev server](http://master.apis.dev.openstreetmap.org) (you will need to setup a new user account if you have't used the dev server before).

To register your app, login to your account, go to "My Settings", click on "oauth settings", and then click "Register your Application" near the bottom. Give your app a name and application URL (you can simply use http://localhost:9000 if desired) and leave the other URL fields blank. In the permissions section, check "read their user preferences" and then click the "Register" button at the bottom to get your consumer and secret keys. Be sure to take note of them.

For more details on the app registration process, see the [OSM OAuth wiki page](http://wiki.openstreetmap.org/wiki/OAuth).


#### Mac OSX

> These instructions assume you have at least Mac OS 10.10 (Mavericks) and [Homebrew](http://brew.sh/) installed. We also assume that you have at least PostgreSQL 9.5 and PostGIS 2.2.1 installed. Homebrew provides packages for both (`brew install postgresql` and `brew install postgis`), which we recommend.

* Make sure you have a Java 8 JDK. Check with `java -version` which should mention an 1.8.x version number. [Get](http://www.oracle.com/technetwork/java/javase/downloads/jdk8-downloads-2133151.html) and install a Java 8 JDK if necessary.
* Install the [Play Framework activator](https://www.playframework.com/documentation/2.5.x/Installing): `brew install typesafe-activator`.
* Create a PostgreSQL superuser `osm`: `createuser -sW osm`. Use `osm` as the password.
* Create a new PostgreSQL database `mp_dev` owned by `osm`: `createdb -O osm mp_dev`.
* Setup your environment variables:
    - Copy `conf/dev.conf.example` to `conf/dev.conf`
    - Update database connection JDBC string as `MR_DATABASE_URL`: `export MR_DATABASE_URL='jdbc:postgresql://localhost:5432/mp_dev?user=osm&password=osm'`
    - Consumer key from [app registration](#register-dev-app-with-openstreetmap) as `MR_OAUTH_CONSUMER_KEY`: `export MR_OAUTH_CONSUMER_KEY=<APPLICATION_CONSUMER_KEY>`
    - Consumer secret from [app registration](#register-dev-app-with-openstreetmap) as `MR_OAUTH_CONSUMER_SECRET`: `export MR_OAUTH_CONSUMER_SECRET=<APPLICATION_CONSUMER_SECRET>`
    - OSM server URL as `MR_OSM_SERVER` if you wish to use the dev server (defaults to production): `export MR_OSM_SERVER='http://master.dev.openstreetmap.org'`
    - APIHost used for Swagger API documentation as `API_HOST`: `export API_HOST=localhost:9000`
* Clone New MapRoulette: `git clone https://github.com/maproulette/maproulette2.git`.
* Navigate into the newly created `maproulette2` directory and run the local development server: `activator run`. This will take some time the first run as dependencies are downloaded.
* Head to [http://localhost:9000/](http://localhost:9000/) and confirm you can see the New MapRoulette front end. This also may take a while as artifacts are compiled.

If you are having issues getting the activator to run, you can configure your instance with [dev.conf](#using-devconf)

#### Linux

> These instructions were written for Ubuntu 16.04

* Make sure you have a Java 8 JDK. Check with `java -version` which should mention a 1.8.x version number. 
* If you don't have Java 8 JDK you can get it with the following command `sudo apt install openjdk-8-jdk`
* Install the [Play Framework activator](https://www.playframework.com/documentation/2.5.x/Installing)
    * After downloading unzip the archive to a directory that you have read and write access to
    * Then add `activator` to your path: Add the following to your `.bashrc` or equivalent: `export PATH=$PATH:/path/to/unzipped-files/bin/`
* Install PostgreSQL and PostGIS: `sudo apt install postgresql postgis`
* Create a PostgreSQL superuser: `osm`: `sudo -u postgres createuser -sP osm`. Use `osm` as the password
* Create a new PostgreSQL database `mp_dev` owned by `osm`: `sudo -u postgres createdb -O osm mp_dev`
* Setup your environment variables:
    - Database connection JDBC string as `MR_DATABASE_URL`: `export MR_DATABASE_URL='jdbc:postgresql://localhost:5432/mp_dev?user=osm&password=osm'`
    - Consumer key from [app registration](#register-dev-app-with-openstreetmap) as `MR_OAUTH_CONSUMER_KEY`: `export MR_OAUTH_CONSUMER_KEY=<APPLICATION_CONSUMER_KEY>`
    - Consumer secret from [app registration](#register-dev-app-with-openstreetmap) as `MR_OAUTH_CONSUMER_SECRET`: `export MR_OAUTH_CONSUMER_SECRET=<APPLICATION_CONSUMER_SECRET>`
    - OSM server URL as `MR_OSM_SERVER` if you wish to use the dev server (defaults to production): `export MR_OSM_SERVER=http://master.dev.openstreetmap.org`
    - APIHost used for Swagger API documentation as `API_HOST`: `export API_HOST=localhost:9000`
* Clone New MapRoulette: `git clone https://github.com/maproulette/maproulette2.git`.
* Navigate into the newly created `maproulette2` directory and run the local development server: `activator run`. This will take some time the first run as dependencies are downloaded.
* Head to [http://localhost:9000/](http://localhost:9000/) and confirm you can see the New MapRoulette front end. This also may take a while as artifacts are compiled.

#### Windows

A work-in-progress setup guide for Windows lives [here](https://gist.github.com/3710d7f15534ec747423a3117cd7cc9c). Please fork and improve!

#### Using dev.conf

Another way to handle dev related configuration variables is to use the [dev.conf](conf/dev.conf) file which has a couple of prepopulated variables that would be beneficial for a test/development environment. To use this file you simply need to add the file as a jvm parameter, eg. -Dconfig.resource=dev.conf

```
activator run -Dconfig.resource=dev.conf
```

Your conf/dev.conf file should have the following:

```
include "application.conf"

db.default.url="jdbc:postgresql://localhost:5432/mp_dev?user=osm&password=osm"
maproulette.super.key="test"
maproulette.super.accounts="*"
osm.server="http://api06.dev.openstreetmap.org"
osm.consumerKey=<APPLICATION_CONSUMER_KEY>
osm.consumerSecret=<APPLICATION_CONSUMER_SECRET>
```

#### SMTP (email) configuration

MapRoulette now supports transmission of emails, for example to inform users
when they receive new in-app notifications. You will need access to an SMTP
server to send emails, and will also need to add SMTP configuration settings to
your configuration file (or whatever configuration mechanism you're using).
`play.mailer.host` is the only required SMTP setting, but most SMTP servers
will also want a username and password.

```
play.mailer.host = "smtp.server.com"
play.mailer.user = "smtpusername"
play.mailer.password = "secret"
```

Many additional SMTP configuration options are available -- see
[play-mailer](https://github.com/playframework/play-mailer/blob/master/README.md)
for a full list.

> Note: If you're doing development, you can set `play.mailer.mock = yes` to
> simply log emails instead of transmitting them

It's also important that you set the `emailFrom` setting to the email address
you wish emails to come from, and that you ensure `publicOrigin` is set so that
links in emails will properly point to your server.

```
emailFrom = "maproulette@yourserver.com"
publicOrigin = "https://www.yourserver.com"
```

By default, notification emails that are to be sent immediately are processed
by a background job every 1 minute. Both the frequency and max number of emails
to process in a single run can be controlled.

```
notifications.immediateEmail.interval = "1 minute"
notifications.immediateEmail.batchSize = 10         # max emails per run
```

Notification emails that are to be sent as a digest are initially processed at
8pm local server time by default, and then every 24 hours thereafter (i.e.
daily digests). Both of these settings can be customized. There is not
currently a maximum limit to the number of emails for digest emails.

```
notifications.digestEmail.startTime = "20:00:00"    # 8pm local server time
notifications.digestEmail.interval = "24 hours"     # once daily
```

#### SSL

Openstreetmap.org recently moved to SSL only. This means that to authenticate against any SSL server you are now required to make sure that Java trusts the OSM SSL certificates. This is not very difficult to do, however they need to be completed for it to work. The steps below are for linux/Mac systems.

1. execute the following command ```openssl s_client -showcerts -connect "www.openstreetmap.org:443" -servername www.openstreetmap.org </dev/null | sed -ne '/-BEGIN CERTIFICATE-/,/-END CERTIFICATE-/p' > osm.pem```
2. execute the following command ```keytool -importcert -noprompt -trustcacerts -alias www.openstreetmap.org -file osm.pem -keystore osmcacerts -storepass openstreetmap```
3. Run server using sbt ```sbt run -Dconfig.resource=dev.conf -Djavax.net.ssl.trustStore=/path/to/file/osmcacerts -Djavax.net.ssl.trustStorePassword=openstreetmap```

If you want to connect to the dev servers you can simply replace all instances of www.openstreetmap.org with master.apis.dev.openstreetmap.org

## Creating new Challenges

[The wiki for this repo](https://github.com/maproulette/maproulette2/wiki) has some information on creating challenges.

[Challenge API](docs/challenge_api.md) has further information about creating challenges through the API.

See also the Swagger API documentation. You can view the documentation by going to the URL ```docs/swagger-ui/index.html?url=/assets/swagger.json``` on any MapRoulette instance.

## Contact

Bug and feature requests are best left as an issue right here on Github. For other things, contact maproulette@maproulette.org

MapRoulette now also has a channel #maproulette on the [OSM US Slack community](http://osmus.slack.com). Invite yourself [here](https://osmus-slack.herokuapp.com/)!
