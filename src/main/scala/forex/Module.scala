package forex

import cats.effect.Clock
import cats.effect.ConcurrentEffect
import cats.effect.Resource
import cats.effect.Timer
import cats.syntax.either._
import cats.syntax.functor._
import forex.config.ApplicationConfig
import forex.domain.Rate
import forex.http.ErrorHandling
import forex.http.rates.RatesHttpRoutes
import forex.programs._
import forex.services.rates.Algebra
import forex.services.rates.errors.Error
import forex.services.rates.interpreters
import forex.services.rates.interpreters.oneframe.LiveOneFrame
import forex.services.rates.interpreters.oneframe.OneFrame
import forex.services.rates.interpreters.oneframe.OneFrameConfig
import org.http4s._
import org.http4s.blaze.client.BlazeClientBuilder
import org.http4s.implicits._
import org.http4s.server.middleware.AutoSlash
import org.http4s.server.middleware.Timeout

import scala.concurrent.ExecutionContext
import scala.concurrent.duration._

class Module[F[_]: ConcurrentEffect: Timer: Clock](config: ApplicationConfig) {

  def createRatesService(ec: ExecutionContext): Resource[F, Algebra[F]] =
    BlazeClientBuilder[F](ec).resource.flatMap { client =>
      Resource.eval {
        val oneFrameConfig = OneFrameConfig(
          baseUrl = config.oneFrame.baseUrl,
          token = config.oneFrame.token
        )
        val liveOneFrame = new LiveOneFrame[F](client, oneFrameConfig)

        // Wrap with caching
        for {
          cached <- interpreters.CachedRates[F](
            liveOneFrame,
            config.cache.ttlSeconds.seconds
          )
        } yield cached
      }
    }

  def getRatesProgram(ec: ExecutionContext): Resource[F, RatesProgram[F]] =
    createRatesService(ec).map(RatesProgram[F](_))

  def getHttpRoutes(ec: ExecutionContext): Resource[F, HttpRoutes[F]] =
    getRatesProgram(ec).map(prog => new RatesHttpRoutes[F](prog).routes)

  def getHttpApp(ec: ExecutionContext): Resource[F, HttpApp[F]] = {
    type PartialMiddleware = HttpRoutes[F] => HttpRoutes[F]
    type TotalMiddleware = HttpApp[F] => HttpApp[F]

    val routesMiddleware: PartialMiddleware = { http: HttpRoutes[F] =>
      AutoSlash(http)
    }

    val appMiddleware: TotalMiddleware = { http: HttpApp[F] =>
      val withErrorHandling = ErrorHandling.errorHandlingMiddleware(http)
      Timeout(config.http.timeout)(withErrorHandling)
    }

    getHttpRoutes(ec).map { routes =>
      appMiddleware(routesMiddleware(routes).orNotFound)
    }
  }

}

// Adapter to convert OneFrame service to Rates service
class DummyOneFrameAdapter[F[_]: ConcurrentEffect](oneFrame: OneFrame[F])
    extends Algebra[F] {

  override def get(pair: Rate.Pair): F[Either[Error, Rate]] =
    oneFrame.getRates.map {
      case Right(rates) =>
        rates
          .find(_.pair == pair)
          .map(_.asRight[Error])
          .getOrElse(
            Error
              .OneFrameLookupFailed(
                s"Rate not found for pair ${pair.from}${pair.to}"
              )
              .asLeft
          )
      case Left(err) =>
        Error.OneFrameLookupFailed(s"OneFrame error: $err").asLeft[Rate]
    }

}
