package os


object GlobInterpolator{
  class Interped(parts: Seq[String]){
    def unapplySeq(s: String) = {
      val Seq(head, tail@_*) = parts.map(java.util.regex.Pattern.quote)

      val regex = head + tail.map("(.*)" + _).mkString
      regex.r.unapplySeq(s)
    }
  }
}

/**
  * Lets you pattern match strings with interpolated glob-variables
  */
class GlobInterpolator(sc: StringContext) {
  def g(parts: Any*) = new StringContext(sc.parts:_*).s(parts:_*)
  def g = new GlobInterpolator.Interped(sc.parts)
}