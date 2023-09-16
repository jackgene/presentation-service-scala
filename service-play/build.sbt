name := "presentation-service-play"

scalacOptions ++= Seq("-no-indent")

libraryDependencies += "com.typesafe.akka" %% "akka-stream-kafka" % "3.0.1"
libraryDependencies += "com.typesafe.akka" %% "akka-stream-typed" % "2.6.21"

PlayKeys.devSettings += "play.server.http.port" -> "8973"
PlayKeys.devSettings += "play.server.http.idleTimeout" -> "900s"

// Deck Elm app
val elmMakeDeck = taskKey[Seq[File]]("elm-make-deck")

elmMakeDeck := {
  import scala.sys.process.*
  import com.typesafe.sbt.web.LineBasedProblem
  import play.sbt.PlayExceptions.CompilationException

  val baseDir: File = file(baseDirectory.value.getName)
  val output: File = baseDir / "public" / "html" / "deck.html"
  val debugFlag: String = "--debug"
  var outErrLines: List[String] = Nil
  var srcFilePath: Option[String] = None
  var lineNum: Option[String] = None
  var offset: Option[String] = None
  Seq(
    "bash", "-c",
    "elm-make " +
      (
        ((baseDir / "app" / "assets" / "javascripts" / "Deck") ** "*.elm").get ++
        ((baseDir / "app" / "assets" / "javascripts" / "SyntaxHighlight") ** "*.elm").get ++
        ((baseDir / "app" / "assets" / "javascripts" / "WordCloud") ** "*.elm").get
      ).mkString(" ") +
      s" --output ${output} " +
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
      //      output +: (file("elm-stuff/build-artifacts") ** "*").get()
      Seq(output)

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
val elmMakeModerator = taskKey[Seq[File]]("elm-make-moderator")

elmMakeModerator := {
  import scala.sys.process.*
  import com.typesafe.sbt.web.LineBasedProblem
  import play.sbt.PlayExceptions.CompilationException

  val baseDir: File = file(baseDirectory.value.getName)
  val output: File = baseDir / "public" / "html" / "moderator.html"
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
    ((baseDir / "app" / "assets" / "javascripts" / "Moderator") ** "*.elm").get.mkString(" ") +
    s" --output ${output} " +
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
//      output +: (file("elm-stuff/build-artifacts") ** "*").get()
      Seq(output)

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
  import scala.sys.process.*
  import com.typesafe.sbt.web.LineBasedProblem
  import play.sbt.PlayExceptions.CompilationException

  val baseDir: File = file(baseDirectory.value.getName)
  val output: File = baseDir / "public" / "html" / "transcriber.html"
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
      ((baseDir / "app" / "assets" / "javascripts" / "Transcriber") ** "*.elm").get.mkString(" ") +
      s" --output ${output} " +
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
      //      output +: (file("elm-stuff/build-artifacts") ** "*").get()
      Seq(output)

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
