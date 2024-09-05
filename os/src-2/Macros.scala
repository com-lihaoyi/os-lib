package os

import scala.reflect.macros.blackbox
import os.PathChunk.{SubPathChunk, segmentsFromString}

import scala.language.experimental.macros
import acyclic.skipped

trait PathChunkMacros extends ViewBoundImplicit {
  implicit def validatedStringChunk(s: String): PathChunk = macro Macros.validatedStringChunkImpl
}

object Macros {

  def validatedStringChunkImpl(c: blackbox.Context)(s: c.Expr[String]): c.Expr[SubPathChunk] = {
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
