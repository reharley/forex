package forex.services.rates.interpreters

import cats.effect.{IO, ContextShift, Timer}
import forex.domain.{Currency, Price, Rate, Timestamp}
import forex.services.rates.interpreters.oneframe.OneFrame
import forex.services.rates.interpreters.oneframe.errors.OneFrameError
import forex.services.rates.errors.Error
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import scala.concurrent.duration._

class CachedRatesSpec extends AnyWordSpec with Matchers {

  implicit val contextShift: ContextShift[IO] =
    IO.contextShift(scala.concurrent.ExecutionContext.global)
  implicit val timer: Timer[IO] =
    IO.timer(scala.concurrent.ExecutionContext.global)

  "CachedRates" should {

    "return cached value on second request for same pair" in {
      var callCount = 0
      val mockOneFrame = new OneFrame[IO] {
        def getRates: IO[Either[OneFrameError, List[Rate]]] = {
          callCount += 1
          val rates = List(
            Rate(
              Rate.Pair(Currency.USD, Currency.EUR),
              Price(BigDecimal(100)),
              Timestamp.now
            )
          )
          IO.pure(Right(rates))
        }
      }

      val program = for {
        cached <- CachedRates[IO](mockOneFrame, 5.seconds)
        pair = Rate.Pair(Currency.USD, Currency.EUR)
        result1 <- cached.get(pair)
        result2 <- cached.get(pair)
      } yield (result1, result2, callCount)

      val (result1, result2, count) = program.unsafeRunSync()

      result1 shouldBe a[Right[_, _]]
      result2 shouldBe a[Right[_, _]]
      count shouldEqual 1 // Should only call underlying once
    }

    "call underlying service again after cache expires" in {
      var callCount = 0
      val mockOneFrame = new OneFrame[IO] {
        def getRates: IO[Either[OneFrameError, List[Rate]]] = {
          callCount += 1
          val rates = List(
            Rate(
              Rate.Pair(Currency.USD, Currency.EUR),
              Price(BigDecimal(100 + callCount)),
              Timestamp.now
            )
          )
          IO.pure(Right(rates))
        }
      }

      val program = for {
        cached <- CachedRates[IO](mockOneFrame, 100.millis)
        pair = Rate.Pair(Currency.USD, Currency.EUR)
        result1 <- cached.get(pair)
        _ <- IO.sleep(150.millis)
        result2 <- cached.get(pair)
      } yield (result1, result2, callCount)

      val (result1, result2, count) = program.unsafeRunSync()

      result1 shouldBe a[Right[_, _]]
      result2 shouldBe a[Right[_, _]]
      count shouldEqual 2 // Should call underlying twice due to expiry
    }

    "cache all pairs from single fetch" in {
      var callCount = 0
      val mockOneFrame = new OneFrame[IO] {
        def getRates: IO[Either[OneFrameError, List[Rate]]] = {
          callCount += 1
          val rates = List(
            Rate(
              Rate.Pair(Currency.USD, Currency.EUR),
              Price(BigDecimal(100)),
              Timestamp.now
            ),
            Rate(
              Rate.Pair(Currency.GBP, Currency.JPY),
              Price(BigDecimal(100)),
              Timestamp.now
            )
          )
          IO.pure(Right(rates))
        }
      }

      val program = for {
        cached <- CachedRates[IO](mockOneFrame, 5.seconds)
        pair1 = Rate.Pair(Currency.USD, Currency.EUR)
        pair2 = Rate.Pair(Currency.GBP, Currency.JPY)
        result1 <- cached.get(pair1)
        result2 <- cached.get(pair2) // Should hit cache from first fetch
        result3 <- cached.get(pair1) // Should hit cache
      } yield (result1, result2, result3, callCount)

      val (result1, result2, result3, count) = program.unsafeRunSync()

      result1 shouldBe a[Right[_, _]]
      result2 shouldBe a[Right[_, _]]
      result3 shouldBe a[Right[_, _]]
      count shouldEqual 1 // Should call underlying once to fetch all pairs
    }

    "not cache errors" in {
      var callCount = 0
      val mockOneFrame = new OneFrame[IO] {
        def getRates: IO[Either[OneFrameError, List[Rate]]] = {
          callCount += 1
          IO.pure(Left(OneFrameError.ConnectionError("Service error")))
        }
      }

      val program = for {
        cached <- CachedRates[IO](mockOneFrame, 5.seconds)
        pair = Rate.Pair(Currency.USD, Currency.EUR)
        result1 <- cached.get(pair)
        result2 <- cached.get(pair)
      } yield (result1, result2, callCount)

      val (result1, result2, count) = program.unsafeRunSync()

      result1 shouldBe a[Left[_, _]]
      result2 shouldBe a[Left[_, _]]
      count shouldEqual 2 // Should call underlying twice since errors aren't cached
    }

    "return correct rate data" in {
      val testRate = Rate(
        Rate.Pair(Currency.USD, Currency.EUR),
        Price(BigDecimal(1.25)),
        Timestamp.now
      )
      val mockOneFrame = new OneFrame[IO] {
        def getRates: IO[Either[OneFrameError, List[Rate]]] =
          IO.pure(Right(List(testRate)))
      }

      val program = for {
        cached <- CachedRates[IO](mockOneFrame, 5.seconds)
        result <- cached.get(Rate.Pair(Currency.USD, Currency.EUR))
      } yield result

      val result = program.unsafeRunSync()

      result shouldBe a[Right[_, _]]
      val rate = result.getOrElse(fail("Expected Right"))
      rate.pair shouldEqual testRate.pair
      rate.price shouldEqual testRate.price
    }

    "handle sequential requests to same pair correctly" in {
      var callCount = 0
      val mockOneFrame = new OneFrame[IO] {
        def getRates: IO[Either[OneFrameError, List[Rate]]] = {
          callCount += 1
          val rates = List(
            Rate(
              Rate.Pair(Currency.USD, Currency.EUR),
              Price(BigDecimal(100)),
              Timestamp.now
            )
          )
          IO.pure(Right(rates))
        }
      }

      val program = for {
        cached <- CachedRates[IO](mockOneFrame, 5.seconds)
        pair = Rate.Pair(Currency.USD, Currency.EUR)
        result1 <- cached.get(pair)
        result2 <- cached.get(pair)
        result3 <- cached.get(pair)
      } yield (result1, result2, result3, callCount)

      val (result1, result2, result3, count) = program.unsafeRunSync()

      result1 shouldBe a[Right[_, _]]
      result2 shouldBe a[Right[_, _]]
      result3 shouldBe a[Right[_, _]]
      count shouldEqual 1 // All should use cache
    }

    "use the correct TTL duration" in {
      var callCount = 0
      val mockOneFrame = new OneFrame[IO] {
        def getRates: IO[Either[OneFrameError, List[Rate]]] = {
          callCount += 1
          val rates = List(
            Rate(
              Rate.Pair(Currency.USD, Currency.EUR),
              Price(BigDecimal(100)),
              Timestamp.now
            )
          )
          IO.pure(Right(rates))
        }
      }

      val program = for {
        cached <- CachedRates[IO](mockOneFrame, 50.millis)
        pair = Rate.Pair(Currency.USD, Currency.EUR)
        result1 <- cached.get(pair)
        _ <- IO.sleep(30.millis) // Still within TTL
        result2 <- cached.get(pair)
        _ <- IO.sleep(30.millis) // Now past TTL (30 + 30 = 60ms > 50ms)
        result3 <- cached.get(pair)
      } yield (result1, result2, result3, callCount)

      val (result1, result2, result3, count) = program.unsafeRunSync()

      result1 shouldBe a[Right[_, _]]
      result2 shouldBe a[Right[_, _]]
      result3 shouldBe a[Right[_, _]]
      count shouldEqual 2 // First call, then after expiry
    }

    "return the exact rate from underlying service" in {
      val testRate = Rate(
        Rate.Pair(Currency.USD, Currency.JPY),
        Price(BigDecimal(2.5)),
        Timestamp.now
      )
      val mockOneFrame = new OneFrame[IO] {
        def getRates: IO[Either[OneFrameError, List[Rate]]] = {
          IO.pure(Right(List(testRate)))
        }
      }

      val program = for {
        cached <- CachedRates[IO](mockOneFrame, 5.seconds)
        pair = Rate.Pair(Currency.USD, Currency.JPY)
        result <- cached.get(pair)
      } yield result

      val result = program.unsafeRunSync()

      result shouldBe a[Right[_, _]]
      val rate = result.getOrElse(fail("Expected Right"))
      rate.price shouldEqual testRate.price
    }

    "work with multiple different currency pairs in sequence" in {
      var callCount = 0
      val mockOneFrame = new OneFrame[IO] {
        def getRates: IO[Either[OneFrameError, List[Rate]]] = {
          callCount += 1
          val rates = List(
            Rate(
              Rate.Pair(Currency.USD, Currency.EUR),
              Price(BigDecimal(100)),
              Timestamp.now
            ),
            Rate(
              Rate.Pair(Currency.GBP, Currency.JPY),
              Price(BigDecimal(100)),
              Timestamp.now
            ),
            Rate(
              Rate.Pair(Currency.EUR, Currency.GBP),
              Price(BigDecimal(100)),
              Timestamp.now
            ),
            Rate(
              Rate.Pair(Currency.USD, Currency.CAD),
              Price(BigDecimal(100)),
              Timestamp.now
            )
          )
          IO.pure(Right(rates))
        }
      }

      val program = for {
        cached <- CachedRates[IO](mockOneFrame, 5.seconds)
        pairs = List(
          Rate.Pair(Currency.USD, Currency.EUR),
          Rate.Pair(Currency.GBP, Currency.JPY),
          Rate.Pair(Currency.EUR, Currency.GBP),
          Rate.Pair(Currency.USD, Currency.CAD)
        )
        results <- pairs.foldLeft(IO.pure(List.empty[Either[Error, Rate]])) {
          case (accIO, pair) =>
            for {
              acc <- accIO
              result <- cached.get(pair)
            } yield acc :+ result
        }
      } yield (results, callCount)

      val (results, count) = program.unsafeRunSync()

      results.foreach(_ shouldBe a[Right[_, _]])
      count shouldEqual 1 // One call to fetch all pairs
    }
  }

}
