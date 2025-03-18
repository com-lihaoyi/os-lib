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
trait SubPathMacros extends StringPathChunkConversion {
  inline implicit def stringSubPathValidated(s: String): SubPath =
    ${
      Macros.stringSubPathValidatedImpl('s)
    }
}
trait RelPathMacros extends StringPathChunkConversion {
  inline implicit def stringRelPathValidated(s: String): RelPath =
    ${
      Macros.stringRelPathValidatedImpl('s)
    }
}
trait PathMacros extends StringPathChunkConversion {
  inline implicit def stringPathValidated(s: String): Path =
    ${
      Macros.stringPathValidatedImpl('s)
    }
}

object Macros {
  def stringPathChunkValidatedImpl(s: Expr[String])(using quotes: Quotes): Expr[PathChunk] = {
    import quotes.reflect.*

    s.asTerm match {
      case Inlined(_, _, Literal(StringConstant(literal))) =>
        val segments = segmentsFromStringLiteralValidation(literal)
        '{
          new RelPathChunk(fromStringSegments(${Expr(segments)}))
        }
      case _ =>
        '{
          {
            new StringPathChunk($s)
          }
        }
    }
  }
  def stringSubPathValidatedImpl(s: Expr[String])(using quotes: Quotes): Expr[SubPath] = {
    import quotes.reflect.*

    s.asTerm match {
      case Inlined(_, _, Literal(StringConstant(literal))) if !literal.startsWith("/") =>
        val stringSegments = segmentsFromStringLiteralValidation(literal)
        if (stringSegments.startsWith(Seq(".."))) {
          report.errorAndAbort("Invalid subpath literal: " + s.show)
        }
        '{ os.sub / fromStringSegments(${Expr(stringSegments)}) }
      case _ => report.errorAndAbort("Invalid subpath literal: " + s.show)

    }
  }
  def stringRelPathValidatedImpl(s: Expr[String])(using quotes: Quotes): Expr[RelPath] = {
    import quotes.reflect.*

    s.asTerm match {
      case Inlined(_, _, Literal(StringConstant(literal))) if !literal.startsWith("/") =>
        val segments = segmentsFromStringLiteralValidation(literal)
        '{ fromStringSegments(${Expr(segments)}) }
      case _ => report.errorAndAbort("Invalid relative path literal: " + s.show)
    }
  }
  def stringPathValidatedImpl(s: Expr[String])(using quotes: Quotes): Expr[Path] = {
    import quotes.reflect.*

    s.asTerm match {
      case Inlined(_, _, Literal(StringConstant(literal))) if literal.startsWith("/") =>
        val segments = segmentsFromStringLiteralValidation(literal.stripPrefix("/"))
        '{ os.root / fromStringSegments(${Expr(segments)}) }
      case _ => report.errorAndAbort("Invalid absolute path literal: " + s.show)
    }
  }
}
