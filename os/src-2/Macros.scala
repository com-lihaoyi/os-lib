package os

import scala.reflect.macros.blackbox
import os.PathChunk.segmentsFromString

import scala.language.experimental.macros
import acyclic.skipped

// StringPathChunkConversion is a fallback to non-macro String => PathChunk implicit conversion in case eta expansion is needed, this is required for ArrayPathChunk and SeqPathChunk
trait PathChunkMacros extends StringPathChunkConversion {
  implicit def stringPathChunkValidated(s: String): PathChunk =
    macro Macros.stringPathChunkValidatedImpl
}

object Macros {

  def stringPathChunkValidatedImpl(c: blackbox.Context)(s: c.Expr[String]): c.Expr[PathChunk] = {
    import c.universe._

    s match {
      case Expr(Literal(Constant(literal: String))) =>
        val stringSegments = segmentsFromString(literal)
        stringSegments.foreach(BasePath.checkSegment)

        c.Expr(
          q"new _root_.os.PathChunk.ArrayPathChunk[String]($stringSegments)(_root_.os.PathChunk.stringToPathChunk)"
        )
      case nonLiteral =>
        c.Expr(
          q"new _root_.os.PathChunk.StringPathChunk($nonLiteral)"
        )
    }
  }
}
