package os

import java.io.{BufferedReader, InputStreamReader}
import java.nio.ByteBuffer
import java.nio.channels.{Channels, FileChannel, SeekableByteChannel}
import java.nio.file.attribute.{FileAttribute, PosixFilePermission, PosixFilePermissions}
import java.nio.file.{Files, OpenOption, StandardOpenOption}

import scala.io.Codec
import StandardOpenOption.{CREATE, WRITE}

/**
 * Write some data to a file. This can be a String, an Array[Byte], or a
 * Seq[String] which is treated as consecutive lines. By default, this
 * fails if a file already exists at the target location. Use [[write.over]]
 * or [[write.append]] if you want to over-write it or add to what's already
 * there.
 */
object write {

  /**
   * Open a [[java.io.OutputStream]] to write to the given file
   */
  def outputStream(
      target: Path,
      perms: PermSet = null,
      createFolders: Boolean = false,
      openOptions: Seq[OpenOption] = Seq(CREATE, WRITE)
  ) = {
    checker.value.onWrite(target)
    if (createFolders) makeDir.all(target / RelPath.up, perms)
    if (perms != null && !exists(target)) {
      val permArray =
        if (perms == null) Array[FileAttribute[PosixFilePermission]]()
        else Array(PosixFilePermissions.asFileAttribute(perms.toSet()))
      java.nio.file.Files.createFile(target.toNIO, permArray: _*)
    }

    java.nio.file.Files.newOutputStream(
      target.toNIO,
      openOptions.toArray: _*
    )
  }

  /**
   * Performs the actual opening and writing to a file. Basically cribbed
   * from `java.nio.file.Files.write` so we could re-use it properly for
   * different combinations of flags and all sorts of [[Source]]s
   */
  def write(
      target: Path,
      data: Source,
      flags: Seq[StandardOpenOption],
      perms: PermSet,
      offset: Long
  ) = {
    checker.value.onWrite(target)

    import collection.JavaConverters._
    val permArray: Array[FileAttribute[_]] =
      if (perms == null) Array.empty
      else Array(PosixFilePermissions.asFileAttribute(perms.toSet()))

    val out = Files.newByteChannel(
      target.wrapped,
      flags.toSet.asJava,
      permArray: _*
    )
    out.position(offset)
    try data.writeBytesTo(out)
    finally if (out != null) out.close()
  }
  def apply(
      target: Path,
      data: Source,
      perms: PermSet = null,
      createFolders: Boolean = false
  ): Unit = {
    if (createFolders) makeDir.all(target / RelPath.up, perms)
    write(target, data, Seq(StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE), perms, 0)
  }

  /**
   * Identical to [[write]], except if the file already exists,
   * appends to the file instead of error-ing out
   */
  object append {
    def apply(
        target: Path,
        data: Source,
        perms: PermSet = null,
        createFolders: Boolean = false
    ): Unit = {
      if (createFolders) makeDir.all(target / RelPath.up, perms)
      write(
        target,
        data,
        Seq(StandardOpenOption.CREATE, StandardOpenOption.APPEND, StandardOpenOption.WRITE),
        perms,
        0
      )
    }

    /**
     * Open a [[java.io.OutputStream]] to write or append to the given file
     */
    def outputStream(target: Path, perms: PermSet = null, createFolders: Boolean = false) = {
      os.write.outputStream(
        target,
        perms,
        createFolders,
        Seq(
          StandardOpenOption.CREATE,
          StandardOpenOption.WRITE,
          StandardOpenOption.APPEND
        )
      )
    }
  }

  /**
   * Similar to [[os.write]], except if the file already exists this
   * over-writes the existing file contents. You can also pass in `truncate = false`
   * to avoid truncating the file if the new contents is shorter than the old
   * contents, and an `offset` to the file you want to write to.
   */
  object over {
    def apply(
        target: Path,
        data: Source,
        perms: PermSet = null,
        offset: Long = 0,
        createFolders: Boolean = false,
        truncate: Boolean = true
    ): Unit = {
      if (createFolders) makeDir.all(target / RelPath.up, perms)
      write(
        target,
        data,
        Seq(
          StandardOpenOption.CREATE,
          StandardOpenOption.WRITE
        ) ++ (if (truncate) Seq(StandardOpenOption.TRUNCATE_EXISTING) else Nil),
        perms,
        offset
      )
    }

    /**
     * Open a [[java.io.OutputStream]] to write to the given file
     */
    def outputStream(target: Path, perms: PermSet = null, createFolders: Boolean = false) = {
      os.write.outputStream(
        target,
        perms,
        createFolders,
        Seq(
          StandardOpenOption.CREATE,
          StandardOpenOption.WRITE,
          StandardOpenOption.TRUNCATE_EXISTING
        )
      )
    }
  }

  /**
   * Opens a [[SeekableByteChannel]] to write to the given file.
   */
  object channel extends Function1[Path, SeekableByteChannel] {
    def write(p: Path, options: Seq[StandardOpenOption]) = {
      checker.value.onWrite(p)
      java.nio.file.Files.newByteChannel(p.toNIO, options.toArray: _*)
    }
    def apply(p: Path): SeekableByteChannel = {
      write(p, Seq(StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE))
    }

    /**
     * Opens a [[SeekableByteChannel]] to write to the given file.
     */
    object append extends Function1[Path, SeekableByteChannel] {
      def apply(p: Path): SeekableByteChannel = {
        write(
          p,
          Seq(
            StandardOpenOption.CREATE,
            StandardOpenOption.APPEND,
            StandardOpenOption.WRITE
          )
        )
      }
    }

    /**
     * Opens a [[SeekableByteChannel]] to write to the given file.
     */
    object over extends Function1[Path, SeekableByteChannel] {
      def apply(p: Path): SeekableByteChannel = {
        write(
          p,
          Seq(
            StandardOpenOption.CREATE,
            StandardOpenOption.TRUNCATE_EXISTING,
            StandardOpenOption.WRITE
          )
        )
      }
    }
  }
}

/**
 * Truncate the given file to the given size. If the file is smaller than the
 * given size, does nothing.
 */
object truncate {
  def apply(p: Path, size: Long): Unit = {
    checker.value.onWrite(p)
    val channel = FileChannel.open(p.toNIO, StandardOpenOption.WRITE)
    try channel.truncate(size)
    finally channel.close()
  }
}

/**
 * Reads the contents of a [[os.Path]] or other [[os.Source]] as a
 * `java.lang.String`. Defaults to reading the entire file as UTF-8, but you can
 * also select a different `charSet` to use, and provide an `offset`/`count` to
 * read from if the source supports seeking.
 */
object read extends Function1[ReadablePath, String] {
  def apply(arg: ReadablePath): String = apply(arg, java.nio.charset.StandardCharsets.UTF_8)
  def apply(arg: ReadablePath, charSet: Codec): String = {
    new String(read.bytes(arg), charSet.charSet)
  }
  def apply(
      arg: Path,
      charSet: Codec = java.nio.charset.StandardCharsets.UTF_8,
      offset: Long = 0,
      count: Int = Int.MaxValue
  ): String = {
    new String(read.bytes(arg, offset, count), charSet.charSet)
  }

  /**
   * Opens a [[java.io.InputStream]] to read from the given file
   */
  object inputStream extends Function1[ReadablePath, java.io.InputStream] {
    def apply(p: ReadablePath): java.io.InputStream = {
      checker.value.onRead(p)
      p.getInputStream
    }
  }

  object stream extends Function1[ReadablePath, geny.Readable] {
    def apply(p: ReadablePath): geny.Readable = {
      new geny.Readable {
        override def contentLength: Option[Long] = p.toSource.contentLength
        def readBytesThrough[T](f: java.io.InputStream => T): T = {
          val is = os.read.inputStream(p)
          try f(is)
          finally is.close()
        }
      }
    }
  }

  /**
   * Opens a [[SeekableByteChannel]] to read from the given file.
   */
  object channel extends Function1[Path, SeekableByteChannel] {
    def apply(p: Path): SeekableByteChannel = {
      checker.value.onRead(p)
      p.toSource.getChannel()
    }
  }

  /**
   * Reads the contents of a [[os.Path]] or [[os.Source]] as an
   * `Array[Byte]`; you can provide an `offset`/`count` to read from if the source
   * supports seeking.
   */
  object bytes extends Function1[ReadablePath, Array[Byte]] {
    def apply(arg: ReadablePath): Array[Byte] = {
      val out = new java.io.ByteArrayOutputStream()
      val stream = os.read.inputStream(arg)
      try Internals.transfer(stream, out)
      finally stream.close()
      out.toByteArray
    }
    def apply(arg: Path, offset: Long, count: Int): Array[Byte] = {
      val arr = new Array[Byte](count)
      val buf = ByteBuffer.wrap(arr)
      val channel = os.read.channel(arg)
      try {
        channel.position(offset)
        val finalCount = channel.read(buf)
        if (finalCount == arr.length) arr
        else arr.take(finalCount)
      } finally {
        channel.close()
      }
    }
  }

  /**
   * Reads the contents of the given [[os.Path]] in chunks of the given size;
   * returns a generator which provides a byte array and an offset into that
   * array which contains the data for that chunk. All chunks will be of the
   * given size, except for the last chunk which may be smaller.
   *
   * Note that the array returned by the generator is shared between each
   * callback; make sure you copy the bytes/array somewhere else if you want
   * to keep them around.
   *
   * Optionally takes in a provided input `buffer` instead of a `chunkSize`,
   * allowing you to re-use the buffer between invocations.
   */
  object chunks {
    def apply(p: ReadablePath, chunkSize: Int): geny.Generator[(Array[Byte], Int)] = {
      apply(p, new Array[Byte](chunkSize))
    }
    def apply(p: ReadablePath, buffer: Array[Byte]): geny.Generator[(Array[Byte], Int)] = {
      new Generator[(Array[Byte], Int)] {
        def generate(handleItem: ((Array[Byte], Int)) => Generator.Action): Generator.Action = {
          val is = os.read.inputStream(p)
          try {
            var bufferOffset = 0
            var lastAction: Generator.Action = Generator.Continue
            while ({
              is.read(buffer, bufferOffset, buffer.length - bufferOffset) match {
                case -1 =>
                  if (bufferOffset != 0) lastAction = handleItem((buffer, bufferOffset))
                  false
                case n =>
                  if (n + bufferOffset == buffer.length) {
                    lastAction = handleItem((buffer, buffer.length))
                    bufferOffset = 0
                  } else {
                    bufferOffset += n
                  }
                  lastAction == Generator.Continue
              }
            }) ()
            lastAction
          } finally {
            is.close()
          }
        }
      }
    }
  }

  /**
   * Reads the given [[os.Path]] or other [[os.Source]] as a string
   * and splits it into lines; defaults to reading as UTF-8, which you
   * can override by specifying a `charSet`.
   */
  object lines extends Function1[ReadablePath, IndexedSeq[String]] {
    def apply(src: ReadablePath): IndexedSeq[String] = stream(src).toArray[String].toIndexedSeq
    def apply(arg: ReadablePath, charSet: Codec): IndexedSeq[String] =
      stream(arg, charSet).toArray[String].toIndexedSeq

    /**
     * Identical to [[os.read.lines]], but streams the results back to you
     * in a [[os.Generator]] rather than accumulating them in memory. Useful
     * if the file is large.
     */
    object stream extends Function1[ReadablePath, geny.Generator[String]] {
      def apply(arg: ReadablePath) = apply(arg, java.nio.charset.StandardCharsets.UTF_8)

      def apply(arg: ReadablePath, charSet: Codec) = {
        new geny.Generator[String] {
          def generate(handleItem: String => Generator.Action) = {
            val is = os.read.inputStream(arg)
            val isr = new InputStreamReader(is, charSet.decoder)
            val buf = new BufferedReader(isr)
            var currentAction: Generator.Action = Generator.Continue
            var looping = true
            try {
              while (looping) {
                buf.readLine() match {
                  case null => looping = false
                  case s =>
                    handleItem(s) match {
                      case Generator.Continue => // go around again
                      case Generator.End =>
                        currentAction = Generator.End
                        looping = false
                    }
                }
              }
              currentAction
            } finally {
              is.close()
              isr.close()
              buf.close()
            }
          }
        }
      }
    }
  }
}
