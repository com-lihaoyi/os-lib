package os

import java.io.{BufferedReader, BufferedWriter, ByteArrayOutputStream, OutputStreamWriter}
import java.nio.charset.{Charset, StandardCharsets}
import java.util.concurrent.TimeUnit

import scala.annotation.tailrec

/**
  * Represents a spawn subprocess that has started and may or may not have
  * completed.
  */
class SubProcess(val wrapped: java.lang.Process,
                 val inputPumperThread: Option[Thread],
                 val outputPumperThread: Option[Thread],
                 val errorPumperThread: Option[Thread]){
  val stdin: SubProcess.InputStream = new SubProcess.InputStream(wrapped.getOutputStream)
  val stdout: SubProcess.OutputStream = new SubProcess.OutputStream(wrapped.getInputStream)
  val stderr: SubProcess.OutputStream = new SubProcess.OutputStream(wrapped.getErrorStream)

  /**
    * The subprocess' exit code. Conventionally, 0 exit code represents a
    * successful termination, and non-zero exit code indicates a failure.
    *
    * Throws an exception if the subprocess has not terminated
    */
  def exitCode(): Int = wrapped.exitValue()

  /**
    * Returns `true` if the subprocess is still running and has not terminated
    */
  def isAlive(): Boolean = wrapped.isAlive

  /**
    * Attempt to destroy the subprocess (gently), via the underlying JVM APIs
    */
  def destroy(): Unit = wrapped.destroy()

  /**
    * Force-destroys the subprocess, via the underlying JVM APIs
    */
  def destroyForcibly(): Unit = wrapped.destroyForcibly()

  /**
    * Wait up to `millis` for the subprocess to terminate, by default waits
    * indefinitely. Returns `true` if the subprocess has terminated by the time
    * this method returns
    */
  def waitFor(millis: Long = -1): Boolean =
    if(millis == -1) {
      wrapped.waitFor()
      true
    } else {
      wrapped.waitFor(millis, TimeUnit.MILLISECONDS)
    }

}


object SubProcess{

  /**
    * A [[BufferedWriter]] with the underlying [[java.io.OutputStream]] exposed
    */
  class InputStream(val wrapped: java.io.OutputStream) extends java.io.OutputStream{
    def write(b: Int) = wrapped.write(b)

    override def write(b: Array[Byte]): Unit = wrapped.write(b)
    override def write(b: Array[Byte], offset: Int, len: Int): Unit = wrapped.write(b, offset, len)


    def write(s: String,
              charSet: Charset = StandardCharsets.UTF_8): Unit = {
      val writer = new OutputStreamWriter(wrapped, charSet)
      writer.write(s)
      writer.flush()
    }
    def writeLine(s: String,
                  charSet: Charset = StandardCharsets.UTF_8): Unit = {
      val writer = new OutputStreamWriter(wrapped, charSet)
      writer.write(s)
      writer.write("\n")
      writer.flush()
    }

    override def flush() = wrapped.flush()
    override def close() = wrapped.close()
  }

  /**
    * A combination [[BufferedReader]] and [[java.io.InputStream]], this allows
    * you to read both bytes and lines, without worrying about the buffer used
    * for reading lines messing up your reading of bytes.
    */
  class OutputStream(val wrapped: java.io.InputStream,
                     bufferSize: Int = 8192) extends java.io.InputStream with StreamValue {
    // We maintain our own buffer internally, in order to make readLine
    // efficient by avoiding reading character by character. As a consequence
    // all the other read methods have to check the buffer for data and using
    // that before going and reading from the wrapped input stream.
    private[this] var bufferOffset = 0
    private[this] var bufferEnd = 0
    private[this] val buffer = new Array[Byte](bufferSize)
    // Keep track if the last readLine() call terminated on a \r, since that
    // means any subsequence readLine() that starts with a \n should ignore the
    // leading character
    private[this] var lastSeenSlashR = false

    /**
      * Read all bytes from this pipe from the subprocess, blocking until it is
      * complete, and returning it as a byte array
      */
    def bytes(): Array[Byte] = {
      val out = new ByteArrayOutputStream()
      Internals.transfer(this, out)
      out.toByteArray
    }

    def read() = {
      lastSeenSlashR = false
      if (bufferOffset < bufferEnd){
        val res = buffer(bufferOffset)
        bufferOffset += 1
        res
      }else{
        wrapped.read()
      }
    }

    override def read(b: Array[Byte]) = {
      lastSeenSlashR = false
      this.read(b, 0, b.length)
    }
    override def read(b: Array[Byte], offset: Int, len: Int) = {
      lastSeenSlashR = false
      val bufferedCount = bufferEnd - bufferOffset
      if (bufferedCount > len){
        bufferOffset += len
        System.arraycopy(buffer, bufferEnd, b, offset, len)
        len
      }else{
        bufferOffset = bufferEnd
        System.arraycopy(buffer, bufferEnd, b, offset, bufferedCount)
        wrapped.read(b, bufferedCount, len - bufferedCount) + bufferedCount
      }
    }

    /**
      * Read a single line from the stream, as a string. A line is ended by \n,
      * \r or \r\n. The returned string does *not* return the trailing
      * delimiter.
      */
    def readLine(charSet: Charset = StandardCharsets.UTF_8): String = {
      val output = new ByteArrayOutputStream()
      // Reads the buffer for a newline, returning the index of the newline
      // (if any). Returns the length of the buffer if no newline is found
      @tailrec def recChar(i: Int, skipFirst: Boolean): (Int, Boolean) = {
        if (i == bufferEnd) (i, skipFirst) // terminate tailrec
        else if (buffer(i) == '\n') {
          if (lastSeenSlashR) {
            lastSeenSlashR = false
            recChar(i + 1, true)
          } else (i, skipFirst)

        } else if (buffer(i) == '\r'){
          lastSeenSlashR = true
          (i, skipFirst)
        } else{
          recChar(i + 1, skipFirst)
        }
      }

      // Reads what's currently in the buffer trying to find a newline. If no
      // newline is found in the whole buffer, load another batch of bytes into
      // the buffer from the input stream and try again
      @tailrec def recBuffer(didSomething: Boolean): Boolean = {
        val (newLineIndex, skipFirst) = recChar(bufferOffset, false)
        val skipOffset = if (skipFirst) 1 else 0
        if (newLineIndex < bufferEnd) { // Found a newline
          output.write(buffer, bufferOffset + skipOffset, newLineIndex - bufferOffset - skipOffset)
          bufferOffset = newLineIndex + 1
          true
        } else if (newLineIndex == bufferEnd){

          val start = bufferOffset + skipOffset
          val end = newLineIndex - bufferOffset - skipOffset
          output.write(buffer, start, end)
          val readCount = wrapped.read(buffer, 0, buffer.length)
          if (readCount != -1){ // End of buffer
            bufferOffset = 0
            bufferEnd = readCount
            recBuffer(didSomething || end > start)
          }else{ // End of input
            val didSomething2 = didSomething || newLineIndex != (bufferOffset + skipOffset)
            bufferOffset = 0
            bufferEnd = 0
            didSomething2
          }
        } else ???
      }

      val didSomething = recBuffer(false)

      if (didSomething) output.toString(charSet.name())
      else null
    }

    override def close() = wrapped.close()
  }
}

/**
  * Represents the configuration of a SubProcess's input stream. Can either be
  * [[os.Inherit]], [[os.Pipe]], [[os.Redirect]] or a [[os.Source]]
  */
trait ProcessInput{
  def redirectFrom: ProcessBuilder.Redirect
  def processInput(stdin: => SubProcess.InputStream): Option[Runnable]
}
object ProcessInput{
  implicit def makeSourceInput[T](r: T)(implicit f: T => Source): ProcessInput = SourceInput(f(r))
  implicit def makePathRedirect(p: Path): ProcessInput = PathRedirect(p)
  case class SourceInput(r: Source) extends ProcessInput {
    def redirectFrom = ProcessBuilder.Redirect.PIPE

    def processInput(stdin: => SubProcess.InputStream): Option[Runnable] = Some{
      new Runnable{def run() = {
        os.Internals.transfer(r.getInputStream(), stdin)
        stdin.close()
      }}
    }
  }
}

/**
  * Represents the configuration of a SubProcess's output or error stream. Can
  * either be [[os.Inherit]], [[os.Pipe]], [[os.Redirect]] or a [[os.ProcessOutput]]
  */
sealed trait ProcessOutput{
  def redirectTo: ProcessBuilder.Redirect
  def processOutput(out: => SubProcess.OutputStream): Option[Runnable]
}
object ProcessOutput{
  implicit def makePathRedirect(p: Path): ProcessInput = PathRedirect(p)

  def apply(f: (Array[Byte], Int) => Unit, preReadCallback: () => Unit = () => ()) =
    CallbackOutput(f, preReadCallback)

  case class CallbackOutput(f: (Array[Byte], Int) => Unit, preReadCallback: () => Unit){
    def redirectTo = ProcessBuilder.Redirect.PIPE
    def processOutput(stdin: => SubProcess.OutputStream) = Some{
      new Runnable {def run(): Unit = os.Internals.transfer0(stdin, preReadCallback, f)}
    }
  }
}

/**
  * Inherit the input/output stream from the current process
  */
object Inherit extends ProcessInput with ProcessOutput {
  def redirectTo = ProcessBuilder.Redirect.INHERIT
  def redirectFrom = ProcessBuilder.Redirect.INHERIT
  def processInput(stdin: => SubProcess.InputStream) = None
  def processOutput(stdin: => SubProcess.OutputStream) = None
}

/**
  * Pipe the input/output stream to the current process to be used via
  * `java.lang.Process#{getInputStream,getOutputStream,getErrorStream}`
  */
object Pipe extends ProcessInput with ProcessOutput {
  def redirectTo = ProcessBuilder.Redirect.PIPE
  def redirectFrom = ProcessBuilder.Redirect.PIPE
  def processInput(stdin: => SubProcess.InputStream) = None
  def processOutput(stdin: => SubProcess.OutputStream) = None
}

case class PathRedirect(p: Path) extends ProcessInput with ProcessOutput{
  def redirectFrom = ProcessBuilder.Redirect.from(p.toIO)
  def processInput(stdin: => SubProcess.InputStream) = None
  def redirectTo = ProcessBuilder.Redirect.to(p.toIO)
  def processOutput(out: => SubProcess.OutputStream) = None
}
case class PathAppendRedirect(p: Path) extends ProcessOutput{
  def redirectTo = ProcessBuilder.Redirect.appendTo(p.toIO)
  def processOutput(out: => SubProcess.OutputStream) = None
}