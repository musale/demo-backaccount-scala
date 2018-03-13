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
import scala.collection.mutable.ListBuffer
import scala.util.{Try, Success, Failure}

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
    val midnight = LocalDate
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
  def readFileHelper(): Try[Source] = {
    Try(Source.fromFile(filename))
  }
  def readFromFile(): List[AccountData] = {
    readFileHelper() match {
      case Success(account_data) => {
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
      }
      case Failure(f) => {
        new FileOutputStream(filename, true)
        val now = Instant.now.getEpochSecond
        val initial_account = List(AccountData("0.0", now, "Deposit"))
        initial_account
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

      val previous_amount = getPreviousAmount(transactions)
      val timestamp: Long = Instant.now.getEpochSecond

      // Max daily deposit is USD 150,000
      val max_deposit = 150000.toDouble
      val todays_deposits = transactions
        .filter(_.ttype == "Deposit")
        .filter(_.timestamp <= timestamp)
        .filter(_.timestamp >= midnightstamp)
      val todays_deposits_total: Double = getTodaysTotalDeposit(todays_deposits)

      // Max transactions per day is 4
      val max_transactions = 4
      val total_transactions = todays_deposits.length
      // Max input is USD 40,000
      val max_input = 40000.toDouble

      val input = amount.toDouble

      validateDeposit(input,
                      max_input,
                      todays_deposits_total,
                      max_deposit,
                      total_transactions,
                      max_transactions,
                      previous_amount,
                      timestamp)
    }
  }

  // Run validations for a deposit and return appropriate Response
  def validateDeposit(input: Double,
                      max_input: Double,
                      todays_deposits_total: Double,
                      max_deposit: Double,
                      total_transactions: Double,
                      max_transactions: Double,
                      previous_amount: Double,
                      timestamp: Long): Response = {
    // deposit a max of 40k per transaction
    if (input > max_input) {
      Response("error",
               s"exceeded maximum deposit of USD $max_input per transaction",
               403)
    }
    // deposit a total max of 150k per day
    else if ((todays_deposits_total + input) >= max_deposit) {
      Response("error",
               s"exceeded maximum deposit amount of USD $max_deposit per day",
               403)
    }
    // deposit in a max of 4 transations
    else if (total_transactions >= max_transactions) {
      Response(
        "error",
        s"exceeded maximum deposit frequency of USD $max_transactions times per day",
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

  // Get previous transactions amount
  def getPreviousAmount(transactions: List[AccountData]): Double = {
    if (!(transactions.isEmpty)) {
      val previous: AccountData = transactions.last
      AccountAmount(previous.amount).toDouble
    } else 0.0
  }

  // Get the total withdrawn today
  def getTodaysTotalWithdrawal(transactions: List[AccountData],
                               last_deposit: Double): Double = {
    if (!(transactions.isEmpty)) {
      last_deposit - transactions.last.amount.toDouble
    } else 0.0
  }

  // Get the total deposited today
  def getTodaysTotalDeposit(transactions: List[AccountData]): Double = {
    if (!(transactions.isEmpty)) {
      transactions.last.amount.toDouble
    } else 0.0
  }

  // Get the last deposit
  def getLastDeposit(transactions: List[AccountData]): Double = {
    if(!(transactions.isEmpty)){
      transactions.filter(_.ttype == "Deposit").last.amount.toDouble
    } else 0.0
  }

  // Withdraw from the account
  override def withdraw(amount: AccountAmount)(
      implicit mc: MarkerContext): Future[Response] = {
    Future {
      logger.trace(s"withdraw: amount = $amount")
      // get the current transaction from the list
      val transactions = readFromFile()
      val timestamp: Long = Instant.now.getEpochSecond
      val previous_amount = getPreviousAmount(transactions: List[AccountData])
      // Max daily withdrawal is USD 50,000
      val max_withdrawal = 50000.toDouble
      val todays_withdrawals: List[AccountData] = transactions
        .filter(_.ttype == "Withdraw")
        .filter(_.timestamp <= timestamp)
        .filter(_.timestamp >= midnightstamp)

      // Last deposit made
      val last_deposit = getLastDeposit(transactions)

      val todays_withdraw_total: Double =
        getTodaysTotalWithdrawal(todays_withdrawals, last_deposit)

      // Max transactions per day is 3
      val max_transactions = 3
      val total_transactions = todays_withdrawals.length
      val input = amount.toDouble
      val total_withdraw_amount = todays_withdraw_total + input

      // Max input is USD 20,000
      val max_input = 20000.toDouble

      validateWithdrawal(input,
                         previous_amount,
                         max_input,
                         total_withdraw_amount,
                         max_withdrawal,
                         total_transactions,
                         max_transactions,
                         timestamp)
    }
  }

  // Run validations before doing a withdrawal and return approriate Reponse
  def validateWithdrawal(input: Double,
                         previous_amount: Double,
                         max_input: Double,
                         total_withdraw_amount: Double,
                         max_withdrawal: Double,
                         total_transactions: Double,
                         max_transactions: Double,
                         timestamp: Long): Response = {
    // withdrawal amount should be less than current balance
    if (input > previous_amount) {
      Response("error", "account balance is lower than withdrawal amount", 403)
    }
    // withdraw less than 20K in a single transaction
    else if (input > max_input) {
      Response("error",
               s"exceeded maximum withdrawal of USD $max_input per transaction",
               403)
    }
    // withdraw a total maximum of 50k per day
    else if (total_withdraw_amount > max_withdrawal) {
      Response(
        "error",
        s"exceeded maximum withdrawal of USD $max_withdrawal amount per day",
        403)
    }
    // withdraw in a max total of 3 transactions
    else if (total_transactions >= max_transactions) {
      Response(
        "error",
        s"exceeded maximum withdrawal frequency of $max_transactions times per day",
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
