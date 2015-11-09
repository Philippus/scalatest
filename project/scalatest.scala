import sbt._
import Keys._
import java.net.{URL, URLClassLoader}
import java.io.PrintWriter
import scala.io.Source
import com.typesafe.sbt.osgi.SbtOsgi._
import com.typesafe.sbt.SbtPgp._
import org.scalajs.sbtplugin.ScalaJSPlugin
import org.scalajs.sbtplugin.ScalaJSPlugin.autoImport._

object ScalaTestBuild extends Build
  with FeatureSpecModules
  with MatchersModules
  with JUnitModules
  with TestNGModules
  with EasyMockModules
  with JMockModules
  with MockitoModules
  with SeleniumModules
  with RegularTests
  with GenerateTests
  with Doc {

  // To run gentests
  // rm -rf gentests
  // sbt genGenTests/test  (etc., look at specific failures on CI output)

  // To enable deprecation warnings on the fly
  // set scalacOptions in ThisBuild ++= Seq("-unchecked", "-deprecation")

  // To temporarily switch sbt to a different Scala version:
  // > ++ 2.10.5
  val buildScalaVersion = "2.11.7"

  val releaseVersion = "3.0.0-SNAP10"

  val scalacheckVersion = "1.12.5"

  val junitVersion = "4.10"

  val testNGVersion = "6.8.7"

  val guiceVersion = "2.0"

  val easyMockVersion = "3.1"

  val jMockVersion = "2.5.1"

  val mockitoVersion = "1.9.0"

  def envVar(name: String): Option[String] =
    try {
      Some(sys.env(name))
    }
    catch {
      case e: NoSuchElementException => None
    }

  def getGPGFilePath: String =
    envVar("SCALATEST_GPG_FILE") match {
      case Some(path) => path
      case None => (Path.userHome / ".gnupg" / "secring.gpg").getAbsolutePath
    }

  def getGPGPassphase: Option[Array[Char]] =
    envVar("SCALATEST_GPG_PASSPHASE") match {
      case Some(passphase) => Some(passphase.toCharArray)
      case None => None
    }

  def getNexusCredentials: Credentials =
    (envVar("SCALATEST_NEXUS_LOGIN"), envVar("SCALATEST_NEXUS_PASSWORD")) match {
      case (Some(login), Some(password)) => Credentials("Sonatype Nexus Repository Manager", "oss.sonatype.org", login, password)
      case _ => Credentials(Path.userHome / ".ivy2" / ".credentials")
    }

  def getJavaHome: Option[File] =
    envVar("JAVA_HOME") match {
      case Some(javaHome) => Some(file(javaHome))
      case None =>
        val javaHome = new File(System.getProperty("java.home"))
        val javaHomeBin = new File(javaHome, "bin")
        val javac = new File(javaHomeBin, "javac")
        val javacExe = new File(javaHomeBin, "javac.exe")
        if (javac.exists || javacExe.exists)
          Some(file(javaHome.getAbsolutePath))
        else {
          println("WARNING: No JAVA_HOME detected, javac on PATH will be used.  Set JAVA_HOME enviroment variable to a JDK to remove this warning.")
          None
        }
    }

  def sharedSettings: Seq[Setting[_]] = Seq(
    javaHome := getJavaHome,
    scalaVersion := buildScalaVersion,
    crossScalaVersions := Seq(buildScalaVersion, "2.10.5"),
    version := releaseVersion,
    scalacOptions ++= Seq("-feature", "-target:jvm-1.6"),
    resolvers += "Sonatype Public" at "https://oss.sonatype.org/content/groups/public",
    libraryDependencies ++= scalaLibraries(scalaVersion.value),
    publishTo <<= version { v: String =>
      val nexus = "https://oss.sonatype.org/"
      if (v.trim.endsWith("SNAPSHOT")) Some("publish-snapshots" at nexus + "content/repositories/snapshots")
      else                             Some("publish-releases" at nexus + "service/local/staging/deploy/maven2")
    },
    publishMavenStyle := true,
    publishArtifact in Test := false,
    pomIncludeRepository := { _ => false },
    pomExtra := (
      <url>http://www.scalatest.org</url>
        <licenses>
          <license>
            <name>the Apache License, ASL Version 2.0</name>
            <url>http://www.apache.org/licenses/LICENSE-2.0</url>
            <distribution>repo</distribution>
          </license>
        </licenses>
        <scm>
          <url>https://github.com/scalatest/scalatest</url>
          <connection>scm:git:git@github.com:scalatest/scalatest.git</connection>
          <developerConnection>
            scm:git:git@github.com:scalatest/scalatest.git
          </developerConnection>
        </scm>
        <developers>
          <developer>
            <id>bvenners</id>
            <name>Bill Venners</name>
            <email>bill@artima.com</email>
          </developer>
          <developer>
            <id>gcberger</id>
            <name>George Berger</name>
            <email>george.berger@gmail.com</email>
          </developer>
          <developer>
            <id>cheeseng</id>
            <name>Chua Chee Seng</name>
            <email>cheeseng@amaseng.com</email>
          </developer>
        </developers>
      ),
    credentials += getNexusCredentials,
    pgpSecretRing := file(getGPGFilePath),
    pgpPassphrase := getGPGPassphase
  )

  def scalacheckDependency(config: String) =
    "org.scalacheck" %% "scalacheck" % scalacheckVersion % config

  def crossBuildLibraryDependencies(theScalaVersion: String) =
    CrossVersion.partialVersion(theScalaVersion) match {
      // if scala 2.11+ is used, add dependency on scala-xml module
      case Some((2, scalaMajor)) if scalaMajor >= 11 =>
        Seq(
          "org.scala-lang.modules" %% "scala-xml" % "1.0.2",
          "org.scala-lang.modules" %% "scala-parser-combinators" % "1.0.4",
          scalacheckDependency("optional")
        )
      case _ =>
        Seq(scalacheckDependency("optional"))
    }

  def scalaLibraries(theScalaVersion: String) =
    Seq(
      "org.scala-lang" % "scala-compiler" % theScalaVersion % "provided",
      "org.scala-lang" % "scala-reflect" % theScalaVersion // this is needed to compile macro
    )

  def scalatestLibraryDependencies =
    Seq(
      "org.scala-sbt" % "test-interface" % "1.0" % "optional",
      "org.apache.ant" % "ant" % "1.7.1" % "optional",
      "org.ow2.asm" % "asm-all" % "4.1" % "optional",
      "org.pegdown" % "pegdown" % "1.4.2" % "optional"
    )

  def scalatestJSLibraryDependencies =
    Seq(
      "org.scala-js" %% "scalajs-test-interface" % "0.6.5"
    )

  def scalatestTestOptions =
    Seq(Tests.Argument(TestFrameworks.ScalaTest,
      "-l", "org.scalatest.tags.Slow",
      "-m", "org.scalatest",
      "-m", "org.scalactic",
      "-m", "org.scalactic.anyvals",
      "-m", "org.scalactic.algebra",
      "-m", "org.scalactic.enablers",
      "-m", "org.scalatest.fixture",
      "-m", "org.scalatest.concurrent",
      "-m", "org.scalatest.testng",
      "-m", "org.scalatest.junit",
      "-m", "org.scalatest.events",
      "-m", "org.scalatest.prop",
      "-m", "org.scalatest.tools",
      "-m", "org.scalatest.matchers",
      "-m", "org.scalatest.suiteprop",
      "-m", "org.scalatest.mock",
      "-m", "org.scalatest.path",
      "-m", "org.scalatest.selenium",
      "-m", "org.scalatest.exceptions",
      "-m", "org.scalatest.time",
      "-m", "org.scalatest.words",
      "-m", "org.scalatest.enablers",
      "-oDI",
      "-W", "120", "60",
      "-h", "target/html",
      "-u", "target/junit",
      "-fW", "target/result.txt"))

  def scalatestTestJSOptions =
    Seq(Tests.Argument(TestFrameworks.ScalaTest,
      "-l", "org.scalatest.tags.Slow",
      "-m", "org.scalatest",
      "-m", "org.scalactic",
      "-m", "org.scalactic.anyvals",
      "-m", "org.scalactic.algebra",
      "-m", "org.scalactic.enablers",
      "-m", "org.scalatest.fixture",
      "-m", "org.scalatest.concurrent",
      "-m", "org.scalatest.testng",
      "-m", "org.scalatest.junit",
      "-m", "org.scalatest.events",
      "-m", "org.scalatest.prop",
      "-m", "org.scalatest.tools",
      "-m", "org.scalatest.matchers",
      "-m", "org.scalatest.suiteprop",
      "-m", "org.scalatest.mock",
      "-m", "org.scalatest.path",
      "-m", "org.scalatest.selenium",
      "-m", "org.scalatest.exceptions",
      "-m", "org.scalatest.time",
      "-m", "org.scalatest.words",
      "-m", "org.scalatest.enablers",
      "-oDIF"))

  lazy val scalacticMacro = Project("scalacticMacro", file("scalactic-macro"))
    .settings(sharedSettings: _*)
    .settings(
      projectTitle := "Scalactic Macro",
      organization := "org.scalactic",
      sourceGenerators in Compile += {
        Def.task{
          ScalacticGenResourcesJVM.genResources((sourceManaged in Compile).value / "scala" / "org" / "scalactic", version.value, scalaVersion.value)
        }.taskValue
      },
      // Disable publishing macros directly, included in scalactic main jar
      publish := {},
      publishLocal := {}
    )

  lazy val scalacticMacroJS = Project("scalacticMacroJS", file("scalactic-macro.js"))
    .settings(sharedSettings: _*)
    .settings(
      projectTitle := "Scalactic Macro.js",
      organization := "org.scalactic",
      sourceGenerators in Compile += {
        Def.task{
          GenScalacticJS.genMacroScala((sourceManaged in Compile).value / "scala", version.value, scalaVersion.value) ++
          ScalacticGenResourcesJSVM.genResources((sourceManaged in Compile).value / "scala" / "org" / "scalactic", version.value, scalaVersion.value)
        }.taskValue
      },
      // Disable publishing macros directly, included in scalactic main jar
      publish := {},
      publishLocal := {}
    ).enablePlugins(ScalaJSPlugin)

  lazy val scalactic = Project("scalactic", file("scalactic"))
    .settings(sharedSettings: _*)
    .settings(scalacticDocSettings: _*)
    .settings(
      projectTitle := "Scalactic",
      organization := "org.scalactic",
      initialCommands in console := "import org.scalactic._",
      sourceGenerators in Compile += {
        Def.task{
          GenVersions.genScalacticVersions((sourceManaged in Compile).value / "scala" / "org" / "scalactic", version.value, scalaVersion.value) ++
          ScalacticGenResourcesJVM.genFailureMessages((sourceManaged in Compile).value / "scala" / "org" / "scalactic", version.value, scalaVersion.value)
        }.taskValue
      },
      // include the macro classes and resources in the main jar
      mappings in (Compile, packageBin) ++= mappings.in(scalacticMacro, Compile, packageBin).value,
      // include the macro sources in the main source jar
      mappings in (Compile, packageSrc) ++= mappings.in(scalacticMacro, Compile, packageSrc).value,
      scalacticDocSourcesSetting,
      docTaskSetting
    ).settings(osgiSettings: _*).settings(
      OsgiKeys.exportPackage := Seq(
        "org.scalactic",
        "org.scalactic.anyvals",
        "org.scalactic.exceptions"
      ),
      OsgiKeys.importPackage := Seq(
        "org.scalatest.*",
        "org.scalactic.*",
        "scala.util.parsing.*;version=\"$<range;[==,=+);$<replace;1.0.4;-;.>>\"",
        "scala.xml.*;version=\"$<range;[==,=+);$<replace;1.0.4;-;.>>\"",
        "scala.*;version=\"$<range;[==,=+);$<replace;"+scalaBinaryVersion.value+";-;.>>\"",
        "*;resolution:=optional"
      ),
      OsgiKeys.additionalHeaders:= Map(
        "Bundle-Name" -> "Scalactic",
        "Bundle-Description" -> "Scalactic is an open-source library for Scala projects.",
        "Bundle-DocURL" -> "http://www.scalactic.org/",
        "Bundle-Vendor" -> "Artima, Inc."
      )
    ).dependsOn(scalacticMacro % "compile-internal, test-internal").aggregate(LocalProject("scalactic-test"))  // avoid dependency in pom on non-existent scalactic-macro artifact, per discussion in http://grokbase.com/t/gg/simple-build-tool/133shekp07/sbt-avoid-dependence-in-a-macro-based-project

  lazy val scalacticJS = Project("scalacticJS", file("scalactic.js"))
    .settings(sharedSettings: _*)
    .settings(
      projectTitle := "Scalactic.js",
      organization := "org.scalactic",
      moduleName := "scalactic",
      sourceGenerators in Compile += {
        Def.task {
          GenScalacticJS.genScala((sourceManaged in Compile).value / "scala", version.value, scalaVersion.value) ++
          ScalacticGenResourcesJSVM.genFailureMessages((sourceManaged in Compile).value / "scala", version.value, scalaVersion.value)
        }.taskValue
      },
      resourceGenerators in Compile += {
        Def.task {
          GenScalacticJS.genResource((sourceManaged in Compile).value / "scala", version.value, scalaVersion.value)
        }.taskValue
      }
    ).settings(osgiSettings: _*).settings(
      OsgiKeys.exportPackage := Seq(
        "org.scalactic",
        "org.scalactic.anyvals",
        "org.scalactic.exceptions"
      ),
      OsgiKeys.importPackage := Seq(
        "org.scalatest.*",
        "org.scalactic.*",
        "scala.util.parsing.*;version=\"$<range;[==,=+);$<replace;1.0.4;-;.>>\"",
        "scala.xml.*;version=\"$<range;[==,=+);$<replace;1.0.4;-;.>>\"",
        "scala.*;version=\"$<range;[==,=+);$<replace;"+scalaBinaryVersion.value+";-;.>>\"",
        "*;resolution:=optional"
      ),
      OsgiKeys.additionalHeaders:= Map(
        "Bundle-Name" -> "Scalactic",
        "Bundle-Description" -> "Scalactic.js is an open-source library for Scala-js projects.",
        "Bundle-DocURL" -> "http://www.scalactic.org/",
        "Bundle-Vendor" -> "Artima, Inc."
      )
    ).dependsOn(scalacticMacroJS % "compile-internal, test-internal").aggregate(LocalProject("scalacticTestJS")).enablePlugins(ScalaJSPlugin)

  lazy val scalatestCore = Project("scalatestCore", file("scalatest-core"))
   .settings(sharedSettings: _*)
   .settings(scalatestDocSettings: _*)
   .settings(
     projectTitle := "ScalaTest Core",
     organization := "org.scalatest",
     moduleName := "scalatest-core",
     initialCommands in console := """|import org.scalatest._
                                      |import org.scalactic._
                                      |import Matchers._""".stripMargin,
     ivyXML :=
       <dependency org="org.eclipse.jetty.orbit" name="javax.servlet" rev="3.0.0.v201112011016">
         <artifact name="javax.servlet" type="orbit" ext="jar"/>
       </dependency>,
     libraryDependencies ++= crossBuildLibraryDependencies(scalaVersion.value),
     libraryDependencies ++= scalatestLibraryDependencies,
     genGenTask,
     genTablesTask,
     genCodeTask,
     genCompatibleClassesTask,
     genSafeStylesTask,
     sourceGenerators in Compile <+=
         (baseDirectory, sourceManaged in Compile, version, scalaVersion) map genFiles("gengen", "GenGen.scala")(GenGen.genMain),
     sourceGenerators in Compile <+=
         (baseDirectory, sourceManaged in Compile, version, scalaVersion) map genFiles("gentables", "GenTable.scala")(GenTable.genMain),
     sourceGenerators in Compile <+=
         (baseDirectory, sourceManaged in Compile, version, scalaVersion) map genFiles("gencompcls", "GenCompatibleClasses.scala")(GenCompatibleClasses.genMain),
     sourceGenerators in Compile <+=
         (baseDirectory, sourceManaged in Compile, version, scalaVersion) map genFiles("genversions", "GenVersions.scala")(GenVersions.genScalaTestVersions),
     sourceGenerators in Compile <+=
       (baseDirectory, sourceManaged in Compile, version, scalaVersion) map genFiles("gensafestyles", "GenSafeStyles.scala")(GenSafeStyles.genMain),
     scalatestDocSourcesSetting,
     sourceGenerators in Compile += {
       Def.task{
         ScalaTestGenResourcesJVM.genResources((sourceManaged in Compile).value / "scala" / "org" / "scalatest", version.value, scalaVersion.value) ++
         ScalaTestGenResourcesJVM.genFailureMessages((sourceManaged in Compile).value / "scala" / "org" / "scalatest", version.value, scalaVersion.value)
       }.taskValue
     },
     docTaskSetting
   ).settings(osgiSettings: _*).settings(
      OsgiKeys.exportPackage := Seq(
        "org.scalatest",
        "org.scalatest.concurrent",
        "org.scalatest.enablers",
        "org.scalatest.events",
        "org.scalatest.exceptions",
        "org.scalatest.fixture",
        "org.scalatest.junit",
        "org.scalatest.matchers",
        "org.scalatest.mock",
        "org.scalatest.path",
        "org.scalatest.prop",
        "org.scalatest.selenium",
        "org.scalatest.tags",
        "org.scalatest.tagobjects",
        "org.scalatest.testng",
        "org.scalatest.time",
        "org.scalatest.tools",
        "org.scalatest.verb",
        "org.scalatest.words"
      ),
      OsgiKeys.importPackage := Seq(
        "org.scalatest.*",
        "org.scalactic.*",
        "scala.util.parsing.*;version=\"$<range;[==,=+);$<replace;1.0.4;-;.>>\"",
        "scala.xml.*;version=\"$<range;[==,=+);$<replace;1.0.4;-;.>>\"",
        "scala.*;version=\"$<range;[==,=+);$<replace;"+scalaBinaryVersion.value+";-;.>>\"",
        "*;resolution:=optional"
      ),
      OsgiKeys.additionalHeaders:= Map(
        "Bundle-Name" -> "ScalaTest",
        "Bundle-Description" -> "ScalaTest is an open-source test framework for the Java Platform designed to increase your productivity by letting you write fewer lines of test code that more clearly reveal your intent.",
        "Bundle-DocURL" -> "http://www.scalatest.org/",
        "Bundle-Vendor" -> "Artima, Inc.",
        "Main-Class" -> "org.scalatest.tools.Runner"
      )
   ).dependsOn(scalacticMacro % "compile-internal, test-internal", scalactic)
    .aggregate(LocalProject("scalatest-test"))

  lazy val scalatestCoreJS = Project("scalatestCoreJS", file("scalatest-core.js"))
    .settings(sharedSettings: _*)
    .settings(
      projectTitle := "ScalaTest",
      organization := "org.scalatest",
      moduleName := "scalatest-core",
      initialCommands in console := """|import org.scalatest._
                                      |import org.scalactic._
                                      |import Matchers._""".stripMargin,
      ivyXML :=
        <dependency org="org.eclipse.jetty.orbit" name="javax.servlet" rev="3.0.0.v201112011016">
          <artifact name="javax.servlet" type="orbit" ext="jar"/>
        </dependency>,
      scalacOptions ++= Seq("-P:scalajs:mapSourceURI:" + scalatestAll.base.toURI + "->https://raw.githubusercontent.com/scalatest/scalatest/v" + version.value + "/"),
      libraryDependencies ++= scalatestJSLibraryDependencies,
      libraryDependencies += "org.scalacheck" %%% "scalacheck" % scalacheckVersion % "optional",
      jsDependencies += RuntimeDOM % "test",
      sourceGenerators in Compile += {
        Def.task {
          GenScalaTestJS.genHtml((sourceManaged in Compile).value, version.value, scalaVersion.value)

          GenScalaTestJS.genScala((sourceManaged in Compile).value / "scala", version.value, scalaVersion.value) ++
          GenVersions.genScalaTestVersions((sourceManaged in Compile).value / "scala" / "org" / "scalatest", version.value, scalaVersion.value) ++
          GenScalaTestJS.genJava((sourceManaged in Compile).value / "java", version.value, scalaVersion.value) ++
          ScalaTestGenResourcesJSVM.genResources((sourceManaged in Compile).value / "scala" / "org" / "scalatest", version.value, scalaVersion.value) ++
          ScalaTestGenResourcesJSVM.genFailureMessages((sourceManaged in Compile).value / "scala" / "org" / "scalatest", version.value, scalaVersion.value)
        }.taskValue
      },
      genSafeStylesTask,
      genGenTask,
      genTablesTask,
      sourceGenerators in Compile <+=
        (baseDirectory, sourceManaged in Compile, version, scalaVersion) map genFiles("gengen", "GenGen.scala")(GenGen.genMain),
      sourceGenerators in Compile <+=
        (baseDirectory, sourceManaged in Compile, version, scalaVersion) map genFiles("gentables", "GenTable.scala")(GenTable.genMainForScalaJS),
      sourceGenerators in Compile <+=
        (baseDirectory, sourceManaged in Compile, version, scalaVersion) map genFiles("gensafestyles", "GenSafeStyles.scala")(GenSafeStyles.genMainForScalaJS),
      /*genMustMatchersTask,
      genGenTask,
      genTablesTask,
      genCodeTask,
      genCompatibleClassesTask,

      sourceGenerators in Compile <+=
        (baseDirectory, sourceManaged in Compile, version, scalaVersion) map genFiles("gencompcls", "GenCompatibleClasses.scala")(GenCompatibleClasses.genMain),
      sourceGenerators in Compile <+=
        (baseDirectory, sourceManaged in Compile, version, scalaVersion) map genFiles("genversions", "GenVersions.scala")(GenVersions.genScalaTestVersions),*/
      //unmanagedResourceDirectories in Compile <+= sourceManaged( _ / "resources" ),
      scalatestJSDocTaskSetting
    ).settings(osgiSettings: _*).settings(
      OsgiKeys.exportPackage := Seq(
        "org.scalatest",
        "org.scalatest.concurrent",
        "org.scalatest.enablers",
        "org.scalatest.events",
        "org.scalatest.exceptions",
        "org.scalatest.fixture",
        "org.scalatest.junit",
        "org.scalatest.matchers",
        "org.scalatest.mock",
        "org.scalatest.path",
        "org.scalatest.prop",
        "org.scalatest.selenium",
        "org.scalatest.tags",
        "org.scalatest.tagobjects",
        "org.scalatest.testng",
        "org.scalatest.time",
        "org.scalatest.tools",
        "org.scalatest.verb",
        "org.scalatest.words"
      ),
      OsgiKeys.importPackage := Seq(
        "org.scalatest.*",
        "org.scalactic.*",
        "scala.util.parsing.*;version=\"$<range;[==,=+);$<replace;1.0.4;-;.>>\"",
        "scala.xml.*;version=\"$<range;[==,=+);$<replace;1.0.4;-;.>>\"",
        "scala.*;version=\"$<range;[==,=+);$<replace;"+scalaBinaryVersion.value+";-;.>>\"",
        "*;resolution:=optional"
      ),
      OsgiKeys.additionalHeaders:= Map(
        "Bundle-Name" -> "ScalaTest",
        "Bundle-Description" -> "ScalaTest.js is an open-source test framework for the Javascript Platform designed to increase your productivity by letting you write fewer lines of test code that more clearly reveal your intent.",
        "Bundle-DocURL" -> "http://www.scalatest.org/",
        "Bundle-Vendor" -> "Artima, Inc.",
        "Main-Class" -> "org.scalatest.tools.Runner"
      )
    ).dependsOn(scalacticMacroJS % "compile-internal, test-internal", scalacticJS).aggregate(LocalProject("scalatestTestJS")).enablePlugins(ScalaJSPlugin)

  def listAllFiles(folder: File): Seq[File] = {
    try {
      val files = folder.listFiles.filter(_.isFile)
      val subdirs = folder.listFiles.filter(_.isDirectory)
      files ++ subdirs.flatMap(listAllFiles)
    }
    catch {
      case t: Throwable =>
        println("###error folder: " + folder.getAbsoluteFile.getName)
        throw t
    }
  }

  lazy val scalatest = Project("scalatest", file("scalatest"))
    .settings(sharedSettings: _*)
    .settings(scalatestDocSettings: _*)
    .settings(
      projectTitle := "ScalaTest",
      organization := "org.scalatest",
      moduleName := "scalatest",
      initialCommands in console := """|import org.scalatest._
                                      |import org.scalactic._
                                      |import Matchers._""".stripMargin,
      ivyXML :=
        <dependency org="org.eclipse.jetty.orbit" name="javax.servlet" rev="3.0.0.v201112011016">
          <artifact name="javax.servlet" type="orbit" ext="jar"/>
        </dependency>,
      libraryDependencies ++= crossBuildLibraryDependencies(scalaVersion.value),
      libraryDependencies ++= scalatestLibraryDependencies,
      libraryDependencies ++=
        Seq(
          "junit" % "junit" % junitVersion % "optional",
          "org.testng" % "testng" % testNGVersion % "optional",
          "com.google.inject" % "guice" % guiceVersion % "optional",
          "org.easymock" % "easymockclassextension" % easyMockVersion % "optional",
          "org.jmock" % "jmock-legacy" % jMockVersion % "optional",
          "org.mockito" % "mockito-all" % mockitoVersion % "optional"
        ),
      compile in Compile <<= compile in Compile dependsOn (
        // Compile other modules first, as some we'll need to copy from their src_managed folder
        compile in Compile in scalatestCore,
        compile in Compile in scalatestFeatureSpec
      ),
      sourceGenerators in Compile += Def.task {
        val scalaTargetDir = (sourceManaged in Compile).value / "scala"
        val javaTargetDir = (sourceManaged in Compile).value / "java"

        // scalatest-core
        IO.copyDirectory(file("scalatest-core/src/main/scala"), scalaTargetDir, true)
        IO.copyDirectory(file("scalatest-core/src/main/java"), javaTargetDir, true)
        IO.copyDirectory(file("scalatest-core/target/scala-" + scalaBinaryVersion.value + "/src_managed/main/scala"), scalaTargetDir, true)

        // scalatest-featurespec
        IO.copyDirectory(file("scalatest-featurespec/src/main/scala"), scalaTargetDir, true)

        // scalatest-junit
        IO.copyDirectory(file("scalatest-junit/src/main/scala"), scalaTargetDir, true)

        // scalatest-testng
        IO.copyDirectory(file("scalatest-testng/src/main/scala"), scalaTargetDir, true)

        // scalatest-easymock
        IO.copyDirectory(file("scalatest-easymock/src/main/scala"), scalaTargetDir, true)

        // scalatest-jmock
        IO.copyDirectory(file("scalatest-jmock/src/main/scala"), scalaTargetDir, true)

        // scalatest-mockito
        IO.copyDirectory(file("scalatest-mockito/src/main/scala"), scalaTargetDir, true)

        listAllFiles(scalaTargetDir) ++ listAllFiles(javaTargetDir)
      }.taskValue,
      resourceGenerators in Compile += Def.task {
        val resourcesTargetDir = (resourceManaged in Compile).value

        IO.copyDirectory(file("scalatest-core/src/main/resources"), resourcesTargetDir, true)

        listAllFiles(resourcesTargetDir)
      }.taskValue
    ).dependsOn(scalacticMacro % "compile-internal, test-internal", scalactic)

  lazy val scalatestJS = Project("scalatestJS", file("scalatest.js"))
    .settings(sharedSettings: _*)
    .settings(
      projectTitle := "ScalaTest",
      organization := "org.scalatest",
      moduleName := "scalatest-core",
      initialCommands in console := """|import org.scalatest._
                                      |import org.scalactic._
                                      |import Matchers._""".stripMargin,
      ivyXML :=
        <dependency org="org.eclipse.jetty.orbit" name="javax.servlet" rev="3.0.0.v201112011016">
          <artifact name="javax.servlet" type="orbit" ext="jar"/>
        </dependency>,
      scalacOptions ++= Seq("-P:scalajs:mapSourceURI:" + scalatestAll.base.toURI + "->https://raw.githubusercontent.com/scalatest/scalatest/v" + version.value + "/"),
      libraryDependencies ++= scalatestJSLibraryDependencies,
      libraryDependencies += "org.scalacheck" %%% "scalacheck" % scalacheckVersion % "optional",
      jsDependencies += RuntimeDOM % "test",
      compile in Compile <<= compile in Compile dependsOn (
        // Compile other modules first, as some we'll need to copy from their src_managed folder
        compile in Compile in scalatestCoreJS,
        compile in Compile in scalatestFeatureSpecJS
      ),
      sourceGenerators in Compile += Def.task {
        val scalaTargetDir = (sourceManaged in Compile).value / "scala"
        val javaTargetDir = (sourceManaged in Compile).value / "java"
        val htmlTargetDir = (sourceManaged in Compile).value / "html"

        IO.copyDirectory(file("scalatest-core.js/src/main/scala"), scalaTargetDir, true)
        IO.copyDirectory(file("scalatest-core.js/target/scala-" + scalaBinaryVersion.value + "/src_managed/main/scala"), scalaTargetDir, true)
        IO.copyDirectory(file("scalatest-core.js/target/scala-" + scalaBinaryVersion.value + "/src_managed/main/java"), javaTargetDir, true)
        IO.copyDirectory(file("scalatest-core.js/target/scala-" + scalaBinaryVersion.value + "/src_managed/main/html"), htmlTargetDir, true)

        listAllFiles(scalaTargetDir) ++ listAllFiles(javaTargetDir)
      }.taskValue,
      scalatestJSDocTaskSetting
    ).settings(osgiSettings: _*).settings(
    OsgiKeys.exportPackage := Seq(
      "org.scalatest",
      "org.scalatest.concurrent",
      "org.scalatest.enablers",
      "org.scalatest.events",
      "org.scalatest.exceptions",
      "org.scalatest.fixture",
      "org.scalatest.junit",
      "org.scalatest.matchers",
      "org.scalatest.mock",
      "org.scalatest.path",
      "org.scalatest.prop",
      "org.scalatest.selenium",
      "org.scalatest.tags",
      "org.scalatest.tagobjects",
      "org.scalatest.testng",
      "org.scalatest.time",
      "org.scalatest.tools",
      "org.scalatest.verb",
      "org.scalatest.words"
    ),
    OsgiKeys.importPackage := Seq(
      "org.scalatest.*",
      "org.scalactic.*",
      "scala.util.parsing.*;version=\"$<range;[==,=+);$<replace;1.0.4;-;.>>\"",
      "scala.xml.*;version=\"$<range;[==,=+);$<replace;1.0.4;-;.>>\"",
      "scala.*;version=\"$<range;[==,=+);$<replace;"+scalaBinaryVersion.value+";-;.>>\"",
      "*;resolution:=optional"
    ),
    OsgiKeys.additionalHeaders:= Map(
      "Bundle-Name" -> "ScalaTest",
      "Bundle-Description" -> "ScalaTest.js is an open-source test framework for the Javascript Platform designed to increase your productivity by letting you write fewer lines of test code that more clearly reveal your intent.",
      "Bundle-DocURL" -> "http://www.scalatest.org/",
      "Bundle-Vendor" -> "Artima, Inc.",
      "Main-Class" -> "org.scalatest.tools.Runner"
    )
  ).dependsOn(scalacticMacroJS % "compile-internal, test-internal", scalacticJS).enablePlugins(ScalaJSPlugin)

  lazy val root = Project("root", file("."))
    .settings(sharedSettings: _*)
    .aggregate(
      scalactic,
      scalatestCore,
      scalatestMatchersCore,
      scalatestMatchers,
      scalatestMustMatchers,
      scalatestMustMatchers,
      scalatestFeatureSpec,
      scalatestJUnit,
      scalatestTestNG,
      scalatestEasyMock,
      scalatestJMock,
      scalatestMockito,
      scalatestSelenium
    )

  lazy val rootJS = Project("js", file("js"))
    .settings(sharedSettings: _*)
    .aggregate(
      scalacticJS,
      scalatestCoreJS,
      scalatestMatchersCoreJS,
      scalatestMatchersJS,
      scalatestMustMatchersJS,
      scalatestMustMatchersJS,
      scalatestFeatureSpecJS
    )

  lazy val scalatestAll = Project("scalatestAll", file("scalatest-all"))
    .settings(sharedSettings: _*)
    .settings(
      projectTitle := "ScalaTest All",
      name := "scalatest-all",
      organization := "org.scalatest",
      libraryDependencies ++= crossBuildLibraryDependencies(scalaVersion.value),
      libraryDependencies ++= scalatestLibraryDependencies,
      libraryDependencies ++=
        Seq(
          "junit" % "junit" % junitVersion % "optional",
          "org.testng" % "testng" % testNGVersion % "optional",
          "com.google.inject" % "guice" % guiceVersion % "optional",
          "org.easymock" % "easymockclassextension" % easyMockVersion % "optional",
          "org.jmock" % "jmock-legacy" % jMockVersion % "optional",
          "org.mockito" % "mockito-all" % mockitoVersion % "optional"
        ),
      // include the scalactic classes and resources in the jar
      mappings in (Compile, packageBin) ++= mappings.in(scalactic, Compile, packageBin).value,
      // include the scalactic sources in the source jar
      mappings in (Compile, packageSrc) ++= mappings.in(scalactic, Compile, packageSrc).value,
      compile in Compile <<= compile in Compile dependsOn (
        // Build scalactic and scalatest first
        compile in Compile in scalactic,
        compile in Compile in scalatest
        ),
      sourceGenerators in Compile += Def.task {
        val scalaTargetDir = (sourceManaged in Compile).value / "scala"
        val javaTargetDir = (sourceManaged in Compile).value / "java"

        IO.copyDirectory(file("scalatest/target/scala-" + scalaBinaryVersion.value + "/src_managed/main/scala"), scalaTargetDir, true)
        IO.copyDirectory(file("scalatest/target/scala-" + scalaBinaryVersion.value + "/src_managed/main/java"), javaTargetDir, true)

        listAllFiles(scalaTargetDir) ++ listAllFiles(javaTargetDir)
      }.taskValue,
      resourceGenerators in Compile += Def.task {
        val resourcesTargetDir = (resourceManaged in Compile).value

        IO.copyDirectory(file("scalatest-core/src/main/resources"), resourcesTargetDir, true)

        listAllFiles(resourcesTargetDir)
      }.taskValue
    ).settings(osgiSettings: _*).settings(
      OsgiKeys.exportPackage := Seq(
        "org.scalatest",
        "org.scalatest.concurrent",
        "org.scalatest.enablers",
        "org.scalatest.events",
        "org.scalatest.exceptions",
        "org.scalatest.fixture",
        "org.scalatest.junit",
        "org.scalatest.matchers",
        "org.scalatest.mock",
        "org.scalatest.path",
        "org.scalatest.prop",
        "org.scalatest.selenium",
        "org.scalatest.tags",
        "org.scalatest.tagobjects",
        "org.scalatest.testng",
        "org.scalatest.time",
        "org.scalatest.tools",
        "org.scalatest.verb",
        "org.scalatest.words",
        "org.scalactic",
        "org.scalactic.anyvals",
        "org.scalactic.exceptions"
      ),
      OsgiKeys.importPackage := Seq(
        "org.scalatest.*",
        "org.scalactic.*",
        "scala.util.parsing.*;version=\"$<range;[==,=+);$<replace;1.0.4;-;.>>\"",
        "scala.xml.*;version=\"$<range;[==,=+);$<replace;1.0.4;-;.>>\"",
        "scala.*;version=\"$<range;[==,=+);$<replace;"+scalaBinaryVersion.value+";-;.>>\"",
        "*;resolution:=optional"
      ),
      OsgiKeys.additionalHeaders:= Map(
        "Bundle-Name" -> "ScalaTest",
        "Bundle-Description" -> "ScalaTest is an open-source test framework for the Java Platform designed to increase your productivity by letting you write fewer lines of test code that more clearly reveal your intent.",
        "Bundle-DocURL" -> "http://www.scalatest.org/",
        "Bundle-Vendor" -> "Artima, Inc.",
        "Main-Class" -> "org.scalatest.tools.Runner"
      )
    ).dependsOn(scalacticMacro % "compile-internal, test-internal", scalactic % "compile-internal")

  lazy val scalatestAllJS = Project("scalatestAllJS", file("scalatest-all.js"))
    .settings(sharedSettings: _*)
    .settings(
      projectTitle := "ScalaTest All",
      name := "scalatest-all",
      organization := "org.scalatest",
      moduleName := "scalatest-all",
      libraryDependencies ++= crossBuildLibraryDependencies(scalaVersion.value),
      libraryDependencies ++= scalatestJSLibraryDependencies,
      // include the scalactic classes and resources in the jar
      mappings in (Compile, packageBin) ++= mappings.in(scalacticJS, Compile, packageBin).value,
      // include the scalactic sources in the source jar
      mappings in (Compile, packageSrc) ++= mappings.in(scalacticJS, Compile, packageSrc).value,
      compile in Compile <<= compile in Compile dependsOn (
        // Build scalactic and scalatest first
        compile in Compile in scalacticJS,
        compile in Compile in scalatestJS
        ),
      sourceGenerators in Compile += Def.task {
        val scalaTargetDir = (sourceManaged in Compile).value / "scala"
        val javaTargetDir = (sourceManaged in Compile).value / "java"

        IO.copyDirectory(file("scalatest.js/target/scala-" + scalaBinaryVersion.value + "/src_managed/main/scala"), scalaTargetDir, true)
        IO.copyDirectory(file("scalatest.js/target/scala-" + scalaBinaryVersion.value + "/src_managed/main/java"), javaTargetDir, true)

        listAllFiles(scalaTargetDir) ++ listAllFiles(javaTargetDir)
      }.taskValue
    ).settings(osgiSettings: _*).settings(
      OsgiKeys.exportPackage := Seq(
        "org.scalatest",
        "org.scalatest.concurrent",
        "org.scalatest.enablers",
        "org.scalatest.events",
        "org.scalatest.exceptions",
        "org.scalatest.fixture",
        "org.scalatest.junit",
        "org.scalatest.matchers",
        "org.scalatest.mock",
        "org.scalatest.path",
        "org.scalatest.prop",
        "org.scalatest.selenium",
        "org.scalatest.tags",
        "org.scalatest.tagobjects",
        "org.scalatest.testng",
        "org.scalatest.time",
        "org.scalatest.tools",
        "org.scalatest.verb",
        "org.scalatest.words",
        "org.scalactic",
        "org.scalactic.anyvals",
        "org.scalactic.exceptions"
      ),
      OsgiKeys.importPackage := Seq(
        "org.scalatest.*",
        "org.scalactic.*",
        "scala.util.parsing.*;version=\"$<range;[==,=+);$<replace;1.0.4;-;.>>\"",
        "scala.xml.*;version=\"$<range;[==,=+);$<replace;1.0.4;-;.>>\"",
        "scala.*;version=\"$<range;[==,=+);$<replace;"+scalaBinaryVersion.value+";-;.>>\"",
        "*;resolution:=optional"
      ),
      OsgiKeys.additionalHeaders:= Map(
        "Bundle-Name" -> "ScalaTest",
        "Bundle-Description" -> "ScalaTest is an open-source test framework for the Java Platform designed to increase your productivity by letting you write fewer lines of test code that more clearly reveal your intent.",
        "Bundle-DocURL" -> "http://www.scalatest.org/",
        "Bundle-Vendor" -> "Artima, Inc.",
        "Main-Class" -> "org.scalatest.tools.Runner"
      )
    ).dependsOn(scalacticMacroJS % "compile-internal, test-internal", scalacticJS % "compile-internal").enablePlugins(ScalaJSPlugin)


  lazy val examples = Project("examples", file("examples"), delegates = scalatestCore :: Nil)
    .settings(
      scalaVersion := buildScalaVersion,
      libraryDependencies += scalacheckDependency("compile")
    ).dependsOn(scalacticMacro, scalactic, scalatestCore)

  def genFiles(name: String, generatorSource: String)(gen: (File, String, String) => Unit)(basedir: File, outDir: File, theVersion: String, theScalaVersion: String): Seq[File] = {
    val tdir = outDir / "scala" / name
    val jdir = outDir / "java" / name
    val genSource = basedir / "project" / generatorSource

    def results = (tdir ** "*.scala").get ++ (jdir ** "*.java").get
    if (results.isEmpty || results.exists(_.lastModified < genSource.lastModified)) {
      tdir.mkdirs()
      gen(tdir, theVersion, theScalaVersion)
    }
    results
  }

  def genJavaFiles(name: String, generatorSource: String)(gen: (File, String, String) => Unit)(basedir: File, outDir: File, theVersion: String, theScalaVersion: String): Seq[File] = {
    val tdir = outDir / "java" / name
    val genSource = basedir / "project" / generatorSource

    def results = (tdir ** "*.java").get
    if (results.isEmpty || results.exists(_.lastModified < genSource.lastModified)) {
      tdir.mkdirs()
      gen(tdir, theVersion, theScalaVersion)
    }
    results
  }

  val genGen = TaskKey[Unit]("gengen", "Generate Property Checks")
  val genGenTask = genGen <<= (sourceManaged in Compile, sourceManaged in Test, name, version, scalaVersion) map { (mainTargetDir: File, testTargetDir: File, projName: String, theVersion: String, theScalaVersion: String) =>
    GenGen.genMain(new File(mainTargetDir, "scala/gengen"), theVersion, theScalaVersion)
  }

  val genTables = TaskKey[Unit]("gentables", "Generate Tables")
  val genTablesTask = genTables <<= (sourceManaged in Compile, sourceManaged in Test, name, version, scalaVersion) map { (mainTargetDir: File, testTargetDir: File, projName: String, theVersion: String, theScalaVersion: String) =>
    GenTable.genMain(new File(mainTargetDir, "scala/gentables"), theVersion, theScalaVersion)
  }

  val genCompatibleClasses = TaskKey[Unit]("gencompcls", "Generate Compatible Classes for Java 6 & 7")
  val genCompatibleClassesTask = genCompatibleClasses <<= (sourceManaged in Compile, sourceManaged in Test, version, scalaVersion) map { (mainTargetDir: File, testTargetDir: File, theVersion: String, theScalaVersion: String) =>
    GenCompatibleClasses.genMain(new File(mainTargetDir, "scala/gencompclass"), theVersion, theScalaVersion)
  }

  val genVersions = TaskKey[Unit]("genversions", "Generate Versions object")
  val genVersionsTask = genVersions <<= (sourceManaged in Compile, sourceManaged in Test, version, scalaVersion) map { (mainTargetDir: File, testTargetDir: File, theVersion: String, theScalaVersion: String) =>
    GenVersions.genScalaTestVersions(new File(mainTargetDir, "scala/gencompclass"), theVersion, theScalaVersion)
  }

  val genCode = TaskKey[Unit]("gencode", "Generate Code, includes Must Matchers and They Word tests.")
  val genCodeTask = genCode <<= (sourceManaged in Compile, sourceManaged in Test, version, scalaVersion) map { (mainTargetDir: File, testTargetDir: File, theVersion: String, theScalaVersion: String) =>
    GenGen.genMain(new File(mainTargetDir, "scala/gengen"), theVersion, theScalaVersion)
    GenTable.genMain(new File(mainTargetDir, "scala/gentables"), theVersion, theScalaVersion)
    GenMatchers.genMain(new File(mainTargetDir, "scala/genmatchers"), theVersion, theScalaVersion)
    GenFactories.genMain(new File(mainTargetDir, "scala/genfactories"), theVersion, theScalaVersion)
  }

  val genSafeStyles = TaskKey[Unit]("gensafestyles", "Generate safe style traits.")
  val genSafeStylesTask = genSafeStyles <<= (sourceManaged in Compile, sourceManaged in Test, version, scalaVersion) map { (mainTargetDir: File, testTargetDir: File, theVersion: String, theScalaVersion: String) =>
    GenSafeStyles.genMain(new File(mainTargetDir, "scala/gensafestyles"), theVersion, theScalaVersion)
  }
}
// set scalacOptions in (Compile, console) += "-Xlog-implicits"
// set scalacOptions in (Compile, console) += "-Xlog-implicits"
// set scalacOptions in (Compile, console) += "-Xlog-implicits"
// set scalacOptions in (Compile, console) += "-nowarn"
