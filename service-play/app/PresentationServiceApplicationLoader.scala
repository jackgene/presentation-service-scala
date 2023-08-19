import _root_.controllers.*
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

    private lazy val mainController: MainController = new MainController(
      controllerComponents, configuration
    )(actorSystem, materializer)
    override lazy val router: Router =
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
