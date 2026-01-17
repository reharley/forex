package forex.domain

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class PriceSpec extends AnyWordSpec with Matchers {

  "Price" should {

    "wrap a BigDecimal value" in {
      val price = Price(BigDecimal("100.50"))
      price.value shouldEqual BigDecimal("100.50")
    }

    "be constructible from an Integer" in {
      val price = Price(100: Integer)
      price.value shouldEqual BigDecimal(100)
    }

    "handle zero price" in {
      val price = Price(BigDecimal("0"))
      price.value shouldEqual BigDecimal("0")
    }

    "handle negative prices" in {
      val price = Price(BigDecimal("-50.25"))
      price.value shouldEqual BigDecimal("-50.25")
    }

    "handle very large prices" in {
      val largePrice = BigDecimal("999999999.99")
      val price = Price(largePrice)
      price.value shouldEqual largePrice
    }

    "handle very small prices" in {
      val smallPrice = BigDecimal("0.000001")
      val price = Price(smallPrice)
      price.value shouldEqual smallPrice
    }

    "support equality comparison" in {
      Price(100: Integer) shouldEqual Price(100: Integer)
      Price(100: Integer) should not equal Price(50: Integer)
    }

    "support value-based comparison through BigDecimal" in {
      val price1 = Price(BigDecimal("100.50"))
      val price2 = Price(BigDecimal("100.50"))
      price1 shouldEqual price2
    }
  }

}
