import _root_.controllers.*
import play.api.*
import play.api.ApplicationLoader.Context
import play.api.mvc.EssentialFilter
import play.api.routing.Router
import play.filters.HttpFiltersComponents
import router.Routes

object PresentationServiceApplicationLoader {
  private class Components(context: Context)
      extends BuiltInComponentsFromContext(context)
      with HttpFiltersComponents
      with AssetsComponents {
    // Get rid of Content-Security-Policy header
    override lazy val httpFilters: Seq[EssentialFilter] = Seq()

    private lazy val mainController: MainController = new MainController(
      controllerComponents, configuration
    )(actorSystem, materializer)
    override lazy val router: Router = new Routes(
      httpErrorHandler, assets, mainController
    )
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