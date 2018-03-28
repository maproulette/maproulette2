name := "MapRouletteV2"

version := "2.1.0"

scalaVersion := "2.11.8"

packageName in Universal := "MapRouletteV2"

lazy val compileScalastyle = taskKey[Unit]("compileScalastyle")

compileScalastyle := org.scalastyle.sbt.ScalastylePlugin.scalastyle.in(Compile).toTask("").value

(scalastyleConfig in Compile) := baseDirectory.value / "conf/scalastyle-config.xml"

//(compile in Compile) <<= (compile in Compile) dependsOn compileScalastyle

lazy val `MapRouletteV2` = (project in file("."))
  .enablePlugins(PlayScala, SbtWeb, SwaggerPlugin)

swaggerDomainNameSpaces := Seq("org.maproulette.models", "org.maproulette.exception", "org.maproulette.session", "org.maproulette.actions", "org.maproulette.data")

swaggerOutputTransformers := Seq(envOutputTransformer)

swaggerRoutesFile := "apiv2.routes"

pipelineStages := Seq(digest, gzip)

routesGenerator := InjectedRoutesGenerator

libraryDependencies ++= Seq(
  jdbc,
  cache,
  ws,
  evolutions,
  specs2 % Test,
  filters,
  "org.scalatestplus.play" %% "scalatestplus-play" % "1.5.1" % Test,
  "org.webjars" % "swagger-ui" % "3.0.5",
  "com.typesafe.play" %% "anorm" % "3.0.0-M1",
  "postgresql" % "postgresql" % "9.1-901-1.jdbc4",
  "net.postgis" % "postgis-jdbc" % "2.2.0",
  "joda-time" % "joda-time" % "2.9.2",
  "com.vividsolutions" % "jts" % "1.13",
  "org.wololo" % "jts2geojson" % "0.10.0",
  "org.julienrf" %% "play-jsmessages" % "2.0.0",
  "org.webjars" %% "webjars-play" % "2.5.0-4",
  "org.webjars" % "bootstrap" % "3.3.6",
  "org.webjars" % "font-awesome" % "4.5.0",
  "org.webjars" % "ionicons" % "2.0.1",
  "org.webjars" % "respond" % "1.4.2",
  "org.webjars" % "html5shiv" % "3.7.3",
  "org.webjars" % "jquery" % "2.2.4",
  "org.webjars" % "toastr" % "2.1.1",
  "org.webjars" % "bootstrap-daterangepicker" % "2.1.19",
  "org.webjars" % "momentjs" % "2.12.0",
  "org.webjars" % "datatables" % "1.10.11",
  "org.webjars" % "js-cookie" % "2.1.0",
  "org.webjars" % "tag-it" % "2.0",
  "org.webjars.bower" % "fuelux" % "3.14.2"
    exclude("org.webjars.bower", "jquery") exclude("org.webjars.bower", "moment")
    exclude("org.webjars.bower", "requirejs") exclude("org.webjars.bower", "bootstrap"),
  "org.webjars.bower" % "jQuery-QueryBuilder" % "2.3.2"
    exclude("org.webjars.bower", "moment") exclude("org.webjars.bower", "jquery")
    exclude("org.webjars.bower", "bootstrap") exclude("org.webjars.bower", "doT"),
  "org.webjars.bower" % "leaflet.markercluster" % "1.0.0-rc.1",
  "org.webjars.bower" % "github-com-makinacorpus-Leaflet-Spin" % "0.1.1",
  "org.webjars.bower" % "marked" % "0.3.5",
  "org.webjars.bower" % "chartjs" % "2.1.0"
)

dependencyOverrides += "org.webjars" % "bootstrap" % "3.3.6"
dependencyOverrides += "org.webjars" % "jquery" % "2.2.4"
dependencyOverrides += "org.webjars" % "momentjs" % "2.12.0"

unmanagedResourceDirectories in Test <+=  baseDirectory ( _ /"target/web/public/test" )

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

includeFilter in (Assets, LessKeys.less) := "*.less"

excludeFilter in (Assets, LessKeys.less) := "_*.less"
