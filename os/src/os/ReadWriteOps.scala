package os

import java.io.{BufferedReader, InputStreamReader}
import java.nio.file.{Files, StandardOpenOption}

import geny.Generator

import scala.io.Codec


/**
  * Write some data to a file. This can be a String, an Array[Byte], or a
  * Seq[String] which is treated as consecutive lines. By default, this
  * fails if a file already exists at the target location. Use [[write.over]]
  * or [[write.append]] if you want to over-write it or add to what's already
  * there.
  */
object write extends Function2[Path, Source, Unit]{
  /**
    * Performs the actual opening and writing to a file. Basically cribbed
    * from `java.nio.file.Files.write` so we could re-use it properly for
    * different combinations of flags and all sorts of [[Source]]s
    */
  def write(target: Path, data: Source, flags: StandardOpenOption*) = {

    val out = Files.newOutputStream(target.toNIO, flags:_*)
    try Internals.transfer(data.getInputStream(), out)
    finally if (out != null) out.close()
  }
  def apply(target: Path, data: Source) = {
    makeDirs(target/RelPath.up)
    write(target, data, StandardOpenOption.CREATE_NEW)
  }

  /**
    * Identical to [[write]], except if the file already exists,
    * appends to the file instead of error-ing out
    */
  object append extends Function2[Path, Source, Unit]{
    def apply(target: Path, data: Source) = {
      makeDirs(target/RelPath.up)
      write(target, data, StandardOpenOption.CREATE, StandardOpenOption.APPEND)
    }
  }
  /**
    * Identical to [[write]], except if the file already exists,
    * replaces the file instead of error-ing out
    */
  object over extends Function2[Path, Source, Unit]{
    def apply(target: Path, data: Source) = {
      makeDirs(target/RelPath.up)
      write(target, data, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)
    }
  }
}


/**
  * Reads a file into memory, either as a String,
  * as (read.lines(...): Seq[String]), or as (read.bytes(...): Array[Byte]).
  */
object read extends Function1[Source, String]{
  def getInputStream(p: Source) = p.getInputStream()

  def apply(arg: Source) = apply(arg, java.nio.charset.StandardCharsets.UTF_8)
  def apply(arg: Source, charSet: Codec) = {
    new String(read.bytes(arg), charSet.charSet)
  }

  object lines extends Internals.StreamableOp1[Source, String, IndexedSeq[String]]{
    def materialize(src: Source, i: geny.Generator[String]) = i.toArray[String]

    object iter extends (Source => geny.Generator[String]){
      def apply(arg: Source) = apply(arg, java.nio.charset.StandardCharsets.UTF_8)

      def apply(arg: Source, charSet: Codec) = {
        new geny.Generator[String]{
          def generate(handleItem: String => Generator.Action) = {
            val is = arg.getInputStream()
            val isr = new InputStreamReader(is)
            val buf = new BufferedReader(isr)
            var currentAction: Generator.Action = Generator.Continue
            var looping = true
            try{
              while(looping){
                buf.readLine() match{
                  case null => looping = false
                  case s =>
                    handleItem(s) match{
                      case Generator.Continue => // go around again
                      case Generator.End =>
                        currentAction = Generator.End
                        looping = false
                    }
                }
              }
              currentAction
            } finally{
              is.close()
              isr.close()
              buf.close()
            }
          }
        }
      }
    }

    def apply(arg: Source, charSet: Codec) = materialize(arg, iter(arg, charSet))
  }

  object bytes extends Function1[Source, Array[Byte]]{
    def apply(arg: Source) = {
      val out = new java.io.ByteArrayOutputStream()
      Internals.transfer(arg.getInputStream(), out)
      out.toByteArray
    }
  }
}