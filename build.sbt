name := "MapRouletteAPI"

version := "3.7.0"

scalaVersion := "2.13.1"

packageName in Universal := "MapRouletteAPI"

lazy val compileScalastyle = taskKey[Unit]("compileScalastyle")

compileScalastyle := scalastyle.in(Compile).toTask("").value
(scalastyleConfig in Compile) := baseDirectory.value / "conf/scalastyle-config.xml"
//(compile in Compile) := ((compile in Compile) dependsOn compileScalastyle).value

lazy val `MapRouletteV2` = (project in file(".")).enablePlugins(PlayScala, SbtWeb, SwaggerPlugin)

swaggerDomainNameSpaces := Seq("org.maproulette.models", "org.maproulette.exception", "org.maproulette.session", "org.maproulette.actions", "org.maproulette.data")

swaggerOutputTransformers := Seq(envOutputTransformer)

swaggerRoutesFile := "apiv2.routes"

pipelineStages := Seq(gzip)

routesGenerator := InjectedRoutesGenerator

libraryDependencies ++= Seq(
  jdbc,
  ehcache,
  ws,
  evolutions,
  specs2 % Test,
  filters,
  guice,
  "com.typesafe.play" %% "play-json-joda" % "2.8.1",
  "com.typesafe.play" %% "play-json" % "2.8.1",
  "org.scalatestplus.play" %% "scalatestplus-play" % "5.0.0" % Test,
  "org.webjars" % "swagger-ui" % "3.25.0",
  "org.playframework.anorm" %% "anorm" % "2.6.5",
  "org.postgresql" % "postgresql" % "42.2.10",
  "net.postgis" % "postgis-jdbc" % "2.3.0",
  "joda-time" % "joda-time" % "2.10.5",
  "com.vividsolutions" % "jts" % "1.13",
  "org.wololo" % "jts2geojson" % "0.14.3",
  "org.apache.commons" % "commons-lang3" % "3.9",
  "commons-codec" % "commons-codec" % "1.14",
  "com.typesafe.play" %% "play-mailer" % "8.0.0",
  "com.typesafe.play" %% "play-mailer-guice" % "8.0.0",
  "com.typesafe.akka" %% "akka-cluster-tools" % "2.6.1",
  "com.typesafe.akka" %% "akka-cluster-typed" % "2.6.1",
  "net.debasishg" %% "redisclient" % "3.20"
)

resolvers ++= Seq(
  Resolver.bintrayRepo("scalaz", "releases"),
  "Atlassian Releases" at "https://maven.atlassian.com/public/",
  Resolver.sonatypeRepo("snapshots")
)

scalacOptions ++= Seq(
  // Show warning feature details in the console
  "-feature",
  // Enable routes file splitting
  "-language:reflectiveCalls"
)

javaOptions in Test ++= Option(System.getProperty("config.file")).map("-Dconfig.file=" + _).toSeq
