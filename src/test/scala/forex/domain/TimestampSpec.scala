package forex.domain

import java.time.OffsetDateTime
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class TimestampSpec extends AnyWordSpec with Matchers {

  "Timestamp" should {

    "wrap an OffsetDateTime value" in {
      val odt = OffsetDateTime.parse("2023-01-15T10:30:00+00:00")
      val timestamp = Timestamp(odt)
      timestamp.value shouldEqual odt
    }

    "create current timestamp with now" in {
      val before = System.currentTimeMillis()
      val timestamp = Timestamp.now
      val after = System.currentTimeMillis()

      timestamp.value.toInstant.toEpochMilli should be >= before
      timestamp.value.toInstant.toEpochMilli should be <= after
    }

    "support equality comparison" in {
      val odt = OffsetDateTime.parse("2023-01-15T10:30:00+00:00")
      Timestamp(odt) shouldEqual Timestamp(odt)
    }

    "distinguish between different timestamps" in {
      val odt1 = OffsetDateTime.parse("2023-01-15T10:30:00+00:00")
      val odt2 = OffsetDateTime.parse("2023-01-15T10:31:00+00:00")
      Timestamp(odt1) should not equal Timestamp(odt2)
    }

    "handle timestamps with different timezones" in {
      val odtUTC = OffsetDateTime.parse("2023-01-15T10:30:00+00:00")
      val odtEST = OffsetDateTime.parse("2023-01-15T05:30:00-05:00")

      val tsUTC = Timestamp(odtUTC)
      val tsEST = Timestamp(odtEST)

      // They should represent the same instant
      tsUTC.value.toInstant shouldEqual tsEST.value.toInstant
    }
  }

}
