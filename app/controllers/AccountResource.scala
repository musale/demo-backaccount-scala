package controllers

import javax.inject.{Inject, Provider}

import play.api.MarkerContext

import scala.concurrent.{ExecutionContext, Future}
import play.api.libs.json._

/**
  * DTO for displaying Account information.
  */
case class AccountResource(status: String, message: String, code: Int)

object AccountResource {

  /**
    * Mapping to write a AccountResource out as a JSON value.
    */
  implicit val implicitWrites = new Writes[AccountResource] {
    def writes(resource: AccountResource): JsValue = {
      Json.obj(
        "status" -> resource.status,
        "message" -> resource.message,
        "code" -> resource.code
      )
    }
  }
}

/**
  * Controls access to the backend data, returning [[AccountResource]]
  */
class AccountResourceHandler @Inject()(
    routerProvider: Provider[AccountRouter],
    storage: AccountStore)(implicit ec: ExecutionContext) {

  def deposit(accountInput: AccountFormInput)(
      implicit mc: MarkerContext): Future[AccountResource] = {
    storage.deposit(AccountAmount(accountInput.amount)).map { data =>
      createAccountResource(data)
    }
  }

  def withdraw(accountInput: AccountFormInput)(
      implicit mc: MarkerContext): Future[AccountResource] = {
    storage.withdraw(AccountAmount(accountInput.amount)).map { data =>
      createAccountResource(data)
    }
  }

  def balance()(implicit mc: MarkerContext): Future[AccountResource] = {
    storage.balance().map { data =>
      createAccountResource(data)
    }

  }

  private def createAccountResource(res: Response): AccountResource = {
    AccountResource(res.status, res.message, res.code)
  }

}
