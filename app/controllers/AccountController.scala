package controllers

import javax.inject.Inject

import play.api.data.Form
import play.api.mvc._
import play.api.libs.json._
import play.api.data.validation._

import scala.concurrent.{ExecutionContext, Future}

case class AccountFormInput(amount: String)

/**
  * Takes HTTP requests and produces JSON.
  */
class AccountController @Inject()(cc: AccountControllerComponents)(
    implicit ec: ExecutionContext)
    extends AccountBaseController(cc) {

  private val form: Form[AccountFormInput] = {
    import play.api.data.Forms._
    val amountCheckConstraints: Constraint[String] =
      Constraint("constraints.amountcheck")({ plainText =>
        try {
          plainText.toDouble
          Valid
        } catch {
          case ex: NumberFormatException =>
            Invalid(
              Seq(ValidationError(s"amount $plainText should be a Number")))
        }
      })
    Form(
      mapping(
        "amount" -> text.verifying(amountCheckConstraints)
      )(AccountFormInput.apply)(AccountFormInput.unapply(_))
    )
  }

  def deposit: Action[AnyContent] = AccountAction.async { implicit request =>
    processDeposit()
  }

  def withdraw: Action[AnyContent] = AccountAction.async { implicit request =>
    processWithdrawal()
  }

  def balance(): Action[AnyContent] = AccountAction.async { implicit request =>
    accountResourceHandler.balance.map { balance =>
      Ok(Json.toJson(balance))
    }
  }

  private def processDeposit[A]()(
      implicit request: AccountRequest[A]): Future[Result] = {
    def failure(badForm: Form[AccountFormInput]) = {
      Future.successful(BadRequest(badForm.errorsAsJson))
    }

    def success(input: AccountFormInput) = {
      accountResourceHandler.deposit(input).map { response =>
        response.code match {
          case 201 =>
            Created(Json.toJson(response))
              .withHeaders(LOCATION -> "/api/v1/balance")
          case 403 => Forbidden(Json.toJson(response))
        }
      }
    }

    form.bindFromRequest().fold(failure, success)
  }

  private def processWithdrawal[A]()(
      implicit request: AccountRequest[A]): Future[Result] = {
    def failure(badForm: Form[AccountFormInput]) = {
      Future.successful(BadRequest(badForm.errorsAsJson))
    }

    def success(input: AccountFormInput) = {
      accountResourceHandler.withdraw(input).map { response =>
        response.code match {
          case 201 =>
            Created(Json.toJson(response))
              .withHeaders(LOCATION -> "/api/v1/balance")
          case 403 => Forbidden(Json.toJson(response))
        }
      }
    }

    form.bindFromRequest().fold(failure, success)
  }
}
