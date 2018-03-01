package controllers

import javax.inject.Inject

import net.logstash.logback.marker.LogstashMarker
import play.api.{Logger, MarkerContext}
import play.api.http.{FileMimeTypes, HttpVerbs}
import play.api.i18n.{Langs, MessagesApi}
import play.api.mvc._

import scala.concurrent.{ExecutionContext, Future}

// A wrapped request for Account resources.
trait AccountRequestHeader
    extends MessagesRequestHeader
    with PreferredMessagesProvider
class AccountRequest[A](request: Request[A], val messagesApi: MessagesApi)
    extends WrappedRequest(request)
    with AccountRequestHeader

// Provides an implicit marker that will show the request in all logger statements.
trait RequestMarkerContext {
  import net.logstash.logback.marker.Markers

  private def marker(tuple: (String, Any)) = Markers.append(tuple._1, tuple._2)

  private implicit class RichLogstashMarker(marker1: LogstashMarker) {
    def &&(marker2: LogstashMarker): LogstashMarker = marker1.and(marker2)
  }

  implicit def requestHeaderToMarkerContext(
      implicit request: RequestHeader): MarkerContext = {
    MarkerContext {
      marker("id" -> request.id) && marker("host" -> request.host) && marker(
        "remoteAddress" -> request.remoteAddress)
    }
  }

}

// The action builder for the Post resource.
class AccountActionBuilder @Inject()(messagesApi: MessagesApi,
                                     playBodyParsers: PlayBodyParsers)(
    implicit val executionContext: ExecutionContext)
    extends ActionBuilder[AccountRequest, AnyContent]
    with RequestMarkerContext
    with HttpVerbs {

  val parser: BodyParser[AnyContent] = playBodyParsers.anyContent

  type AccountRequestBlock[A] = AccountRequest[A] => Future[Result]

  private val logger = Logger(this.getClass)

  override def invokeBlock[A](request: Request[A],
                              block: AccountRequestBlock[A]): Future[Result] = {
    // Convert to marker context and use request in block
    implicit val markerContext: MarkerContext = requestHeaderToMarkerContext(
      request)
    logger.trace(s"invokeBlock: ")

    val future = block(new AccountRequest(request, messagesApi))

    future.map { result =>
      request.method match {
        case GET | HEAD =>
          result.withHeaders("Cache-Control" -> s"max-age: 100")
        case other =>
          result
      }
    }
  }
}

// Packages up the component dependencies for the Account controller.
case class AccountControllerComponents @Inject()(
    accountActionBuilder: AccountActionBuilder,
    accountResourceHandler: AccountResourceHandler,
    actionBuilder: DefaultActionBuilder,
    parsers: PlayBodyParsers,
    messagesApi: MessagesApi,
    langs: Langs,
    fileMimeTypes: FileMimeTypes,
    executionContext: scala.concurrent.ExecutionContext)
    extends ControllerComponents

// Exposes actions and handler to the AccountController by wiring the injected state into the base class.
class AccountBaseController @Inject()(acc: AccountControllerComponents)
    extends BaseController
    with RequestMarkerContext {
  override protected def controllerComponents: ControllerComponents = acc

  def AccountAction: AccountActionBuilder = acc.accountActionBuilder

  def accountResourceHandler: AccountResourceHandler = acc.accountResourceHandler
}
