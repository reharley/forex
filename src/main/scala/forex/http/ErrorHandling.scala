package forex.http

import cats.effect.Sync
import cats.syntax.applicativeError._
import io.circe.Encoder
import io.circe.generic.extras.Configuration
import io.circe.generic.extras.semiauto.deriveConfiguredEncoder
import org.http4s.HttpApp
import org.http4s.Response
import org.http4s.Status

object ErrorHandling {

  final case class ErrorResponse(error: String)

  object ErrorResponse {
    implicit val configuration: Configuration =
      Configuration.default.withSnakeCaseMemberNames
    implicit val encoder: Encoder[ErrorResponse] =
      deriveConfiguredEncoder[ErrorResponse]
  }

  def errorHandlingMiddleware[F[_]: Sync](http: HttpApp[F]): HttpApp[F] = {
    http.mapF { fa =>
      fa.handleErrorWith { error =>
        Sync[F].pure(
          Response[F](status = Status.InternalServerError).withEntity(
            ErrorResponse(
              Option(error.getMessage).getOrElse(error.getClass.getSimpleName)
            )
          )
        )
      }
    }
  }

}
