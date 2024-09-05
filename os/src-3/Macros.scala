package os

import os.Macros.validatedPathChunkImpl
import os.PathChunk.{SubPathChunk, segmentsFromString}

import scala.collection.immutable.IndexedSeq
import scala.quoted.{Expr, Quotes}

trait PathChunkMacros extends ViewBoundImplicit{
  inline implicit def validatedStringChunk(s:String): PathChunk = ${validatedPathChunkImpl('s)}
}

object Macros {
  def validatedPathChunkImpl(s:Expr[String])(using quotes: Quotes): Expr[SubPathChunk] = {
    import quotes.reflect.*

    s.asTerm match {
      case Inlined(_, _, Literal(StringConstant(literal))) =>
        val stringSegments = segmentsFromString(literal)
        stringSegments.foreach(BasePath.checkSegment)

        '{new SubPathChunk(SubPath.apply(${Expr(stringSegments)}.toIndexedSeq))}
      case _ =>
        '{{new SubPathChunk(SubPath.apply(IndexedSeq($s)))}}
    }
  }
}
