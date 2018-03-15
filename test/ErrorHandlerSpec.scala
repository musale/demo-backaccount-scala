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

class ErrorHandlerSpec extends PlaySpec with GuiceOneAppPerTest {
  "ErrorHandler" should {
    "throw a 404 not found client error" in {
      val request = FakeRequest(GET, "/api/v1/doesnotexist")
        .withHeaders(HOST -> "localhost:9000")
        .withCSRFToken
      val result = route(app, request).get
      status(result) mustEqual 404
      val resultJson: JsValue =
        contentAsJson(result)(Timeout(2, TimeUnit.SECONDS))
      resultJson.toString mustEqual """{"errors":[{"status":404,"message":""}]}"""
    }
  }
}
