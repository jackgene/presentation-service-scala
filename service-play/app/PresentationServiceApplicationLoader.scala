import _root_.controllers.*
import play.api.*
import play.api.ApplicationLoader.Context
import play.api.routing.Router
import play.filters.HttpFiltersComponents
import router.Routes

object PresentationServiceApplicationLoader {
  private class Components(context: Context)
      extends BuiltInComponentsFromContext(context)
      with HttpFiltersComponents
      with AssetsComponents {
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

  def load(context: Context): Application = new Components(context).application
}
