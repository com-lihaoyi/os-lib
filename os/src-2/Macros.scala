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
trait SubPathMacros extends StringPathChunkConversion {
  implicit def stringSubPathValidated(s: String): SubPath =
    macro Macros.stringSubPathValidatedImpl
}
trait RelPathMacros extends StringPathChunkConversion {
  implicit def stringRelPathValidated(s: String): RelPath =
    macro Macros.stringRelPathValidatedImpl
}
trait PathMacros extends StringPathChunkConversion {
  implicit def stringPathValidated(s: String): Path =
    macro Macros.stringPathValidatedImpl
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
  def stringSubPathValidatedImpl(c: blackbox.Context)(s: c.Expr[String]): c.Expr[SubPath] = {
    import c.universe.{Try => _, _}

    s match {
      case Expr(Literal(Constant(literal: String))) if !literal.startsWith("/") =>
        val stringSegments = segmentsFromStringLiteralValidation(literal)

        if(stringSegments.startsWith(Seq(".."))) {
          c.abort(s.tree.pos, "Invalid subpath literal: " + s.tree)
        }
        c.Expr(q"""os.sub / _root_.os.RelPath.fromStringSegments($stringSegments)""")

      case _ => c.abort(s.tree.pos, "Invalid subpath literal: " + s.tree)
    }
  }
  def stringRelPathValidatedImpl(c: blackbox.Context)(s: c.Expr[String]): c.Expr[RelPath] = {
    import c.universe.{Try => _, _}

    s match {
      case Expr(Literal(Constant(literal: String))) if !literal.startsWith("/") =>
        val stringSegments = segmentsFromStringLiteralValidation(literal)
        c.Expr(q"""os.rel / _root_.os.RelPath.fromStringSegments($stringSegments)""")

      case _ => c.abort(s.tree.pos, "Invalid relative path literal: " + s.tree)
    }
  }
  def stringPathValidatedImpl(c: blackbox.Context)(s: c.Expr[String]): c.Expr[Path] = {
    import c.universe.{Try => _, _}

    s match {
      case Expr(Literal(Constant(literal: String))) if literal.startsWith("/") =>
        val stringSegments = segmentsFromStringLiteralValidation(literal.stripPrefix("/"))

        c.Expr(q"""os.root / _root_.os.RelPath.fromStringSegments($stringSegments)""")

      case _ => c.abort(s.tree.pos, "Invalid absolute path literal: " + s.tree)
    }
  }
}
