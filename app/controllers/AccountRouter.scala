package controllers

import javax.inject.Inject

import play.api.routing.Router.Routes
import play.api.routing.SimpleRouter
import play.api.routing.sird._

/**
  * Routes and URLs to the AccountResource controller.
  */
class AccountRouter @Inject()(controller: AccountController)
    extends SimpleRouter {
  val prefix = "/api/v1"

  override def routes: Routes = {
    case GET(p"/balance") =>
      controller.balance

    case POST(p"/deposit") =>
      controller.deposit

    case POST(p"/withdraw") =>
      controller.withdraw

  }

}
