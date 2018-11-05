package os

import java.io.{BufferedReader, InputStreamReader}
import java.nio.ByteBuffer
import java.nio.channels.{Channels, FileChannel, SeekableByteChannel}
import java.nio.file.attribute.{FileAttribute, PosixFilePermission, PosixFilePermissions}
import java.nio.file.{Files, OpenOption, StandardOpenOption}

import geny.Generator

import scala.io.Codec


/**
  * Write some data to a file. This can be a String, an Array[Byte], or a
  * Seq[String] which is treated as consecutive lines. By default, this
  * fails if a file already exists at the target location. Use [[write.over]]
  * or [[write.append]] if you want to over-write it or add to what's already
  * there.
  */
object write{
  /**
    * Open a [[java.io.OutputStream]] to write to the given file
    */
  def outputStream(target: Path,
                   perms: PermSet = null,
                   createFolders: Boolean = true,
                   openOptions: Seq[OpenOption] =
                     Seq(StandardOpenOption.CREATE, StandardOpenOption.WRITE)) = {
    if (createFolders) makeDir.all(target/RelPath.up, perms)
    if (perms != null && !exists(target)){
      import collection.JavaConverters._
      val permArray =
        if (perms == null) Array[FileAttribute[PosixFilePermission]]()
        else Array(PosixFilePermissions.asFileAttribute(perms.value.asJava))
      java.nio.file.Files.createFile(target.toNIO, permArray:_*)
    }
    java.nio.file.Files.newOutputStream(
      target.toNIO,
      openOptions.toArray:_*
    )
  }

  /**
    * Performs the actual opening and writing to a file. Basically cribbed
    * from `java.nio.file.Files.write` so we could re-use it properly for
    * different combinations of flags and all sorts of [[Source]]s
    */
  def write(target: Path,
            data: Source,
            flags: Seq[StandardOpenOption],
            perms: PermSet,
            offset: Long) = {

    import collection.JavaConverters._
    val permArray =
      if (perms == null) Array[FileAttribute[PosixFilePermission]]()
      else Array(PosixFilePermissions.asFileAttribute(perms.value.asJava))

    val out = Files.newByteChannel(
      target.wrapped,
      flags.toSet.asJava,
      permArray:_*
    )
    out.position(offset)
    try {
      data.getHandle().right.toOption.collect{case fcn: FileChannel => fcn} match{
        case Some(fcn) => fcn.transferTo(0, Long.MaxValue, out)
        case None => Internals.transfer(data.getInputStream(), Channels.newOutputStream(out))
      }
    }
    finally if (out != null) out.close()
  }
  def apply(target: Path,
            data: Source,
            perms: PermSet = null,
            createFolders: Boolean = true): Unit = {
    if (createFolders) makeDir.all(target/RelPath.up, perms)
    write(target, data, Seq(StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE), perms, 0)
  }

  /**
    * Identical to [[write]], except if the file already exists,
    * appends to the file instead of error-ing out
    */
  object append{
    def apply(target: Path,
              data: Source,
              perms: PermSet = null,
              createFolders: Boolean = true): Unit = {
      if (createFolders) makeDir.all(target/RelPath.up, perms)
      write(
        target, data,
        Seq(StandardOpenOption.CREATE, StandardOpenOption.APPEND, StandardOpenOption.WRITE),
        perms,
        0
      )
    }

    /**
      * Open a [[java.io.OutputStream]] to write or append to the given file
      */
    def outputStream(target: Path,
                     perms: PermSet = null,
                     createFolders: Boolean = true) = {
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
  object over{
    def apply(target: Path,
              data: Source,
              perms: PermSet = null,
              offset: Long = 0,
              createFolders: Boolean = true,
              truncate: Boolean = true): Unit = {
      if (createFolders) makeDir.all(target/RelPath.up, perms)
      write(
        target, data,
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
    def outputStream(target: Path,
                     perms: PermSet = null,
                     createFolders: Boolean = true) = {
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
}


/**
  * Reads the contents of a [[os.Path]] or other [[os.Source]] as a
  * `java.lang.String`. Defaults to reading the entire file as UTF-8, but you can
  * also select a different `charSet` to use, and provide an `offset`/`count` to
  * read from if the source supports seeking.
  */
object read extends Function1[ReadablePath, String]{
  def apply(arg: ReadablePath): String = apply(arg, java.nio.charset.StandardCharsets.UTF_8)
  def apply(arg: ReadablePath, charSet: Codec): String = {
    new String(read.bytes(arg), charSet.charSet)
  }
  def apply(arg: Path,
            charSet: Codec = java.nio.charset.StandardCharsets.UTF_8,
            offset: Long = 0,
            count: Int = Int.MaxValue): String = {
    new String(read.bytes(arg, offset, count), charSet.charSet)
  }

  /**
    * Opens a [[java.io.InputStream]] to read from the given file
    */
  object inputStream extends Function1[ReadablePath, java.io.InputStream]{
    def apply(p: ReadablePath): java.io.InputStream = p.toSource.getInputStream()
  }

  /**
    * Opens a [[SeekableByteChannel]] to read from the given file.
    */
  object channel extends Function1[Path, SeekableByteChannel]{
    def apply(p: Path): SeekableByteChannel = p.toSource.getChannel()
  }

  /**
    * Reads the contents of a [[os.Path]] or [[os.Source]] as an
    * `Array[Byte]`; you can provide an `offset`/`count` to read from if the source
    * supports seeking.
    */
  object bytes extends Function1[ReadablePath, Array[Byte]]{
    def apply(arg: ReadablePath): Array[Byte] = {
      val out = new java.io.ByteArrayOutputStream()
      Internals.transfer(arg.toSource.getInputStream(), out)
      out.toByteArray
    }
    def apply(arg: Path, offset: Long, count: Int): Array[Byte] = {
      val arr = new Array[Byte](count)
      val buf = ByteBuffer.wrap(arr)
      val channel = arg.toSource.getChannel()
      channel.position(offset)
      val finalCount = channel.read(buf)
      if (finalCount == arr.length) arr
      else arr.take(finalCount)
    }
  }

  /**
    * Reads the given [[os.Path]] or other [[os.Source]] as a string
    * and splits it into lines; defaults to reading as UTF-8, which you
    * can override by specifying a `charSet`.
    */
  object lines extends Function1[ReadablePath, IndexedSeq[String]]{
    def apply(src: ReadablePath) = stream(src).toArray[String]
    def apply(arg: ReadablePath, charSet: Codec): IndexedSeq[String] =
      stream(arg, charSet).toArray[String]

    /**
      * Identical to [[os.read.lines]], but streams the results back to you
      * in a [[os.Generator]] rather than accumulating them in memory. Useful
      * if the file is large.
      */
    object stream extends Function1[ReadablePath, geny.Generator[String]]{
      def apply(arg: ReadablePath) = apply(arg, java.nio.charset.StandardCharsets.UTF_8)

      def apply(arg: ReadablePath, charSet: Codec) = {
        new geny.Generator[String]{
          def generate(handleItem: String => Generator.Action) = {
            val is = arg.toSource.getInputStream()
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
  }

  def getInputStream(p: Source) = p.getInputStream()
}