package frdomain.e2e
package domain

import org.joda.time._

import scala.collection.concurrent.TrieMap
import scalaz._
import Scalaz._

import Common._
import Money._

/**
 * In memory implementation of `AccountRepository` based on `Disjunction` monad.
 */
class InMemoryAccountRepository extends AccountRepository[ErrorOr] {

  val M: Monad[ErrorOr] = Monad[ErrorOr]

  // account repo
  private val arepo = TrieMap.empty[String, Account]

  // balance repo
  private val brepo = TrieMap.empty[(String, LocalDate), Money]
  
  def query(accountNo: String): ErrorOr[Account] = 
    arepo.get(accountNo).map(a => a.right).getOrElse(s"Account with no $accountNo does not exist".left)

  def store(a: Account): ErrorOr[Account] = {
    arepo += ((a.no, a))
    a.right
  }

  def update(a: Account): ErrorOr[Account] = store(a)

  def accountsOpenedOn(date: DateTime): ErrorOr[List[Account]] = (for {
    (no, a) <- arepo
    if a.dateOpened.toLocalDate.compareTo(today.toLocalDate) == 0
  } yield a).toList.right

  def updateBalance(account: Account, amount: Amount, asOn: DateTime): ErrorOr[AccountBalance] = {
    val m = Money(Map(account.currency -> amount))
    brepo.get((account.no, asOn.toLocalDate)).map { oldBalance =>
      val newBalance = oldBalance |+| m
      val r = brepo.replace((account.no, asOn.toLocalDate), oldBalance, newBalance)
      if (r) AccountBalance.accountBalance(account, newBalance.some, asOn.toLocalDate)
      else s"Balance update failed : could not get lock on Map".left
    }.getOrElse {
      brepo += (((account.no, asOn.toLocalDate), m))
      AccountBalance.accountBalance(account, m.some, asOn.toLocalDate)
    }
  }

  def balance(account: Account, asOn: DateTime): ErrorOr[AccountBalance] =
    brepo.get((account.no, asOn.toLocalDate))
         .map { b => AccountBalance.accountBalance(account, b.some, asOn.toLocalDate) }
         .getOrElse { AccountBalance.accountBalance(account, None, asOn.toLocalDate) }
}

object InMemoryAccountRepository extends InMemoryAccountRepository