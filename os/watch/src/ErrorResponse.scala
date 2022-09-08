package os.watch

// How to react to service errors
sealed trait ErrorResponse

object ErrorResponse {
  // close this instnace of the watch service
  case object Close extends ErrorResponse
  
  // Other possibilities
  //case object Continue extends ErrorResponse // the caller fixed the state
  //case class Restart(paths: Seq[os.Path]) extends ErrorResponse // the caller knows

  def defaultHandler(e: WatchError): ErrorResponse = {
    println(e)
    Close
  }
}
