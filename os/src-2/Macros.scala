package os

import os.PathChunk.segmentsFromStringLiteralValidation

import scala.language.experimental.macros
import scala.reflect.macros.blackbox
import acyclic.skipped

// StringPathChunkConversion is a fallback to non-macro String => PathChunk implicit conversion in case eta expansion is needed, this is required for ArrayPathChunk and SeqPathChunk
trait PathChunkMacros extends StringPathChunkConversion {
  implicit def stringPathChunkValidated(s: String): PathChunk =
    macro Macros.stringPathChunkValidatedImpl
}

object Macros {

  def stringPathChunkValidatedImpl(c: blackbox.Context)(s: c.Expr[String]): c.Expr[PathChunk] = {
    import c.universe.{Try => _, _}

    s match {
      case Expr(Literal(Constant(literal: String))) =>
        val stringSegments = segmentsFromStringLiteralValidation(literal)

        c.Expr(
          q"""new _root_.os.PathChunk.RelPathChunk(_root_.os.RelPath.fromStringSegments($stringSegments))"""
        )
      case nonLiteral =>
        c.Expr(
          q"new _root_.os.PathChunk.StringPathChunk($nonLiteral)"
        )
    }
  }
}
