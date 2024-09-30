import _root_.controllers.*
import actors.*
import akka.actor.typed.ActorRef
import akka.actor.typed.scaladsl.adapter.*
import com.jackleow.presentation.tokenizing.{MappedKeywordsTokenizer, NormalizedWordsTokenizer}
import play.api.*
import play.api.ApplicationLoader.Context
import play.api.http.{HttpErrorHandler, JsonHttpErrorHandler}
import play.api.routing.Router
import router.Routes

object PresentationServiceApplicationLoader {
  private class Components(context: Context)
      extends BuiltInComponentsFromContext(context)
      with NoHttpFiltersComponents
      with AssetsComponents {
    override lazy val httpErrorHandler: HttpErrorHandler =
      new JsonHttpErrorHandler(environment, devContext.map(_.sourceMapper))

    private val chatMessages: ActorRef[ChatMessageBroadcaster.Command] =
      actorSystem.spawn(ChatMessageBroadcaster(), "chat")
    private val rejectedMessages: ActorRef[ChatMessageBroadcaster.Command] =
      actorSystem.spawn(ChatMessageBroadcaster(), "rejected")
    private val languagePoll: ActorRef[SendersByTokenCounter.Command] =
      actorSystem.spawn(
        SendersByTokenCounter(
          extractTokens = MappedKeywordsTokenizer(
            configuration.get[Map[String, String]]("presentation.languagePoll.languageByKeyword")
          ),
          tokensPerSender = configuration.get[Int]("presentation.languagePoll.maxVotesPerPerson"),
          chatMessages, rejectedMessages
        ),
        "language-poll"
      )
    private val wordCloud: ActorRef[SendersByTokenCounter.Command] =
      actorSystem.spawn(
        SendersByTokenCounter(
          extractTokens = NormalizedWordsTokenizer(
            configuration.get[Seq[String]]("presentation.wordCloud.stopWords").toSet,
            configuration.get[Int]("presentation.wordCloud.minWordLength"),
            configuration.get[Int]("presentation.wordCloud.maxWordLength")
          ),
          tokensPerSender = configuration.get[Int]("presentation.wordCloud.maxWordsPerPerson"),
          chatMessages, rejectedMessages
        ),
        "word-cloud"
      )
    private val chattiest: ActorRef[MessagesBySenderCounter.Command] =
      actorSystem.spawn(
        MessagesBySenderCounter(chatMessages), "chattiest"
      )
    private val questions: ActorRef[ModeratedTextCollector.Command] =
      actorSystem.spawn(
        ModeratedTextCollector(chatMessages, rejectedMessages), "question"
      )
    private val transcriptions: ActorRef[TranscriptionBroadcaster.Command] =
      actorSystem.spawn(
        TranscriptionBroadcaster(), "transcriptions"
      )
    private val mainController: MainController = new MainController(
      chatMessages = chatMessages,
      rejectedMessages = rejectedMessages,
      languagePoll = languagePoll,
      wordCloud = wordCloud,
      chattiest = chattiest,
      questions = questions,
      transcriptions = transcriptions,
      controllerComponents
    )(using actorSystem)
    override val router: Router =
      new Routes(httpErrorHandler, assets, mainController)
  }
}
class PresentationServiceApplicationLoader extends ApplicationLoader {
  import PresentationServiceApplicationLoader.*

  def load(context: Context): Application = {
    LoggerConfigurator(context.environment.classLoader).foreach {
      _.configure(context.environment, context.initialConfiguration, Map.empty)
    }
    new Components(context).application
  }
}
