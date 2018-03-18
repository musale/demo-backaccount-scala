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
  def parseToDouble(str: String): Try[Double] = Try(str.toDouble)
  def toDouble: Double = {
    parseToDouble(amount) match {
      case Success(amount) => amount
      case Failure(f)      => throw new Error(s"amount $amount should be a Number")
    }
  }
}

object AccountAmount {
  def apply(raw: String): AccountAmount = {
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
      case Success(accountData) => {
        val accountList = new ListBuffer[AccountData]()
        for (line <- accountData.getLines()) {
          Json.parse(line).validate[AccountData] match {
            case s: JsSuccess[AccountData] => {
              val acc: AccountData = s.get
              accountList += acc
            }
            case e: JsError => {
              logger.error("error reading from file: %s", e.get)
            }
          }
        }
        accountList.toList
      }
      case Failure(f) => {
        new FileOutputStream(filename, true)
        val now = Instant.now.getEpochSecond
        val initialAccount = List(AccountData("0.0", now, "Deposit"))
        initialAccount
      }
    }
  }

  // Get current balance
  override def balance()(implicit mc: MarkerContext): Future[Response] = {
    Future {
      logger.trace(s"balance")
      // get the current transaction from the list
      val account: List[AccountData] = readFromFile()
      val now: Long = Instant.now.getEpochSecond
      val transaction: AccountData =
        account.lastOption.getOrElse(AccountData("0.0", now, "Initial"))
      val balance: String = transaction.amount
      Response("OK", s"account balance is USD $balance", 200)
    }
  }

  // Make a deposit
  override def deposit(amount: AccountAmount)(
      implicit mc: MarkerContext): Future[Response] = {
    Future {
      logger.trace(s"deposit: amount = $amount")
      // get the current transaction from the list
      val transactions = readFromFile()

      val previousAmount = getPreviousAmount(transactions)
      val timestamp: Long = Instant.now.getEpochSecond

      // Max daily deposit is USD 150,000
      val maxDeposit = AccountAmount("150000").toDouble
      val todaysDeposits = transactions
        .filter(_.ttype == "Deposit")
        .filter(_.timestamp <= timestamp)
        .filter(_.timestamp >= midnightstamp)
      val todaysDepositsTotal: Double = getTodaysTotalDeposit(todaysDeposits)

      // Max transactions per day is 4
      val maxTransactions = 4
      val totalTransactions = todaysDeposits.length
      // Max input is USD 40,000
      val maxInput = AccountAmount("40000").toDouble

      val input = amount.toDouble

      validateDeposit(input,
                      maxInput,
                      todaysDepositsTotal,
                      maxDeposit,
                      totalTransactions,
                      maxTransactions,
                      previousAmount,
                      timestamp)
    }
  }

  // Run validations for a deposit and return appropriate Response
  def validateDeposit(input: Double,
                      maxInput: Double,
                      todaysDepositsTotal: Double,
                      maxDeposit: Double,
                      totalTransactions: Double,
                      maxTransactions: Double,
                      previousAmount: Double,
                      timestamp: Long): Response = {
    // deposit a max of 40k per transaction
    if (input > maxInput) {
      Response("error",
               s"exceeded maximum deposit of USD $maxInput per transaction",
               403)
    }
    // deposit a total max of 150k per day
    else if ((todaysDepositsTotal + input) >= maxDeposit) {
      Response("error",
               s"exceeded maximum deposit amount of USD $maxDeposit per day",
               403)
    }
    // deposit in a max of 4 transations
    else if (totalTransactions >= maxTransactions) {
      Response(
        "error",
        s"exceeded maximum deposit frequency of USD $maxTransactions times per day",
        403)
    }
    // all conditions passed
    else {
      val current: Double = previousAmount + input
      val account = AccountData(current.toString, timestamp, "Deposit")
      // Add the transaction to transactions
      writeTofile(account)
      Response("OK", s"new balance after deposit is USD $current", 201)
    }
  }

  // Get previous transactions amount
  def getPreviousAmount(transactions: List[AccountData]): Double = {
    if (!(transactions.isEmpty)) {
      val now = Instant.now.getEpochSecond
      val previous: AccountData =
        transactions.lastOption.getOrElse(AccountData("0.0", now, "Initial"))
      AccountAmount(previous.amount).toDouble
    } else 0.0
  }

  // Get the total withdrawn today
  def getTodaysTotalWithdrawal(transactions: List[AccountData],
                               lastDeposit: Double): Double = {
    if (!(transactions.isEmpty)) {
      val now = Instant.now.getEpochSecond
      val lastTransaction =
        transactions.lastOption.getOrElse(AccountData("0.0", now, "Initial"))
      val lastBalance = AccountAmount(lastTransaction.amount).toDouble
      lastDeposit - lastBalance
    } else 0.0
  }

  // Get the total deposited today
  def getTodaysTotalDeposit(transactions: List[AccountData]): Double = {
    if (!(transactions.isEmpty)) {
      val now = Instant.now.getEpochSecond
      val lastTransaction =
        transactions.lastOption.getOrElse(AccountData("0.0", now, "Initial"))
      val lastBalance = AccountAmount(lastTransaction.amount).toDouble
      lastBalance
    } else 0.0
  }

  // Get the last deposit
  def getLastDeposit(transactions: List[AccountData]): Double = {
    if (!(transactions.isEmpty)) {
      val now = Instant.now.getEpochSecond
      val lastDepositTransaction = transactions
        .filter(_.ttype == "Deposit")
        .lastOption
        .getOrElse(AccountData("0.0", now, "Initial"))
      val lastDeposit = AccountAmount(lastDepositTransaction.amount).toDouble
      lastDeposit
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
      val previousAmount = getPreviousAmount(transactions: List[AccountData])
      // Max daily withdrawal is USD 50,000
      val maxWithdrawal = AccountAmount("50000").toDouble
      val todaysWithdrawals: List[AccountData] = transactions
        .filter(_.ttype == "Withdraw")
        .filter(_.timestamp <= timestamp)
        .filter(_.timestamp >= midnightstamp)

      // Last deposit made
      val lastDeposit = getLastDeposit(transactions)

      val todaysWithdrawnTotal: Double =
        getTodaysTotalWithdrawal(todaysWithdrawals, lastDeposit)

      // Max transactions per day is 3
      val maxTransactions = 3
      val totalTransactions = todaysWithdrawals.length
      val input = amount.toDouble
      val todaysWithdrawnAmount = todaysWithdrawnTotal + input

      // Max input is USD 20,000
      val maxInput = AccountAmount("20000").toDouble

      validateWithdrawal(input,
                         previousAmount,
                         maxInput,
                         todaysWithdrawnAmount,
                         maxWithdrawal,
                         totalTransactions,
                         maxTransactions,
                         timestamp)
    }
  }

  // Run validations before doing a withdrawal and return approriate Reponse
  def validateWithdrawal(input: Double,
                         previousAmount: Double,
                         maxInput: Double,
                         todaysWithdrawnAmount: Double,
                         maxWithdrawal: Double,
                         totalTransactions: Double,
                         maxTransactions: Double,
                         timestamp: Long): Response = {
    // withdrawal amount should be less than current balance
    if (input > previousAmount) {
      Response("error", "account balance is lower than withdrawal amount", 403)
    }
    // withdraw less than 20K in a single transaction
    else if (input > maxInput) {
      Response("error",
               s"exceeded maximum withdrawal of USD $maxInput per transaction",
               403)
    }
    // withdraw a total maximum of 50k per day
    else if (todaysWithdrawnAmount > maxWithdrawal) {
      Response(
        "error",
        s"exceeded maximum withdrawal of USD $maxWithdrawal amount per day",
        403)
    }
    // withdraw in a max total of 3 transactions
    else if (totalTransactions >= maxTransactions) {
      Response(
        "error",
        s"exceeded maximum withdrawal frequency of $maxTransactions times per day",
        403)
    }
    // all conditions passed
    else {
      val current: Double = previousAmount - input
      val account = AccountData(current.toString, timestamp, "Withdraw")
      // Add the transaction to transactions
      writeTofile(account)
      Response("OK",
               s"new balance after withdrawal of USD $input is USD $current",
               201)
    }
  }
}
