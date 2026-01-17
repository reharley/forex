package forex.services.rates.interpreters.oneframe

object errors {

  sealed trait OneFrameError
  object OneFrameError {
    final case class InvalidPair(pair: String)                    extends OneFrameError
    case object RateLimitExceeded                                 extends OneFrameError
    final case class ServerError(statusCode: Int, msg: String)    extends OneFrameError
    final case class ConnectionError(msg: String)                 extends OneFrameError
    final case class UnexpectedResponse(msg: String)              extends OneFrameError
  }

}
