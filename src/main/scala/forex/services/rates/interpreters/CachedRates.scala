package forex.services.rates.interpreters

import cats.effect.Clock
import cats.effect.Sync
import cats.effect.concurrent.Ref
import cats.syntax.flatMap._
import cats.syntax.functor._
import forex.domain.Rate
import forex.services.rates.Algebra
import forex.services.rates.errors.Error
import forex.services.rates.interpreters.oneframe.OneFrame

import scala.concurrent.duration.FiniteDuration

class CachedRates[F[_]: Sync: Clock](
    oneFrame: OneFrame[F],
    cacheTTL: FiniteDuration,
    cacheRef: Ref[F, Map[String, (Rate, Long)]]
) extends Algebra[F] {

  def get(pair: Rate.Pair): F[Either[Error, Rate]] = {
    val pairKey = s"${pair.from}${pair.to}"

    for {
      now <- Clock[F].realTime(scala.concurrent.duration.MILLISECONDS)
      cache <- cacheRef.get
      result <- cache.get(pairKey) match {
        case Some((rate, expiry)) if now < expiry =>
          // Cache hit - return cached rate
          Sync[F].pure(Right(rate))
        case _ =>
          // Cache miss or expired - fetch ALL pairs from oneFrame
          fetchAndCacheAllPairs(now).map { cachedRates =>
            cachedRates
              .get(pairKey)
              .toRight(Error.ServiceUnavailable("Service unavailable"))
              .map(_._1)
          }
      }
    } yield result
  }

  private def fetchAndCacheAllPairs(now: Long): F[Map[String, (Rate, Long)]] = {
    oneFrame.getRates
      .flatMap {
        case Right(rates) =>
          val newExpiry = now + (cacheTTL.toMillis)
          val cachedRates = rates.map { rate =>
            val key = s"${rate.pair.from}${rate.pair.to}"
            (key, (rate, newExpiry))
          }.toMap: Map[String, (Rate, Long)]
          cacheRef.update(_ ++ cachedRates).as(cachedRates)
        case Left(_) =>
          Sync[F].pure(Map.empty)
      }
  }

}

object CachedRates {

  def apply[F[_]: Sync: Clock](
      oneFrame: OneFrame[F],
      cacheTTL: FiniteDuration
  ): F[Algebra[F]] =
    Ref.of[F, Map[String, (Rate, Long)]](Map.empty).map { cacheRef =>
      new CachedRates[F](oneFrame, cacheTTL, cacheRef)
    }

}
