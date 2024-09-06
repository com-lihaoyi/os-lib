package os

import scala.reflect.macros.blackbox
import os.PathChunk.{SubPathChunk, segmentsFromString}

import scala.language.experimental.macros
import acyclic.skipped

// StringPathChunkConversion is a fallback to non-macro String => PathChunk implicit conversion in case eta expansion is needed, this is required for ArrayPathChunk and SeqPathChunk
trait PathChunkMacros extends StringPathChunkConversion {
  implicit def stringPathChunkValidated(s: String): PathChunk =
    macro Macros.stringPathChunkValidatedImpl
}

object Macros {

  def stringPathChunkValidatedImpl(c: blackbox.Context)(s: c.Expr[String]): c.Expr[SubPathChunk] = {
    import c.universe._

    s match {
      case Expr(Literal(Constant(literal: String))) =>
        val stringSegments = segmentsFromString(literal)
        stringSegments.foreach(BasePath.checkSegment)

        c.Expr(
          q"new _root_.os.PathChunk.SubPathChunk(_root_.os.SubPath.apply(${stringSegments}.toIndexedSeq))"
        )
      case nonLiteral =>
        c.Expr(
          q"new _root_.os.PathChunk.SubPathChunk(_root_.os.SubPath.apply(IndexedSeq($nonLiteral)))"
        )
    }
  }
}
