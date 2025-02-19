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
   * the time the timeout has occurred.
   *
   * By default, a process is destroyed by sending a `SIGTERM` signal, which allows an opportunity
   * for it to clean up any resources it was using. If the process is unresponsive to this, a
   * `SIGKILL` signal is sent `timeoutGracePeriod` milliseconds later. If `timeoutGracePeriod` is
   * `0`, then there is no `SIGTERM`; if it is `-1`, there is no `SIGKILL` sent.
   *
   * @returns `true` when the process did not require explicit termination by either `SIGTERM` or `SIGKILL` and `false` otherwise.
   * @note the issuing of `SIGTERM` instead of `SIGKILL` is implementation dependent on your JVM version. Pre-Java 9, no `SIGTERM` may be
   *       issued. Check the documentation for your JDK's `Process.destroy`.
   */
  def join(timeout: Long = -1, timeoutGracePeriod: Long = 100): Boolean = {
    val exitedCleanly = waitFor(timeout)
    if (!exitedCleanly) {
      assume(
        timeout != -1,
        "if the waitFor does not complete cleanly, this implies there is a timeout imposed, so the grace period is applicable"
      )
      if (timeoutGracePeriod == -1) destroy()
      else if (timeoutGracePeriod == 0) destroyForcibly()
      else {
        destroy()
        if (!waitFor(timeoutGracePeriod)) {
          destroyForcibly()
        }
      }
      waitFor(-1)
    }
    joinPumperThreadsHook()
    exitedCleanly
  }

  @deprecatedOverriding("this method is now a forwarder, and should not be overriden", "0.10.4")
  private[os] def join(timeout: Long): Boolean = join(timeout, timeoutGracePeriod = 100)

  /**
   * A hook method used by `join` to close the input and output streams associated with the process, not for public consumption.
   */
  private[os] def joinPumperThreadsHook(): Unit
}

/**
 * Represents a spawn subprocess that has started and may or may not have
 * completed.
 */
@deprecatedInheritance(
  "this class will be made final: if you are using it be aware that `join` has a new overloading",
  "0.10.4"
)
class SubProcess(
    val wrapped: java.lang.Process,
    val inputPumperThread: Option[Thread],
    val outputPumperThread: Option[Thread],
    val errorPumperThread: Option[Thread],
    val shutdownGracePeriod: Long,
    val shutdownHookMonitorThread: Option[Thread]
) extends ProcessLike {
  def this(
      wrapped: java.lang.Process,
      inputPumperThread: Option[Thread],
      outputPumperThread: Option[Thread],
      errorPumperThread: Option[Thread]
  ) = this(
    wrapped,
    inputPumperThread,
    outputPumperThread,
    errorPumperThread,
    100,
    None
  )
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
  def destroy(): Unit = destroy(shutdownGracePeriod = this.shutdownGracePeriod, async = false)

  def destroy(
      shutdownGracePeriod: Long,
      async: Boolean
  ): Unit = destroy(shutdownGracePeriod, async, recursive = true)

  /**
   * Destroys the subprocess, via the underlying JVM APIs, with configurable levels of
   * aggressiveness:
   *
   * @param async set this to `true` if you do not want to wait on the subprocess exiting
   * @param shutdownGracePeriod use this to override the default wait time for the subprocess
   *                            to gracefully exit before destroying it forcibly. Defaults to the `shutdownGracePeriod`
   *                            that was used to spawned the process, but can be set to 0
   *                            (i.e. force exit immediately) or -1 (i.e. never force exit)
   *                            or anything in between. Typically defaults to 100 milliseconds.
   * @param recursive whether or not to also destroy this process's own child processes and
   *                  descendents. Each parent process is destroyed before its children, to
   *                  ensure that when we are destroying the child processes no other children
   *                  can be spawned concurrently
   */
  def destroy(
      shutdownGracePeriod: Long = this.shutdownGracePeriod,
      async: Boolean = false,
      recursive: Boolean = true
  ): Unit = {
    SubProcess.destroy(wrapped.toHandle, async, shutdownGracePeriod, recursive)
  }

  @deprecated("Use destroy(shutdownGracePeriod = 0)")
  def destroyForcibly(): Unit = destroy(shutdownGracePeriod = 0)

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

  private[os] def joinPumperThreadsHook(): Unit = {
    outputPumperThread.foreach(_.join())
    errorPumperThread.foreach(_.join())
  }
}

object SubProcess {
  private def destroySingle(p: ProcessHandle, async: Boolean, shutdownGracePeriod: Long) = {
    p.destroy()
    if (!async) {
      val now = System.currentTimeMillis()

      while (
        p.isAlive && (shutdownGracePeriod == -1 || System.currentTimeMillis() - now < shutdownGracePeriod)
      ) {
        Thread.sleep(1)
      }

      if (p.isAlive) p.destroyForcibly()
    }
  }

  private def destroyRecursive(
      p: ProcessHandle,
      async: Boolean,
      shutdownGracePeriod: Long
  ): Unit = {
    destroyRecursive(p, async, shutdownGracePeriod)
    p.children().forEach(c => destroyRecursive(c, async, shutdownGracePeriod))

  }

  /**
   * Similar to [[SubProcess.destroy]], but can be called on an arbitrary process handle,
   * not just [[SubProcess]] objects created by OS-Lib. e.g. could be to called on
   * `ProcessHandle.current().children()` to cleanup leaked processes during shutdown
   */
  def destroy(
      p: ProcessHandle,
      async: Boolean = false,
      shutdownGracePeriod: Long = 100L,
      recursive: Boolean = true
  ): Unit = {
    if (recursive) SubProcess.destroyRecursive(p, async, shutdownGracePeriod)
    else SubProcess.destroySingle(p, async, shutdownGracePeriod)
  }

  /**
   * The env passed by default to child processes.
   * When `null`, the system environment is used.
   */
  val env = new scala.util.DynamicVariable[Map[String, String]](null)

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

@deprecatedInheritance(
  "this class will be made final: if you are using it be aware that `join` has a new overloading",
  "0.10.4"
)
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
            if (brokenPipeIndex == processes.length) { // Special case signaling finished pipeline
              pipelineRunning = false
            } else {
              processes(brokenPipeIndex).destroyForcibly()
            }
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
   * Wait up to `timeout` for the [[ProcessPipeline]] to terminate, by default waits
   * indefinitely. Returns `true` if the [[ProcessPipeline]] has terminated by the time
   * this method returns.
   *
   * Waits for each process one by one, while aggregating the total time waited. If
   * `timeout` has passed before all processes have terminated, returns `false`.
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
   * Wait up to `timeout` for the [[ProcessPipeline]] to terminate all the processes
   * in pipeline. By default waits indefinitely; if a time limit is given, explicitly
   * destroys each process if it has not completed by the time the timeout has occurred.
   *
   * By default, the processes are destroyed by sending `SIGTERM` signals, which allows an opportunity
   * for them to clean up any resources it. If any process is unresponsive to this, a
   * `SIGKILL` signal is sent `timeoutGracePeriod` milliseconds later. If `timeoutGracePeriod` is
   * `0`, then there is no `SIGTERM`; if it is `-1`, there is no `SIGKILL` sent.
   *
   * @returns `true` when the processes did not require explicit termination by either `SIGTERM` or `SIGKILL` and `false` otherwise.
   * @note the issuing of `SIGTERM` instead of `SIGKILL` is implementation dependent on your JVM version. Pre-Java 9, no `SIGTERM` may be
   *       issued. Check the documentation for your JDK's `Process.destroy`.
   */
  override def join(timeout: Long = -1, timeoutGracePeriod: Long = 100): Boolean = {
    // in this case, the grace period does not apply, so fine
    if (timeout == -1) {
      processes.forall(_.join())
    } else super.join(timeout, timeoutGracePeriod)
  }

  private[os] def joinPumperThreadsHook(): Unit = {
    processes.foreach(_.joinPumperThreadsHook())
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
 * Inherit the input/output stream from the current process.
 *
 * Can be overriden on a thread local basis for the various
 * kinds of streams (stdin, stdout, stderr) via [[in]], [[out]], and [[err]]
 */
object Inherit extends ProcessInput with ProcessOutput {
  def redirectTo = ProcessBuilder.Redirect.INHERIT
  def redirectFrom = ProcessBuilder.Redirect.INHERIT
  def processInput(stdin: => SubProcess.InputStream) = None
  def processOutput(stdin: => SubProcess.OutputStream) = None

  val in = new scala.util.DynamicVariable[ProcessInput](Inherit)
  val out = new scala.util.DynamicVariable[ProcessOutput](Inherit)
  val err = new scala.util.DynamicVariable[ProcessOutput](Inherit)
}

/**
 * Inherit the input/output stream from the current process.
 * Identical of [[os.Inherit]], except it cannot be redirected globally
 */
object InheritRaw extends ProcessInput with ProcessOutput {
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
