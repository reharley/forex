package forex.services.rates.interpreters.oneframe

import cats.MonadError
import cats.effect.ConcurrentEffect
import cats.syntax.either._
import cats.syntax.flatMap._
import cats.syntax.traverse._
import forex.domain.{Rate, Currency}
import Protocol.RateResponse
import org.http4s._
import org.http4s.circe.jsonOf
import org.http4s.client.Client
import org.typelevel.ci.CIString
import errors.OneFrameError

case class OneFrameConfig(
    baseUrl: String,
    token: String
)

class LiveOneFrame[F[_]: ConcurrentEffect](
    client: Client[F],
    config: OneFrameConfig
) extends OneFrame[F] {

  implicit val getResponseDecoder: EntityDecoder[F, List[RateResponse]] =
    jsonOf[F, List[RateResponse]]

  // All available currencies - sourced from Currency.all
  private val allCurrencies = Currency.all

  // Generate all possible currency pairs (excluding pairs where from == to)
  private def getAllPairs: List[Rate.Pair] =
    for {
      from <- allCurrencies
      to <- allCurrencies
      if from != to
    } yield Rate.Pair(from, to)

  def getRates: F[Either[OneFrameError, List[Rate]]] = {
    // Always fetch all possible pairs regardless of input
    val allPairs = getAllPairs
    val pairStrings: List[String] = allPairs.map(p => s"${p.from}${p.to}")
    val multiParams = pairStrings
      .map(p => "pair" -> p)
      .groupBy(_._1)
      .view
      .mapValues(_.map(_._2))
      .toMap
    val uri = Uri.fromString(config.baseUrl) match {
      case Right(u) =>
        u.withPath(Uri.Path.Root / "rates")
          .withMultiValueQueryParams(multiParams)
      case Left(e) => throw new Exception(s"Invalid base URL: $e")
    }
    val req = Request[F](Method.GET, uri)
      .withHeaders(
        Header.Raw(CIString("token"), config.token)
      )

    val me = MonadError[F, Throwable]

    me.recoverWith {
      client
        .expectOr[List[RateResponse]](req) { response =>
          response.status match {
            case Status.Unauthorized | Status.Forbidden =>
              response.as[String].flatMap { body =>
                me.raiseError(new Exception(s"Unauthorized: $body"))
              }
            case Status.TooManyRequests =>
              me.raiseError(new Exception("Rate limit exceeded"))
            case _ if response.status.code >= 500 =>
              response.as[String].flatMap { body =>
                me.raiseError(new Exception(s"Server error: $body"))
              }
            case _ =>
              response.as[String].flatMap { body =>
                me.raiseError(new Exception(s"Unexpected response: $body"))
              }
          }
        }
        .flatMap { rateResponses =>
          val rates: Either[OneFrameError, List[Rate]] = rateResponses
            .traverse { rateResp =>
              rateResp.toRate.leftMap(_ =>
                OneFrameError.InvalidPair(s"${rateResp.from}${rateResp.to}")
              )
            }

          me.pure(rates)
        }
    } { e =>
      me.pure {
        e match {
          case e: Throwable
              if e.getMessage != null && e.getMessage.contains(
                "Unauthorized"
              ) =>
            OneFrameError.ServerError(401, e.getMessage).asLeft
          case e: Throwable
              if e.getMessage != null && e.getMessage.contains("Rate limit") =>
            OneFrameError.RateLimitExceeded.asLeft
          case e: Throwable
              if e.getMessage != null && e.getMessage.contains(
                "Server error"
              ) =>
            OneFrameError.ServerError(500, e.getMessage).asLeft
          case e: Throwable
              if e.getMessage != null && e.getMessage.contains("JSON") =>
            OneFrameError.UnexpectedResponse("Invalid JSON response").asLeft
          case e: Throwable =>
            OneFrameError
              .ConnectionError(Option(e.getMessage).getOrElse(e.toString))
              .asLeft
          case e =>
            OneFrameError.ConnectionError(e.toString).asLeft
        }
      }
    }
  }

}
