package os

import java.io._
import java.util.concurrent.TimeUnit

import scala.language.implicitConversions
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.LinkedTransferQueue
import java.util.concurrent.LinkedBlockingQueue
import scala.annotation.tailrec

/**
 * Parent type for single processes and process pipelines.
 */
sealed trait ProcessLike extends java.lang.AutoCloseable {

  /**
   * The exit code of this [[ProcessLike]]. Conventionally, 0 exit code represents a
   * successful termination, and non-zero exit code indicates a failure.
   *
   * Throws an exception if the subprocess has not terminated
   */
  def exitCode(): Int

  /**
   * Returns `true` if the [[ProcessLike]] is still running and has not terminated
   */
  def isAlive(): Boolean

  /**
   * Attempt to destroy the [[ProcessLike]] (gently), via the underlying JVM APIs
   */
  def destroy(): Unit

  /**
   * Force-destroys the [[ProcessLike]], via the underlying JVM APIs
   */
  def destroyForcibly(): Unit

  /**
   * Alias for [[destroy]], implemented for [[java.lang.AutoCloseable]]
   */
  override def close(): Unit

  /**
   * Wait up to `millis` for the [[ProcessLike]] to terminate, by default waits
   * indefinitely. Returns `true` if the [[ProcessLike]] has terminated by the time
   * this method returns.
   */
  def waitFor(timeout: Long = -1): Boolean

  /**
   * Wait up to `millis` for the [[ProcessLike]] to terminate and all stdout and stderr
   * from the subprocess to be handled. By default waits indefinitely; if a time
   * limit is given, explicitly destroys the [[ProcessLike]] if it has not completed by
   * the time the timeout has occurred
   */
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
) extends ProcessLike {
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

class ProcessPipeline(
    val processes: Seq[SubProcess],
    pipefail: Boolean,
    brokenPipeQueue: Option[LinkedBlockingQueue[Int]] // to emulate pipeline behavior in jvm < 9
) extends ProcessLike {
  pipeline =>

  /**
   * String representation of the pipeline.
   */
  def commandString = processes.map(_.wrapped.toString).mkString(" | ")

  private[os] val brokenPipeHandler: Option[Thread] = brokenPipeQueue.map { queue =>
    new Thread(
      new Runnable {
        override def run(): Unit = {
          var pipelineRunning = true
          while (pipelineRunning) {
            val brokenPipeIndex = queue.take()
            println("Killing " + brokenPipeIndex)
            if (brokenPipeIndex == processes.length) { // Special case signaling finished pipeline
              pipelineRunning = false
            } else {
              processes(brokenPipeIndex).destroyForcibly()
            }
            println("Processes status: " + processes.map(_.isAlive()))
          }
          new Thread(
            new Runnable {
              override def run(): Unit = {
                while (!pipeline.waitFor()) {} // handle spurious wakes
                queue.put(processes.length) // Signal finished pipeline
              }
            },
            commandString + " pipeline termination handler"
          ).start()
        }
      },
      commandString + " broken pipe handler"
    )
  }

  /**
   * The exit code of this [[ProcessPipeline]]. Conventionally, 0 exit code represents a
   * successful termination, and non-zero exit code indicates a failure. Throws an exception
   * if the subprocess has not terminated.
   *
   * If pipefail is set, the exit code is the first non-zero exit code of the pipeline. If no
   * process in the pipeline has a non-zero exit code, the exit code is 0.
   */
  override def exitCode(): Int = {
    if (pipefail)
      processes.map(_.exitCode())
        .filter(_ != 0)
        .headOption
        .getOrElse(0)
    else
      processes.last.exitCode()
  }

  /**
   * Returns `true` if the [[ProcessPipeline]] is still running and has not terminated.
   * Any process in the pipeline can be alive for the pipeline to be alive.
   */
  override def isAlive(): Boolean = {
    processes.exists(_.isAlive())
  }

  /**
   * Attempt to destroy the [[ProcessPipeline]] (gently), via the underlying JVM APIs.
   * All processes in the pipeline are destroyed.
   */
  override def destroy(): Unit = {
    processes.foreach(_.destroy())
  }

  /**
   * Force-destroys the [[ProcessPipeline]], via the underlying JVM APIs.
   * All processes in the pipeline are force-destroyed.
   */
  override def destroyForcibly(): Unit = {
    processes.foreach(_.destroyForcibly())
  }

  /**
   * Alias for [[destroy]], implemented for [[java.lang.AutoCloseable]].
   */
  override def close(): Unit = {
    processes.foreach(_.close())
  }

  /**
   * Wait up to `millis` for the [[ProcessPipeline]] to terminate, by default waits
   * indefinitely. Returns `true` if the [[ProcessPipeline]] has terminated by the time
   * this method returns.
   *
   * Waits for each process one by one, while aggregating the total time waited. If
   * [[timeout]] has passed before all processes have terminated, returns `false`.
   */
  override def waitFor(timeout: Long = -1): Boolean = {
    @tailrec
    def waitForRec(startedAt: Long, processesLeft: Seq[SubProcess]): Boolean = processesLeft match {
      case Nil => true
      case head :: tail =>
        val elapsed = System.currentTimeMillis() - startedAt
        val timeoutLeft = timeout - elapsed
        if (timeoutLeft < 0) false
        else if (head.waitFor(timeoutLeft)) waitForRec(startedAt, tail)
        else false
    }

    if (timeout == -1) {
      processes.forall(_.waitFor())
    } else {
      val timeNow = System.currentTimeMillis()
      waitForRec(timeNow, processes)
    }
  }

  /**
   * Wait up to `millis` for the [[ProcessPipeline]] to terminate all the processes
   * in pipeline. By default waits indefinitely; if a time limit is given, explicitly
   * destroys each process if it has not completed by the time the timeout has occurred.
   */
  override def join(timeout: Long = -1): Boolean = {
    @tailrec
    def joinRec(startedAt: Long, processesLeft: Seq[SubProcess], result: Boolean): Boolean =
      processesLeft match {
        case Nil => result
        case head :: tail =>
          val elapsed = System.currentTimeMillis() - startedAt
          val timeoutLeft = Math.max(0, timeout - elapsed)
          val exitedCleanly = head.join(timeoutLeft)
          joinRec(startedAt, tail, result && exitedCleanly)
      }

    if (timeout == -1) {
      processes.forall(_.join())
    } else {
      val timeNow = System.currentTimeMillis()
      joinRec(timeNow, processes, true)
    }
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
