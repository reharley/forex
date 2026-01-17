package forex.programs.rates

import forex.services.rates.errors.{Error => RatesServiceError}

object errors {

  sealed trait Error extends Exception {
    def message: String
  }
  object Error {
    final case class RateLookupFailed(msg: String) extends Error {
      def message: String = msg
    }
  }

  def toProgramError(error: RatesServiceError): Error = error match {
    case RatesServiceError.OneFrameLookupFailed(msg) =>
      Error.RateLookupFailed(msg)
    case RatesServiceError.InvalidPair(pair) =>
      Error.RateLookupFailed(s"Invalid pair: $pair")
    case RatesServiceError.RateLimitExceeded() =>
      Error.RateLookupFailed("Rate limit exceeded")
    case RatesServiceError.ServiceUnavailable(msg) =>
      Error.RateLookupFailed(msg)
  }
}
