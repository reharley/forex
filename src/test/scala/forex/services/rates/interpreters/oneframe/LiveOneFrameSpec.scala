package forex.services.rates.interpreters.oneframe

import cats.effect.{IO, Resource, ConcurrentEffect}
import forex.domain.Currency
import org.http4s._
import org.http4s.client.Client
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.typelevel.ci.CIString
import java.time.{ZoneOffset, Instant}

class LiveOneFrameSpec extends AnyWordSpec with Matchers {

  implicit val contextShift: cats.effect.ContextShift[IO] =
    IO.contextShift(scala.concurrent.ExecutionContext.global)
  implicit val ioEnv: ConcurrentEffect[IO] = IO.ioConcurrentEffect

  "LiveOneFrame" should {

    "construct URI with all currency pairs" in {
      val mockClient = mockHttpClient { req =>
        val uri = req.uri.toString()
        val allCurrencies = Currency.all
        val expectedPairs = for {
          from <- allCurrencies
          to <- allCurrencies
          if from != to
        } yield s"${from}${to}"

        expectedPairs.foreach { pair =>
          uri should include(s"pair=$pair")
        }

        IO.pure(validRateResponse())
      }

      val liveOneFrame = new LiveOneFrame[IO](mockClient, config)
      val _ = liveOneFrame.getRates.unsafeRunSync()
    }

    "include token in request headers" in {
      val token = "test-token-123"
      val configWithToken =
        OneFrameConfig(baseUrl = "http://api.example.com", token = token)

      val mockClient = mockHttpClient { req =>
        req.headers
          .get(CIString("token"))
          .map(_.toString)
          .getOrElse("") should include("test-token-123")

        IO.pure(validRateResponse())
      }

      val liveOneFrame = new LiveOneFrame[IO](mockClient, configWithToken)
      val _ = liveOneFrame.getRates.unsafeRunSync()
    }

    "convert successful JSON response to rates" in {
      val now = Instant.now().atOffset(ZoneOffset.UTC)
      val rateResponse =
        forex.services.rates.interpreters.oneframe.Protocol.RateResponse(
          from = "USD",
          to = "EUR",
          bid = BigDecimal("1.10"),
          ask = BigDecimal("1.12"),
          price = BigDecimal("1.11"),
          time_stamp = now.toInstant.toString
        )

      val mockClient = mockHttpClient { _ =>
        IO.pure(List(rateResponse))
      }

      val liveOneFrame = new LiveOneFrame[IO](mockClient, config)
      val result = liveOneFrame.getRates.unsafeRunSync()

      result shouldBe a[Right[_, _]]
      val rates = result.getOrElse(fail("Expected Right, got Left"))
      rates.length should be > 0
      rates.head.pair.from shouldEqual Currency.USD
      rates.head.pair.to shouldEqual Currency.EUR
      rates.head.price.value shouldEqual BigDecimal("1.11")
    }

    "handle 401 Unauthorized as ServerError" in {
      val mockClient = Client[IO] { _ =>
        Resource.pure[IO, Response[IO]](Response[IO](Status.Unauthorized))
      }

      val liveOneFrame = new LiveOneFrame[IO](mockClient, config)
      val result = liveOneFrame.getRates.unsafeRunSync()

      result shouldBe a[Left[_, _]]
      val error = result.left.getOrElse(fail("Expected Left, got Right"))
      error shouldBe a[errors.OneFrameError.ServerError]
    }

    "handle 429 Too Many Requests as RateLimitExceeded" in {
      val mockClient = Client[IO] { _ =>
        Resource.pure[IO, Response[IO]](Response[IO](Status.TooManyRequests))
      }

      val liveOneFrame = new LiveOneFrame[IO](mockClient, config)
      val result = liveOneFrame.getRates.unsafeRunSync()

      result shouldBe a[Left[_, _]]
      val error = result.left.getOrElse(fail("Expected Left, got Right"))
      error shouldBe a[errors.OneFrameError.RateLimitExceeded.type]
    }

    "handle 500 Server Error" in {
      val mockClient = Client[IO] { _ =>
        Resource.pure[IO, Response[IO]](
          Response[IO](Status.InternalServerError)
        )
      }

      val liveOneFrame = new LiveOneFrame[IO](mockClient, config)
      val result = liveOneFrame.getRates.unsafeRunSync()

      result shouldBe a[Left[_, _]]
      val error = result.left.getOrElse(fail("Expected Left, got Right"))
      error shouldBe a[errors.OneFrameError.ServerError]
    }

    "handle connection errors gracefully" in {
      val mockClient = Client[IO] { _ =>
        Resource.eval[IO, Response[IO]](
          IO.raiseError(new Exception("Connection refused"))
        )
      }

      val liveOneFrame = new LiveOneFrame[IO](mockClient, config)
      val result = liveOneFrame.getRates.unsafeRunSync()

      result shouldBe a[Left[_, _]]
      val error = result.left.getOrElse(fail("Expected Left, got Right"))
      error shouldBe a[errors.OneFrameError.ConnectionError]
    }

    "handle invalid JSON response" in {
      val mockClient = Client[IO] { _ =>
        val invalidResponse =
          Response[IO](Status.Ok).withEntity("{invalid json")
        Resource.pure[IO, Response[IO]](invalidResponse)
      }

      val liveOneFrame = new LiveOneFrame[IO](mockClient, config)
      val result = liveOneFrame.getRates.unsafeRunSync()

      result shouldBe a[Left[_, _]]
      val error = result.left.getOrElse(fail("Expected Left, got Right"))
      error shouldBe a[errors.OneFrameError.UnexpectedResponse]
    }

    "handle invalid currency in response" in {
      val rateResponse =
        forex.services.rates.interpreters.oneframe.Protocol.RateResponse(
          from = "INVALID",
          to = "EUR",
          bid = BigDecimal("1.10"),
          ask = BigDecimal("1.12"),
          price = BigDecimal("1.11"),
          time_stamp = Instant.now().toString
        )

      val mockClient = mockHttpClient { _ =>
        IO.pure(List(rateResponse))
      }

      val liveOneFrame = new LiveOneFrame[IO](mockClient, config)
      val result = liveOneFrame.getRates.unsafeRunSync()

      result shouldBe a[Left[_, _]]
      val error = result.left.getOrElse(fail("Expected Left, got Right"))
      error shouldBe a[errors.OneFrameError.InvalidPair]
    }

    "handle multiple currency pairs in response" in {
      val now = Instant.now().atOffset(ZoneOffset.UTC)
      val responses = List(
        forex.services.rates.interpreters.oneframe.Protocol.RateResponse(
          from = "USD",
          to = "EUR",
          bid = BigDecimal("1.10"),
          ask = BigDecimal("1.12"),
          price = BigDecimal("1.11"),
          time_stamp = now.toInstant.toString
        ),
        forex.services.rates.interpreters.oneframe.Protocol.RateResponse(
          from = "GBP",
          to = "JPY",
          bid = BigDecimal("150"),
          ask = BigDecimal("152"),
          price = BigDecimal("151"),
          time_stamp = now.toInstant.toString
        )
      )

      val mockClient = mockHttpClient { _ =>
        IO.pure(responses)
      }

      val liveOneFrame = new LiveOneFrame[IO](mockClient, config)
      val result = liveOneFrame.getRates.unsafeRunSync()

      result shouldBe a[Right[_, _]]
      val rates = result.getOrElse(fail("Expected Right, got Left"))
      rates should have length 2
    }
  }

  private val config = OneFrameConfig(
    baseUrl = "http://api.example.com",
    token = "test-token"
  )

  private def validRateResponse(): List[
    forex.services.rates.interpreters.oneframe.Protocol.RateResponse
  ] = {
    val now = Instant.now().atOffset(ZoneOffset.UTC)
    List(
      forex.services.rates.interpreters.oneframe.Protocol.RateResponse(
        from = "USD",
        to = "EUR",
        bid = BigDecimal("1.10"),
        ask = BigDecimal("1.12"),
        price = BigDecimal("1.11"),
        time_stamp = now.toInstant.toString
      )
    )
  }

  private def mockHttpClient(
      handler: Request[IO] => IO[
        List[forex.services.rates.interpreters.oneframe.Protocol.RateResponse]
      ]
  ): Client[IO] = {
    Client[IO] { req =>
      Resource.eval[IO, Response[IO]] {
        handler(req).map { responses =>
          import io.circe.syntax._
          val json = responses.asJson.toString()
          Response[IO](Status.Ok).withEntity(json)
        }
      }
    }
  }

}
