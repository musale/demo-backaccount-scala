// import org.scalatestplus.play._
// import org.scalatestplus.play.guice._
// import org.scalatest.BeforeAndAfterEach

// import play.api.test._
// import play.api.mvc._
// import play.api.libs.json._
// import play.api.test.Helpers._
// import play.api.test.CSRFTokenHelper._
// import java.time.Instant

// import ErrorHandler

// class ErrorHandlerSpec extends PlaySpec with GuiceOneAppPerTest {
//     "ErrorJandler" should {
//         "catch server error" in {
//             try {
//                 throw new Exception("test error")
//             } catch {
//                 case ex: Exception => println 
//             }
//         }

//         "create AccountData" in {
//             val timestamp = Instant.now.getEpochSecond;
//             val data = AccountData("10.0", timestamp, "Deposit")
//             data.ttype mustEqual "Deposit"
//         }
//     }
// }