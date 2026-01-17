package forex.services.rates

object errors {

  sealed trait Error {
    def message: String
  }

  object Error {
    final case class OneFrameLookupFailed(msg: String) extends Error {
      def message: String = s"OneFrame lookup failed: $msg"
    }
    final case class InvalidPair(pair: String) extends Error {
      def message: String = s"Invalid currency pair: $pair"
    }
    final case class RateLimitExceeded() extends Error {
      def message: String = "Rate limit exceeded"
    }
    final case class ServiceUnavailable(msg: String) extends Error {
      def message: String = s"Service unavailable: $msg"
    }
  }

}
