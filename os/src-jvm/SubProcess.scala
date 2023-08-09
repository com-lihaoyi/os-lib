package os

import java.io._
import java.util.concurrent.TimeUnit

import scala.language.implicitConversions
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.LinkedTransferQueue

trait ProcessLike {

  def exitCode(): Int

  def isAlive(): Boolean

  def destroy(): Unit

  def destroyForcibly(): Unit

  def close(): Unit

  def waitFor(timeout: Long = -1): Boolean

  def join(timeout: Long = -1): Boolean
}


/**
 * Represents a spawn subprocess that has started and may or may not have
 * completed.
 */
class SubProcess(
    val wrapped: java.lang.Process,
    val inputPumperThread: Option[Thread],
    val outputPumperThread: Option[Thread],
    val errorPumperThread: Option[Thread]
) extends java.lang.AutoCloseable with ProcessLike {
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
   * Alias for [[destroy]]
   */
  def close() = wrapped.destroy()

  /**
   * Wait up to `millis` for the subprocess to terminate, by default waits
   * indefinitely. Returns `true` if the subprocess has terminated by the time
   * this method returns.
   */
  def waitFor(timeout: Long = -1): Boolean = {
    if (timeout == -1) {
      wrapped.waitFor()
      true
    } else {
      wrapped.waitFor(timeout, TimeUnit.MILLISECONDS)
    }
  }

  /**
   * Wait up to `millis` for the subprocess to terminate and all stdout and stderr
   * from the subprocess to be handled. By default waits indefinitely; if a time
   * limit is given, explicitly destroys the subprocess if it has not completed by
   * the time the timeout has occurred
   */
  def join(timeout: Long = -1): Boolean = {
    val exitedCleanly = waitFor(timeout)
    if (!exitedCleanly) {
      destroy()
      destroyForcibly()
      waitFor(-1)
    }
    outputPumperThread.foreach(_.join())
    errorPumperThread.foreach(_.join())
    exitedCleanly
  }
}

object SubProcess {

  /**
   * A [[BufferedWriter]] with the underlying [[java.io.OutputStream]] exposed
   *
   * Note that all writes that occur through this class are thread-safe and
   * synchronized. If you wish to perform writes without the synchronization
   * overhead, you can use the underlying [[wrapped]] stream directly
   */
  class InputStream(val wrapped: java.io.OutputStream)
      extends java.io.OutputStream with DataOutput {
    val data = new DataOutputStream(wrapped)
    val buffered = new BufferedWriter(new OutputStreamWriter(wrapped))

    def write(b: Int) = wrapped.write(b)
    override def write(b: Array[Byte]): Unit = wrapped.write(b)
    override def write(b: Array[Byte], off: Int, len: Int): Unit = wrapped.write(b, off, len)

    def writeBoolean(v: Boolean) = data.writeBoolean(v)
    def writeByte(v: Int) = data.writeByte(v)
    def writeShort(v: Int) = data.writeShort(v)
    def writeChar(v: Int) = data.writeChar(v)
    def writeInt(v: Int) = data.writeInt(v)
    def writeLong(v: Long) = data.writeLong(v)
    def writeFloat(v: Float) = data.writeFloat(v)
    def writeDouble(v: Double) = data.writeDouble(v)
    def writeBytes(s: String) = data.writeBytes(s)
    def writeChars(s: String) = data.writeChars(s)
    def writeUTF(s: String) = data.writeUTF(s)

    def writeLine(s: String) = buffered.write(s + "\n")
    def write(s: String) = buffered.write(s)
    def write(s: Array[Char]) = buffered.write(s)

    override def flush() = {
      data.flush()
      buffered.flush()
      wrapped.flush()
    }
    override def close() = wrapped.close()
  }

  /**
   * A combination [[BufferedReader]] and [[java.io.InputStream]], this allows
   * you to read both bytes and lines, without worrying about the buffer used
   * for reading lines messing up your reading of bytes.
   *
   * Note that all reads that occur through this class are thread-safe and
   * synchronized. If you wish to perform writes without the synchronization
   * overhead, you can use the underlying [[wrapped]] stream directly
   */
  class OutputStream(val wrapped: java.io.InputStream)
      extends java.io.InputStream with DataInput with geny.ByteData {
    val data = new DataInputStream(wrapped)
    val buffered = new BufferedReader(new InputStreamReader(wrapped))

    def read() = wrapped.read()
    override def read(b: Array[Byte]) = wrapped.read(b)
    override def read(b: Array[Byte], off: Int, len: Int) = wrapped.read(b, off, len)

    def readFully(b: Array[Byte]) = data.readFully(b)
    def readFully(b: Array[Byte], off: Int, len: Int) = data.readFully(b, off, len)

    def skipBytes(n: Int) = ???
    def readBoolean() = data.readBoolean()
    def readByte() = data.readByte()
    def readUnsignedByte() = data.readUnsignedByte()
    def readShort() = data.readShort()
    def readUnsignedShort() = data.readUnsignedShort()
    def readChar() = data.readChar()
    def readInt() = data.readInt()
    def readLong() = data.readLong()
    def readFloat() = data.readFloat()
    def readDouble() = data.readDouble()
    //    def readLine() = data.readLine()
    def readUTF() = data.readUTF()

    def readLine() = buffered.readLine()

    def bytes: Array[Byte] = synchronized {
      val out = new ByteArrayOutputStream()
      Internals.transfer(wrapped, out)
      out.toByteArray
    }

    override def close() = wrapped.close()
  }
}

class ProcessesPipeline(
  processes: Seq[SubProcess],
  failFast: Boolean,
  pipefail: Boolean
) extends AutoCloseable with ProcessLike {

  val failFastHandler: Option[Thread] = Option.when(failFast) {
    val finishedProcessQueue = new LinkedBlockingQueue[Int]()
    val processExitListeners = processes.zipWithIndex.map { case (process, index) =>
      new Thread(() => {
        process.join()
        finishedProcessQueue.put(index)
      })
    }

    val pipeBreakListener = new Thread(() => {
      processExitListeners.foreach(_.start())

      var pipelineRunning = true
      var highestBrokenPipeIndex = -1
      while(pipelineRunning) {  
        val brokenPipeIndex = finishedProcessQueue.take()
        if(brokenPipeIndex > highestBrokenPipeIndex) {
          highestBrokenPipeIndex = brokenPipeIndex
          if(brokenPipeIndex == processes.length - 1) 
            pipelineRunning = false
          pipelineRunning = pipelineRunning && processes(brokenPipeIndex).exitCode() == 0

          processes.take(brokenPipeIndex).filter(_.isAlive()).foreach(_.destroyForcibly())
        }
      }
      processes.filter(_.isAlive()).foreach(_.destroyForcibly())
      processExitListeners.foreach(_.join())
    })
    pipeBreakListener
  }

  override def exitCode(): Int = {
    if (pipefail) 
      processes.map(_.exitCode())
      .filter(_ != 0)
      .headOption
      .getOrElse(0)
    else 
      processes.last.exitCode()
  }

  override def isAlive(): Boolean = {
    processes.last.isAlive()
  }

  override def destroy(): Unit = {
    processes.foreach(_.destroy())
  }

  override def destroyForcibly(): Unit = {
    processes.foreach(_.destroyForcibly())
  }

  override def close(): Unit = {
    processes.foreach(_.close())
  }

  override def waitFor(timeout: Long = -1): Boolean = {
    processes.last.waitFor(timeout)
  }

  override def join(timeout: Long = -1): Boolean = {
    processes.last.join(timeout)
  }

  override def close(): Boolean = {
    processes.foreach(_.close())
  }
}

/**
 * Represents the configuration of a SubProcess's input stream. Can either be
 * [[os.Inherit]], [[os.Pipe]], [[os.Path]] or a [[os.Source]]
 */
trait ProcessInput {
  def redirectFrom: ProcessBuilder.Redirect
  def processInput(stdin: => SubProcess.InputStream): Option[Runnable]
}
object ProcessInput {
  implicit def makeSourceInput[T](r: T)(implicit f: T => Source): ProcessInput = SourceInput(f(r))
  implicit def makePathRedirect(p: Path): ProcessInput = PathRedirect(p)
  case class SourceInput(r: Source) extends ProcessInput {
    def redirectFrom = ProcessBuilder.Redirect.PIPE

    def processInput(stdin: => SubProcess.InputStream): Option[Runnable] = Some {
      new Runnable {
        def run() = {
          r.writeBytesTo(stdin)
          stdin.close()
        }
      }
    }
  }
}

/**
 * Represents the configuration of a SubProcess's output or error stream. Can
 * either be [[os.Inherit]], [[os.Pipe]], [[os.Path]] or a [[os.ProcessOutput]]
 */
trait ProcessOutput {
  def redirectTo: ProcessBuilder.Redirect
  def processOutput(out: => SubProcess.OutputStream): Option[Runnable]
}
object ProcessOutput {
  implicit def makePathRedirect(p: Path): ProcessOutput = PathRedirect(p)

  def apply(f: (Array[Byte], Int) => Unit) = ReadBytes(f)

  case class ReadBytes(f: (Array[Byte], Int) => Unit)
      extends ProcessOutput {
    def redirectTo = ProcessBuilder.Redirect.PIPE
    def processOutput(out: => SubProcess.OutputStream) = Some {
      new Runnable { def run(): Unit = os.Internals.transfer0(out, f) }
    }
  }

  case class Readlines(f: String => Unit)
      extends ProcessOutput {
    def redirectTo = ProcessBuilder.Redirect.PIPE
    def processOutput(out: => SubProcess.OutputStream) = Some {
      new Runnable {
        def run(): Unit = {
          val buffered = new BufferedReader(new InputStreamReader(out))
          while ({
            val lineOpt =
              try {
                buffered.readLine() match {
                  case null => None
                  case line => Some(line)
                }
              } catch { case e: Throwable => None }
            lineOpt match {
              case None => false
              case Some(s) =>
                f(s)
                true
            }
          }) ()
        }
      }
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

case class PathRedirect(p: Path) extends ProcessInput with ProcessOutput {
  def redirectFrom = ProcessBuilder.Redirect.from(p.toIO)
  def processInput(stdin: => SubProcess.InputStream) = None
  def redirectTo = ProcessBuilder.Redirect.to(p.toIO)
  def processOutput(out: => SubProcess.OutputStream) = None
}
case class PathAppendRedirect(p: Path) extends ProcessOutput {
  def redirectTo = ProcessBuilder.Redirect.appendTo(p.toIO)
  def processOutput(out: => SubProcess.OutputStream) = None
}