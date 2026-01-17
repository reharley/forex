package forex.domain

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class CurrencySpec extends AnyWordSpec with Matchers {

  "Currency" should {

    "contain all 9 supported currencies" in {
      Currency.all should have length 9
    }

    "include USD, EUR, GBP, JPY, AUD, CAD, CHF, NZD, SGD" in {
      Currency.all should contain allOf (
        Currency.USD,
        Currency.EUR,
        Currency.GBP,
        Currency.JPY,
        Currency.AUD,
        Currency.CAD,
        Currency.CHF,
        Currency.NZD,
        Currency.SGD
      )
    }

    "convert AUD to string" in {
      Currency.show.show(Currency.AUD) shouldEqual "AUD"
    }

    "convert EUR to string" in {
      Currency.show.show(Currency.EUR) shouldEqual "EUR"
    }

    "convert JPY to string" in {
      Currency.show.show(Currency.JPY) shouldEqual "JPY"
    }

    "parse 'USD' from string" in {
      Currency.fromString("USD") shouldEqual Right(Currency.USD)
    }

    "parse 'eur' from string (case-insensitive)" in {
      Currency.fromString("eur") shouldEqual Right(Currency.EUR)
    }

    "parse 'GBP' from string" in {
      Currency.fromString("GBP") shouldEqual Right(Currency.GBP)
    }

    "parse 'jpy' from string (case-insensitive)" in {
      Currency.fromString("jpy") shouldEqual Right(Currency.JPY)
    }

    "parse all supported currencies from string" in {
      Currency.fromString("AUD") shouldEqual Right(Currency.AUD)
      Currency.fromString("CAD") shouldEqual Right(Currency.CAD)
      Currency.fromString("CHF") shouldEqual Right(Currency.CHF)
      Currency.fromString("NZD") shouldEqual Right(Currency.NZD)
      Currency.fromString("SGD") shouldEqual Right(Currency.SGD)
    }

    "return error for invalid currency string" in {
      Currency.fromString("INVALID") should matchPattern { case Left(_) =>
      }
    }
  }

}
