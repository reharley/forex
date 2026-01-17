package forex.services.rates.interpreters.oneframe

import forex.domain.{ Currency, Price, Rate, Timestamp }
import io.circe.{ Decoder, Encoder }
import io.circe.generic.semiauto.{ deriveDecoder, deriveEncoder }

object Protocol {

  final case class GetRatesRequest(pairs: List[String])

  final case class RateResponse(
      from: String,
      to: String,
      bid: BigDecimal,
      ask: BigDecimal,
      price: BigDecimal,
      time_stamp: String
  ) {

    def toRate: Either[String, Rate] = {
      for {
        from <- Currency.fromString(this.from)
        to <- Currency.fromString(this.to)
        timestamp <- try {
          Right(java.time.Instant.parse(this.time_stamp).atOffset(java.time.ZoneOffset.UTC))
        } catch {
          case _: Exception => Left(s"Invalid timestamp: ${this.time_stamp}")
        }
      } yield Rate(
        pair = Rate.Pair(from, to),
        price = Price(price),
        timestamp = Timestamp(timestamp)
      )
    }

  }

  object RateResponse {
    implicit val decoder: Decoder[RateResponse] = deriveDecoder[RateResponse]
    implicit val encoder: Encoder[RateResponse] = deriveEncoder[RateResponse]
  }

  final case class GetRatesResponse(rates: List[RateResponse])

  object GetRatesResponse {
    implicit val decoder: Decoder[GetRatesResponse] = deriveDecoder[GetRatesResponse]
    implicit val encoder: Encoder[GetRatesResponse] = deriveEncoder[GetRatesResponse]
  }

}
