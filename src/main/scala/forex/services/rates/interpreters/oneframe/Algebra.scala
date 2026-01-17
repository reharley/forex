package forex.services.rates.interpreters.oneframe

import forex.domain.Rate
import errors.OneFrameError

trait OneFrame[F[_]] {
  def getRates: F[Either[OneFrameError, List[Rate]]]
}
