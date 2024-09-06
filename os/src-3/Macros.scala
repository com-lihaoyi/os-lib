package os

import os.PathChunk.{ArrayPathChunk, StringPathChunk, segmentsFromString, stringToPathChunk}

import scala.quoted.{Expr, Quotes}

// StringPathChunkConversion is a fallback to non-macro String => PathChunk implicit conversion in case eta expansion is needed, this is required for ArrayPathChunk and SeqPathChunk
trait PathChunkMacros extends StringPathChunkConversion {
  inline implicit def stringPathChunkValidated(s: String): PathChunk =
    ${ Macros.stringPathChunkValidatedImpl('s) }
}

object Macros {
  def stringPathChunkValidatedImpl(s: Expr[String])(using quotes: Quotes): Expr[PathChunk] = {
    import quotes.reflect.*

    s.asTerm match {
      case Inlined(_, _, Literal(StringConstant(literal))) =>
        val stringSegments = segmentsFromString(literal)
        stringSegments.foreach(BasePath.checkSegment)

        '{ new ArrayPathChunk[String](${ Expr(stringSegments) })(using stringToPathChunk) }
      case _ =>
        '{ { new StringPathChunk($s) } }
    }
  }
}
