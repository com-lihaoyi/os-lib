package os

import os.PathChunk.{RelPathChunk, StringPathChunk, segmentsFromString, segmentsFromStringLiteralValidation}
import os.RelPath.fromStringSegments

import scala.quoted.{Expr, Quotes}
import acyclic.skipped

// StringPathChunkConversion is a fallback to non-macro String => PathChunk implicit conversion in case eta expansion is needed, this is required for ArrayPathChunk and SeqPathChunk
trait PathChunkMacros extends StringPathChunkConversion {
  inline implicit def stringPathChunkValidated(s: String): PathChunk =
    ${
      Macros.stringPathChunkValidatedImpl('s)
    }
}

object Macros {
  def stringPathChunkValidatedImpl(s: Expr[String])(using quotes: Quotes): Expr[PathChunk] = {
    import quotes.reflect.*

    s.asTerm match {
      case Inlined(_, _, Literal(StringConstant(literal))) =>
        segmentsFromStringLiteralValidation(literal)
        '{
          new RelPathChunk(fromStringSegments(segmentsFromString($s)))
        }
      case _ =>
        '{
          {
            new StringPathChunk($s)
          }
        }
    }
  }
}
