package forex.domain

import java.time.OffsetDateTime
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class RateSpec extends AnyWordSpec with Matchers {

  "Rate" should {

    "be constructible with all required fields" in {
      val pair = Rate.Pair(Currency.USD, Currency.EUR)
      val price = Price(BigDecimal("1.20"))
      val timestamp = Timestamp(OffsetDateTime.parse("2023-01-15T10:30:00+00:00"))

      val rate = Rate(pair, price, timestamp)

      rate.pair shouldEqual pair
      rate.price shouldEqual price
      rate.timestamp shouldEqual timestamp
    }

    "contain a pair of currencies" in {
      val rate = Rate(Rate.Pair(Currency.GBP, Currency.JPY), Price(BigDecimal(100)), Timestamp.now)
      rate.pair.from shouldEqual Currency.GBP
      rate.pair.to shouldEqual Currency.JPY
    }

    "support different currency pairs" in {
      val rate1 = Rate(Rate.Pair(Currency.USD, Currency.CAD), Price(1.35), Timestamp.now)
      val rate2 = Rate(Rate.Pair(Currency.EUR, Currency.GBP), Price(0.86), Timestamp.now)

      rate1.pair should not equal rate2.pair
    }

    "support equality comparison" in {
      val odt = OffsetDateTime.parse("2023-01-15T10:30:00+00:00")
      val pair = Rate.Pair(Currency.USD, Currency.EUR)
      val price = Price(1.20)
      val timestamp = Timestamp(odt)

      Rate(pair, price, timestamp) shouldEqual Rate(pair, price, timestamp)
    }

    "distinguish between different rates" in {
      val odt = OffsetDateTime.parse("2023-01-15T10:30:00+00:00")
      val pair = Rate.Pair(Currency.USD, Currency.EUR)
      val timestamp = Timestamp(odt)

      val rate1 = Rate(pair, Price(1.20), timestamp)
      val rate2 = Rate(pair, Price(1.21), timestamp)

      rate1 should not equal rate2
    }

    "handle Pair with same from and to currency (edge case)" in {
      val pair = Rate.Pair(Currency.USD, Currency.USD)
      val rate = Rate(pair, Price(1.0), Timestamp.now)

      rate.pair.from shouldEqual Currency.USD
      rate.pair.to shouldEqual Currency.USD
    }
  }

  "Rate.Pair" should {

    "be constructible with two currencies" in {
      val pair = Rate.Pair(Currency.USD, Currency.EUR)
      pair.from shouldEqual Currency.USD
      pair.to shouldEqual Currency.EUR
    }

    "support equality comparison" in {
      Rate.Pair(Currency.USD, Currency.EUR) shouldEqual Rate.Pair(Currency.USD, Currency.EUR)
    }

    "distinguish between different pairs" in {
      Rate.Pair(Currency.USD, Currency.EUR) should not equal Rate.Pair(Currency.EUR, Currency.USD)
    }

    "represent all available currency combinations" in {
      val pair1 = Rate.Pair(Currency.USD, Currency.JPY)
      val pair2 = Rate.Pair(Currency.GBP, Currency.AUD)
      val pair3 = Rate.Pair(Currency.EUR, Currency.CAD)

      pair1.from shouldEqual Currency.USD
      pair2.to shouldEqual Currency.AUD
      pair3.from shouldEqual Currency.EUR
    }
  }

}
