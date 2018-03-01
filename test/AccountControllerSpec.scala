import org.scalatestplus.play._
import org.scalatestplus.play.guice._
import org.scalatest.BeforeAndAfterEach

import play.api.test._
import play.api.mvc._
import play.api.libs.json._
import play.api.test.Helpers._
import play.api.test.CSRFTokenHelper._

import scala.concurrent._
import akka.util.Timeout
import java.util.concurrent.TimeUnit
import java.io._

class AccountControllerSpec extends PlaySpec with GuiceOneAppPerTest {

  "AccountController" should {

    "return OK when getting balance" in {
      val request = FakeRequest(GET, "/api/v1/balance")
        .withHeaders(HOST -> "localhost:9000")
        .withCSRFToken
      val result = route(app, request).get
      status(result) mustEqual OK
    }

    "fail for deposit amount which is not a Number" in {
      val request =
        FakeRequest(POST,
                    "/api/v1/deposit",
                    FakeHeaders(),
                    AnyContentAsJson(Json.parse("""{"amount":"word"}""")))
      val futureResult = route(app, request).get
      val resultJson: JsValue =
        contentAsJson(futureResult)(Timeout(2, TimeUnit.SECONDS))
      status(futureResult) mustEqual 400
    }

    "create first deposit of USD 40000.0" in {
      val request =
        FakeRequest(POST,
                    "/api/v1/deposit",
                    FakeHeaders(),
                    AnyContentAsJson(Json.parse("""{"amount":"40000.0"}""")))
      val futureResult = route(app, request).get
      val resultJson: JsValue =
        contentAsJson(futureResult)(Timeout(2, TimeUnit.SECONDS))
      status(futureResult) mustEqual 201
    }

    "return balance as USD 40000.0" in {
      val request = FakeRequest(GET, "/api/v1/balance")
        .withHeaders(HOST -> "localhost:9000")
        .withCSRFToken
      val result = route(app, request).get
      val resultJson: JsValue =
        contentAsJson(result)(Timeout(2, TimeUnit.SECONDS))
      status(result) mustEqual OK
      resultJson
        .toString() mustEqual """{"status":"OK","message":"account balance is USD 40000.0","code":200}"""
    }

    "ensure that REST API does not need a trailing / " in {
      val request = FakeRequest(GET, "/api/v1/balance/")
        .withHeaders(HOST -> "localhost:9000")
        .withCSRFToken
      val result = route(app, request).get
      val resultJson: JsValue =
        contentAsJson(result)(Timeout(2, TimeUnit.SECONDS))
      status(result) mustEqual OK
      resultJson
        .toString() mustEqual """{"status":"OK","message":"account balance is USD 40000.0","code":200}"""
    }
    "create second deposit of USD 40000.0" in {
      val request =
        FakeRequest(POST,
                    "/api/v1/deposit",
                    FakeHeaders(),
                    AnyContentAsJson(Json.parse("""{"amount":"40000.0"}""")))
      val futureResult = route(app, request).get
      val resultJson: JsValue =
        contentAsJson(futureResult)(Timeout(2, TimeUnit.SECONDS))
      status(futureResult) mustEqual 201
    }

    "create third deposit of USD 40000.0" in {
      val request =
        FakeRequest(POST,
                    "/api/v1/deposit",
                    FakeHeaders(),
                    AnyContentAsJson(Json.parse("""{"amount":"40000.0"}""")))
      val futureResult = route(app, request).get
      val resultJson: JsValue =
        contentAsJson(futureResult)(Timeout(2, TimeUnit.SECONDS))
      status(futureResult) mustEqual 201
    }

    "fail depositing a total of more than USD 150k per day" in {
      val request =
        FakeRequest(POST,
                    "/api/v1/deposit",
                    FakeHeaders(),
                    AnyContentAsJson(Json.parse("""{"amount":"40000.0"}""")))
      val futureResult = route(app, request).get
      val resultJson: JsValue =
        contentAsJson(futureResult)(Timeout(2, TimeUnit.SECONDS))
      status(futureResult) mustEqual 403
      resultJson
        .toString() mustEqual """{"status":"error","message":"exceeded maximum deposit amount of USD 150000.0 per day","code":403}"""
    }

    "not deposit more than USD 40000 per transaction" in {
      val request =
        FakeRequest(POST,
                    "/api/v1/withdraw",
                    FakeHeaders(),
                    AnyContentAsJson(Json.parse("""{"amount":"40000000.0"}""")))
      val futureResult = route(app, request).get
      val resultJson: JsValue =
        contentAsJson(futureResult)(Timeout(2, TimeUnit.SECONDS))
      status(futureResult) mustEqual 403
    }

    "create fourth deposit of USD 20000.0" in {
      val request =
        FakeRequest(POST,
                    "/api/v1/deposit",
                    FakeHeaders(),
                    AnyContentAsJson(Json.parse("""{"amount":"20000.0"}""")))
      val futureResult = route(app, request).get
      val resultJson: JsValue =
        contentAsJson(futureResult)(Timeout(2, TimeUnit.SECONDS))
      status(futureResult) mustEqual 201
    }

    "fail because of maximum 4 deposit transactions per day" in {
      val request =
        FakeRequest(POST,
                    "/api/v1/deposit",
                    FakeHeaders(),
                    AnyContentAsJson(Json.parse("""{"amount":"20000.0"}""")))
      val futureResult = route(app, request).get
      val resultJson: JsValue =
        contentAsJson(futureResult)(Timeout(2, TimeUnit.SECONDS))
      status(futureResult) mustEqual 403
    }

    "not withdraw more than USD 20000 in one transaction " in {
      val request =
        FakeRequest(POST,
                    "/api/v1/withdraw",
                    FakeHeaders(),
                    AnyContentAsJson(Json.parse("""{"amount":"40000.0"}""")))
      val futureResult = route(app, request).get
      val resultJson: JsValue =
        contentAsJson(futureResult)(Timeout(2, TimeUnit.SECONDS))
      status(futureResult) mustEqual 403
    }

    "not withdraw more than account balance" in {
      val request =
        FakeRequest(POST,
                    "/api/v1/withdraw",
                    FakeHeaders(),
                    AnyContentAsJson(Json.parse("""{"amount":"40000000.0"}""")))
      val futureResult = route(app, request).get
      val resultJson: JsValue =
        contentAsJson(futureResult)(Timeout(2, TimeUnit.SECONDS))
      status(futureResult) mustEqual 403
    }

    "withdraw first USD 20000 in one transaction " in {
      val request =
        FakeRequest(POST,
                    "/api/v1/withdraw",
                    FakeHeaders(),
                    AnyContentAsJson(Json.parse("""{"amount":"20000.0"}""")))
      val futureResult = route(app, request).get
      val resultJson: JsValue =
        contentAsJson(futureResult)(Timeout(2, TimeUnit.SECONDS))
      status(futureResult) mustEqual 201
    }

    "withdraw second USD 20000 in one transaction " in {
      val request =
        FakeRequest(POST,
                    "/api/v1/withdraw",
                    FakeHeaders(),
                    AnyContentAsJson(Json.parse("""{"amount":"20000.0"}""")))
      val futureResult = route(app, request).get
      val resultJson: JsValue =
        contentAsJson(futureResult)(Timeout(2, TimeUnit.SECONDS))
      // resultJson.toString() mustEqual """{"status":"error"}"""
      status(futureResult) mustEqual 201
    }

    "not withdraw because it is more than total amount allowed per day" in {
      val request =
        FakeRequest(POST,
                    "/api/v1/withdraw",
                    FakeHeaders(),
                    AnyContentAsJson(Json.parse("""{"amount":"20000.0"}""")))
      val futureResult = route(app, request).get
      val resultJson: JsValue =
        contentAsJson(futureResult)(Timeout(2, TimeUnit.SECONDS))
      status(futureResult) mustEqual 403
    }

    "fail to withdraw amount which is not a Number" in {
      val request =
        FakeRequest(POST,
                    "/api/v1/withdraw",
                    FakeHeaders(),
                    AnyContentAsJson(Json.parse("""{"amount":"word"}""")))
      val futureResult = route(app, request).get
      val resultJson: JsValue =
        contentAsJson(futureResult)(Timeout(2, TimeUnit.SECONDS))
      status(futureResult) mustEqual 400
    }

    "withdraw USD 10000 in one transaction " in {
      val request =
        FakeRequest(POST,
                    "/api/v1/withdraw",
                    FakeHeaders(),
                    AnyContentAsJson(Json.parse("""{"amount":"10000.0"}""")))
      val futureResult = route(app, request).get
      val resultJson: JsValue =
        contentAsJson(futureResult)(Timeout(2, TimeUnit.SECONDS))
      status(futureResult) mustEqual 201
    }

    "not withdraw because total daily transactions are more than 3" in {
      val request =
        FakeRequest(POST,
                    "/api/v1/withdraw",
                    FakeHeaders(),
                    AnyContentAsJson(Json.parse("""{"amount":"10000.0"}""")))
      val futureResult = route(app, request).get
      val resultJson: JsValue =
        contentAsJson(futureResult)(Timeout(2, TimeUnit.SECONDS))
      status(futureResult) mustEqual 403
    }

    "remove test data file" in {
      val delete = new File("transactions.test.db").delete()
      delete mustEqual true
    }
  }

}
