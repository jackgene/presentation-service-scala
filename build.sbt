name := "presentation-service"
organization := "com.jackleow"

version := "1.0-SNAPSHOT"

lazy val root = (project in file(".")).enablePlugins(PlayScala)

scalaVersion := "2.13.8"

scalacOptions ++= Seq(
  "-Xsource:3",
  "-deprecation", "-feature",
  "-Wdead-code",
  "-Wextra-implicit",
  "-Wmacros:after",
  "-Wnumeric-widen",
  "-Woctal-literal",
  "-Wunused:imports,patvars,privates,locals,explicits,implicits,params,linted",
  "-Wvalue-discard",
  "-Xlint:adapted-args",
  "-Xlint:constant",
  "-Xlint:delayedinit-select",
  "-Xlint:eta-sam",
  "-Xlint:eta-zero",
  "-Xlint:implicit-not-found",
  "-Xlint:implicit-recursion",
  "-Xlint:inaccessible",
  "-Xlint:infer-any",
  "-Xlint:missing-interpolator",
  "-Xlint:nonlocal-return",
  "-Xlint:nullary-unit",
  "-Xlint:option-implicit",
  "-Xlint:package-object-classes",
  "-Xlint:poly-implicit-overload",
  "-Xlint:private-shadow",
  "-Xlint:serial",
  "-Xlint:stars-align",
  "-Xlint:type-parameter-shadow",
  "-Xlint:valpattern"
)

libraryDependencies += guice
libraryDependencies += ws
libraryDependencies += "com.google.inject" % "guice" % "5.1.0"
libraryDependencies += "com.google.inject.extensions" % "guice-assistedinject" % "5.1.0"
libraryDependencies += "org.scalatestplus" %% "scalacheck-1-14" % "3.1.1.0" % Test
libraryDependencies += "org.scalatestplus.play" %% "scalatestplus-play" % "5.1.0" % Test

PlayKeys.devSettings += "play.server.http.port" -> "8973"
PlayKeys.devSettings += "play.server.http.idleTimeout" -> "900s"

Test / fork := false

// Deck Elm app

val elmMakeDeck = taskKey[Seq[File]]("elm-make-deck")

elmMakeDeck := {
  import scala.sys.process._
  import com.typesafe.sbt.web.LineBasedProblem
  import play.sbt.PlayExceptions.CompilationException

  val outputPath: String = "public/html/deck.html"
  val debugFlag: String = "--debug"
  var outErrLines: List[String] = Nil
  var srcFilePath: Option[String] = None
  var lineNum: Option[String] = None
  var offset: Option[String] = None
  Seq(
    "bash", "-c",
    "elm-make " +
      (file("app/assets/javascripts/Deck") ** "*.elm").get.mkString(" ") +
      " " +
      (file("app/assets/javascripts/SyntaxHighlight") ** "*.elm").get.mkString(" ") +
      s" --output ${outputPath} " +
      s"--yes ${debugFlag} --warn"
  ).!(
    new ProcessLogger {
      override def out(s: => String): Unit = {
        streams.value.log.info(s)
        outErrLines = s :: outErrLines
      }

      override def err(s: => String): Unit = {
        streams.value.log.warn(s)
        val SrcFilePathExtractor = """-- [A-Z ]+ -+ (app/assets/javascripts/Deck/.+\.elm)""".r
        val LineNumExtractor = """([0-9]+)\|.*""".r
        val PosExtractor = """ *\^+ *""".r
        s match {
          case SrcFilePathExtractor(path: String) =>
            srcFilePath = srcFilePath orElse Some(path)
          case LineNumExtractor(num: String) =>
            lineNum = lineNum orElse Some(num)
          case PosExtractor() =>
            offset = offset orElse Some(s)
          case _ =>
        }
        outErrLines = s :: outErrLines
      }

      override def buffer[T](f: => T): T = f
    }
  ) match {
    case 0 =>
      streams.value.log.success("elm-make (for Deck) completed.")
      //      file(outputPath) +: (file("elm-stuff/build-artifacts") ** "*").get()
      Seq(file(outputPath))

    case 127 =>
      streams.value.log.warn("elm-make not found in PATH. Skipping Elm build.")
      Nil

    case _ =>
      throw CompilationException(
        new LineBasedProblem(
          message = outErrLines.reverse.mkString("\n"),
          severity = null,
          lineNumber = lineNum.map(_.toInt).getOrElse(0),
          characterOffset = offset.map(_.indexOf('^') - 2 - lineNum.map(_.length).getOrElse(0)).getOrElse(0),
          lineContent = "",
          source = file(srcFilePath.getOrElse(""))
        )
      )
  }
}

Assets / sourceGenerators += elmMakeDeck.taskValue

// Moderator Elm app
val elmMakeModerator = taskKey[Seq[File]]("elm-make-moedrator")

elmMakeModerator := {
  import scala.sys.process._
  import com.typesafe.sbt.web.LineBasedProblem
  import play.sbt.PlayExceptions.CompilationException

  val outputPath: String = "public/html/moderator.html"
  val debugFlag: String =
    if (sys.props.getOrElse("elm.debug", "false").toLowerCase != "true") ""
    else "--debug"
  var outErrLines: List[String] = Nil
  var srcFilePath: Option[String] = None
  var lineNum: Option[String] = None
  var offset: Option[String] = None
  Seq(
    "bash", "-c",
    "elm-make " +
    (file("app/assets/javascripts/Moderator") ** "*.elm").get.mkString(" ") +
    s" --output ${outputPath} " +
    s"--yes ${debugFlag} --warn"
  ).!(
    new ProcessLogger {
      override def out(s: => String): Unit = {
        streams.value.log.info(s)
        outErrLines = s :: outErrLines
      }

      override def err(s: => String): Unit = {
        streams.value.log.warn(s)
        val SrcFilePathExtractor = """-- [A-Z ]+ -+ (app/assets/javascripts/Moderator/.+\.elm)""".r
        val LineNumExtractor = """([0-9]+)\|.*""".r
        val PosExtractor = """ *\^+ *""".r
        s match {
          case SrcFilePathExtractor(path: String) =>
            srcFilePath = srcFilePath orElse Some(path)
          case LineNumExtractor(num: String) =>
            lineNum = lineNum orElse Some(num)
          case PosExtractor() =>
            offset = offset orElse Some(s)
          case _ =>
        }
        outErrLines = s :: outErrLines
      }

      override def buffer[T](f: => T): T = f
    }
  ) match {
    case 0 =>
      streams.value.log.success("elm-make (for Moderator) completed.")
//      file(outputPath) +: (file("elm-stuff/build-artifacts") ** "*").get()
      Seq(file(outputPath))

    case 127 =>
      streams.value.log.warn("elm-make not found in PATH. Skipping Elm build.")
      Nil

    case _ =>
      throw CompilationException(
        new LineBasedProblem(
          message = outErrLines.reverse.mkString("\n"),
          severity = null,
          lineNumber = lineNum.map(_.toInt).getOrElse(0),
          characterOffset = offset.map(_.indexOf('^') - 2 - lineNum.map(_.length).getOrElse(0)).getOrElse(0),
          lineContent = "",
          source = file(srcFilePath.getOrElse(""))
        )
      )
  }
}

Assets / sourceGenerators += elmMakeModerator.taskValue

// Transcriber Elm app

val elmMakeTranscriber = taskKey[Seq[File]]("elm-make-transcriber")

elmMakeTranscriber := {
  import scala.sys.process._
  import com.typesafe.sbt.web.LineBasedProblem
  import play.sbt.PlayExceptions.CompilationException

  val outputPath: String = "public/html/transcriber.html"
  val debugFlag: String =
    if (sys.props.getOrElse("elm.debug", "false").toLowerCase != "true") ""
    else "--debug"
  var outErrLines: List[String] = Nil
  var srcFilePath: Option[String] = None
  var lineNum: Option[String] = None
  var offset: Option[String] = None
  Seq(
    "bash", "-c",
    "elm-make " +
      (file("app/assets/javascripts/Transcriber") ** "*.elm").get.mkString(" ") +
      s" --output ${outputPath} " +
      s"--yes ${debugFlag} --warn"
  ).!(
    new ProcessLogger {
      override def out(s: => String): Unit = {
        streams.value.log.info(s)
        outErrLines = s :: outErrLines
      }

      override def err(s: => String): Unit = {
        streams.value.log.warn(s)
        val SrcFilePathExtractor = """-- [A-Z ]+ -+ (app/assets/javascripts/Transcriber/.+\.elm)""".r
        val LineNumExtractor = """([0-9]+)\|.*""".r
        val PosExtractor = """ *\^+ *""".r
        s match {
          case SrcFilePathExtractor(path: String) =>
            srcFilePath = srcFilePath orElse Some(path)
          case LineNumExtractor(num: String) =>
            lineNum = lineNum orElse Some(num)
          case PosExtractor() =>
            offset = offset orElse Some(s)
          case _ =>
        }
        outErrLines = s :: outErrLines
      }

      override def buffer[T](f: => T): T = f
    }
  ) match {
    case 0 =>
      streams.value.log.success("elm-make (for Transcriber) completed.")
      //      file(outputPath) +: (file("elm-stuff/build-artifacts") ** "*").get()
      Seq(file(outputPath))

    case 127 =>
      streams.value.log.warn("elm-make not found in PATH. Skipping Elm build.")
      Nil

    case _ =>
      throw CompilationException(
        new LineBasedProblem(
          message = outErrLines.reverse.mkString("\n"),
          severity = null,
          lineNumber = lineNum.map(_.toInt).getOrElse(0),
          characterOffset = offset.map(_.indexOf('^') - 2 - lineNum.map(_.length).getOrElse(0)).getOrElse(0),
          lineContent = "",
          source = file(srcFilePath.getOrElse(""))
        )
      )
  }
}

Assets / sourceGenerators += elmMakeTranscriber.taskValue

// Docker configuration
import com.typesafe.sbt.packager.docker.DockerChmodType
import com.typesafe.sbt.packager.docker.DockerPermissionStrategy

dockerChmodType := DockerChmodType.UserGroupWriteExecute
dockerPermissionStrategy := DockerPermissionStrategy.CopyChown
dockerBaseImage := "azul/zulu-openjdk:15-jre-headless-latest"
dockerExposedPorts := Seq(8973)
