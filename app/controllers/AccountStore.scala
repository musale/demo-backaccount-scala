package controllers

import akka.actor.ActorSystem
import java.io._
import java.time.{Instant, LocalDate, LocalTime, ZoneId, ZoneOffset}
import javax.inject.{Inject, Singleton}
import play.api.libs.concurrent.CustomExecutionContext
import play.api.{Logger, MarkerContext, Environment, Mode}
import play.api.libs.json._
import play.api.libs.functional.syntax._
import scala.concurrent.Future
import scala.io.Source

// Account is already for a single user. Get amount to deposit/withdraw
// ttypes are transaction types and can be withdraw or deposit
final case class AccountData(amount: String, timestamp: Long, ttype: String)

object AccountData {
  implicit val fileWriter = new Writes[AccountData] {
    def writes(account: AccountData): JsValue = {
      Json.obj(
        "amount" -> account.amount,
        "timestamp" -> account.timestamp,
        "ttype" -> account.ttype
      )
    }
  }
  implicit val fileReader: Reads[AccountData] = (
    (JsPath \ "amount").read[String] and
      (JsPath \ "timestamp").read[Long] and
      (JsPath \ "ttype").read[String]
  )(AccountData.apply _)
}

// Generic class for a HTTP response
case class Response(status: String, message: String, code: Int)

// Write Response to json
object Response {
  implicit val implicitWrites = new Writes[Response] {
    def writes(res: Response): JsValue = {
      Json.obj(
        "status" -> res.status,
        "message" -> res.message,
        "code" -> res.code
      )
    }
  }
}

// Convert amount to double for easier calculations
class AccountAmount private (val amount: String) extends AnyVal {
  def toDouble: Double = amount.toDouble
}

object AccountAmount {
  def apply(raw: String): AccountAmount = {
    require(raw != null)
    new AccountAmount(raw)
  }
}

class AccountExecutionContext @Inject()(actorSystem: ActorSystem)
    extends CustomExecutionContext(actorSystem, "repository.dispatcher")

// A pure non-blocking interface for the AccountStore.
trait AccountStore {
  def balance()(implicit mc: MarkerContext): Future[Response]
  def deposit(amount: AccountAmount)(
      implicit mc: MarkerContext): Future[Response]
  def withdraw(amount: AccountAmount)(
      implicit mc: MarkerContext): Future[Response]
}

// A trivial implementation for the AccountStore.
@Singleton
class AccountStoreImpl @Inject()(env: Environment)(
    implicit ec: AccountExecutionContext)
    extends AccountStore {

  private val logger = Logger(this.getClass)
  private val midnightstamp = midnightEpoch()
  private val filename = getFilename()

  // Get the file to persist transactions based on env
  def getFilename(): String = {
    if (env.mode == Mode.Test) {
      "transactions.test.db"
    } else {
      "transactions.db"
    }
  }

  // Get the start of current day (midnight)
  def midnightEpoch(): Long = {
    var midnight = LocalDate
      .now()
      .atTime(LocalTime.MIN)
      .atZone(ZoneId.systemDefault())
      .toInstant()
      .toEpochMilli()
    midnight / 1000
  }

  // Write transactions to file
  def writeTofile(account: AccountData) = {
    val writer = new PrintWriter(new FileOutputStream(filename, true))
    writer.println(Json.toJson(account))
    writer.close()
  }

  // Read transactions from file
  def readFromFile(): List[AccountData] = {
    import scala.collection.mutable.ListBuffer
    try {
      val account_data = Source.fromFile(filename)
      var account_list = new ListBuffer[AccountData]()
      for (line <- account_data.getLines()) {
        Json.parse(line).validate[AccountData] match {
          case s: JsSuccess[AccountData] => {
            val acc: AccountData = s.get
            account_list += acc
          }
          case e: JsError => {
            logger.error("error reading from file: %s", e.get)
          }
        }
      }
      account_list.toList
    } catch {
      case ex: FileNotFoundException => {
        new FileOutputStream(filename, true)
        val account_data = Source.fromFile(filename)
        var account_list = new ListBuffer[AccountData]()
        account_list.toList
      }
    }
  }

  // Get current balance
  override def balance()(implicit mc: MarkerContext): Future[Response] = {
    Future {
      logger.trace(s"balance")
      // get the current transaction from the list
      val account = readFromFile()
      if (!(account.isEmpty)) {
        val balance = account.last.amount
        Response("OK", s"account balance is USD $balance", 200)
      } else {
        // No deposit has been made
        Response("OK", "account balance is USD 0", 200)
      }
    }
  }

  // Make a deposit
  override def deposit(amount: AccountAmount)(
      implicit mc: MarkerContext): Future[Response] = {
    Future {
      logger.trace(s"deposit: amount = $amount")
      // get the current transaction from the list
      val transactions = readFromFile()

      var previous_amount: Double = 0.0
      val timestamp: Long = Instant.now.getEpochSecond
      if (!(transactions.isEmpty)) {
        val previous: AccountData = transactions.last
        previous_amount = AccountAmount(previous.amount).toDouble
      }

      // Max daily deposit is USD 150,000
      val MAX_DEPOSIT = 150000.toDouble
      val todays_deposits = transactions
        .filter(_.ttype == "Deposit")
        .filter(_.timestamp <= timestamp)
        .filter(_.timestamp >= midnightstamp)
      var todays_deposits_total: Double = 0.0
      if (!(todays_deposits.isEmpty)) {
        todays_deposits_total = todays_deposits.last.amount.toDouble
      }
      // Max transactions per day is 4
      val MAX_TRANSACTIONS = 4
      val total_transactions = todays_deposits.length
      // Max input is USD 40,000
      val MAX_INPUT = 40000.toDouble

      val input = amount.toDouble

      // deposit a max of 40k per transaction
      if (input > MAX_INPUT) {
        Response("error",
                 s"exceeded maximum deposit of USD $MAX_INPUT per transaction",
                 403)
      }
      // deposit a total max of 150k per day
      else if ((todays_deposits_total + input) >= MAX_DEPOSIT) {
        Response("error",
                 s"exceeded maximum deposit amount of USD $MAX_DEPOSIT per day",
                 403)
      }
      // deposit in a max of 4 transations
      else if (total_transactions >= MAX_TRANSACTIONS) {
        Response(
          "error",
          s"exceeded maximum deposit frequency of USD $MAX_TRANSACTIONS times per day",
          403)
      }
      // all conditions passed
      else {
        val current: Double = previous_amount + input
        val account = AccountData(current.toString, timestamp, "Deposit")
        // Add the transaction to transactions
        writeTofile(account)
        Response("OK", s"new balance after deposit is USD $current", 201)
      }
    }
  }

  // Withdraw from the account
  override def withdraw(amount: AccountAmount)(
      implicit mc: MarkerContext): Future[Response] = {
    Future {
      logger.trace(s"withdraw: amount = $amount")
      // get the current transaction from the list
      val transactions = readFromFile()
      var previous_amount: Double = 0.0
      val timestamp: Long = Instant.now.getEpochSecond
      if (!(transactions.isEmpty)) {
        val previous: AccountData = transactions.last
        previous_amount = AccountAmount(previous.amount).toDouble
      }
      // Max daily withdrawal is USD 50,000
      val MAX_WITHDRAWAL = 50000.toDouble
      val todays_withdrawals: List[AccountData] = transactions
        .filter(_.ttype == "Withdraw")
        .filter(_.timestamp <= timestamp)
        .filter(_.timestamp >= midnightstamp)
      var todays_withdraw_total: Double = 0.0;

      // Last deposit made
      val last_deposit =
        transactions.filter(_.ttype == "Deposit").last.amount.toDouble

      if (!(todays_withdrawals.isEmpty)) {
        todays_withdraw_total = last_deposit - todays_withdrawals.last.amount.toDouble
      }

      // Max transactions per day is 3
      val MAX_TRANSACTIONS = 3
      val total_transactions = todays_withdrawals.length
      val input = amount.toDouble
      val total_withdraw_amount = todays_withdraw_total + input

      // Max input is USD 20,000
      val MAX_INPUT = 20000.toDouble

      // withdrawal amount should be less than current balance
      if (input > previous_amount) {
        Response("error",
                 "account balance is lower than withdrawal amount",
                 403)
      }
      // withdraw less than 20K in a single transaction
      else if (input > MAX_INPUT) {
        Response(
          "error",
          s"exceeded maximum withdrawal of USD $MAX_INPUT per transaction",
          403)
      }
      // withdraw a total maximum of 50k per day
      else if (total_withdraw_amount > MAX_WITHDRAWAL) {
        Response(
          "error",
          s"exceeded maximum withdrawal of USD $MAX_WITHDRAWAL amount per day",
          403)
      }
      // withdraw in a max total of 3 transactions
      else if (total_transactions >= MAX_TRANSACTIONS) {
        Response(
          "error",
          s"exceeded maximum withdrawal frequency of $MAX_TRANSACTIONS times per day",
          403)
      }
      // all conditions passed
      else {
        val current: Double = previous_amount - input
        val account = AccountData(current.toString, timestamp, "Withdraw")
        // Add the transaction to transactions
        writeTofile(account)
        Response("OK",
                 s"new balance after withdrawal of USD $input is USD $current",
                 201)
      }
    }
  }
}
