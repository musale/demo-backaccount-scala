import org.scalatestplus.play._
import org.scalatestplus.play.guice._
import org.scalatest.Assertions._

import play.api.test._
import play.api.mvc._
import play.api.libs.json._
import play.api.test.Helpers._
import play.api.test.CSRFTokenHelper._

import scala.concurrent._
import akka.util.Timeout
import java.time.Instant
import java.util.concurrent.TimeUnit
import java.util.NoSuchElementException

import controllers.{AccountAmount, AccountData, Response}

class AccountStoreSpec extends PlaySpec with GuiceOneAppPerTest {
  "AccountStore" should {
    "create double from AccountAmount" in {
      val amount = AccountAmount("10.0").toDouble
      amount mustEqual 10.0
    }

    "create AccountData" in {
      val timestamp = Instant.now.getEpochSecond;
      val data = AccountData("10.0", timestamp, "Deposit")
      data.ttype mustEqual "Deposit"
    }

    "create Response json" in {
      val response = Response("OK", "Success", 200)
      response.code mustEqual 200
      val resultJson: JsValue = Json.toJson(response)
      resultJson.toString mustEqual """{"status":"OK","message":"Success","code":200}"""
    }

    "throw a server error" in {
      assertThrows[NoSuchElementException] {

        val request =
          FakeRequest(POST,
                      "/api/v1/withdraw",
                      FakeHeaders(),
                      AnyContentAsJson(Json.parse("""{"amount":"20000.0"}""")))
        val futureResult = route(app, request).get
        val resultJson: JsValue =
          contentAsJson(futureResult)(Timeout(2, TimeUnit.SECONDS))
        status(futureResult) mustEqual 500
      }
    }
  }
}
