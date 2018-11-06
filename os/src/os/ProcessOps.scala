package os

import java.io._
import java.nio.charset.{Charset, StandardCharsets}
import java.util.concurrent.locks.ReentrantLock
import java.util.concurrent.{ArrayBlockingQueue, Semaphore, TimeUnit}

import os.write.append

import scala.annotation.tailrec

/**
  * Convenience APIs around [[java.lang.Process]] and [[java.lang.ProcessBuilder]]:
  *
  * - os.proc.call provides a convenient wrapper for "function-like" processes
  *   that you invoke with some input, whose entire output you need, but
  *   otherwise do not have any intricate back-and-forth communication
  *
  * - os.proc.stream provides a lower level API: rather than providing the output
  *   all at once, you pass in callbacks it invokes whenever there is a chunk of
  *   output received from the spawned process.
  *
  * - os.proc(...) provides the lowest level API: an simple Scala API around
  *   [[java.lang.ProcessBuilder]], that spawns a normal [[java.lang.Process]]
  *   for you to deal with. You can then interact with it normally through
  *   the standard stdin/stdout/stderr streams, using whatever protocol you
  *   want
  */
case class proc(command: Shellable*) {
  /**
    * Invokes the given subprocess like a function, passing in input and returning a
    * [[CommandResult]]. You can then call `result.exitCode` to see how it exited, or
    * `result.out.bytes` or `result.err.string` to access the aggregated stdout and
    * stderr of the subprocess in a number of convenient ways.
    *
    * If you want to spawn an interactive subprocess, such as `vim`, `less`, or a
    * `python` shell, set all of `stdin`/`stdout`/`stderr` to [[os.Inherit]]
    *
    * `call` provides a number of parameters that let you configure how the subprocess
    * is run:
    *
    * @param cwd             the working directory of the subprocess
    * @param env             any additional environment variables you wish to set in the subprocess
    * @param stdin           any data you wish to pass to the subprocess's standard input
    * @param stdout          [[os.Redirect]] that lets you configure how the
    *                        process's output stream is configured.
    * @param stderr          [[os.Redirect]] that lets you configure how the
    *                        process's error stream is configured.
    * @param mergeErrIntoOut merges the subprocess's stderr stream into it's stdout
    * @param timeout         how long to wait for the subprocess to complete
    * @param check           disable this to avoid throwing an exception if the subprocess
    *                        fails with a non-zero exit code
    * @param propagateEnv    disable this to avoid passing in this parent process's
    *                        environment variables to the subprocess
    */
  def call(cwd: Path = null,
           env: Map[String, String] = null,
           stdin: ProcessInput = Pipe,
           stdout: ProcessOutput = Pipe,
           stderr: ProcessOutput = Pipe,
           mergeErrIntoOut: Boolean = false,
           timeout: Long = Long.MaxValue,
           check: Boolean = true,
           propagateEnv: Boolean = true)
            : CommandResult = {

    val chunks = collection.mutable.Buffer.empty[Either[Bytes, Bytes]]
    val exitCode = stream(
      cwd, env,
      (arr, i) => chunks.append(Left(new Bytes(arr.take(i)))),
      (arr, i) => chunks.append(Right(new Bytes(arr.take(i)))),
      stdin,
      stdout,
      stderr,
      mergeErrIntoOut,
      timeout,
      propagateEnv
    )
    val res = CommandResult(exitCode, chunks)
    if (exitCode == 0 || !check) res
    else throw SubprocessException(res)
  }

  /**
    * Similar to [[os.proc.call]], but instead of aggregating the process's
    * standard output/error streams for you, you pass in `onOut`/`onErr` callbacks to
    * receive the data as it is generated.
    *
    * Note that the Array[Byte] buffer you are passed in `onOut`/`onErr` are
    * shared from callback to callback, so if you want to preserve the data make
    * sure you read the it out of the array rather than storing the array (which
    * will have its contents over-written next callback.
    *
    * Returns the exit code of the subprocess once it terminates.
    */
  def stream(cwd: Path = null,
             env: Map[String, String] = null,
             onOut: (Array[Byte], Int) => Unit,
             onErr: (Array[Byte], Int) => Unit,
             stdin: ProcessInput = Pipe,
             stdout: ProcessOutput = Pipe,
             stderr: ProcessOutput = Pipe,
             mergeErrIntoOut: Boolean = false,
             timeout: Long = Long.MaxValue,
             propagateEnv: Boolean = true): Int = {
    val process = spawn(
      cwd, env, stdin, stdout, stderr, mergeErrIntoOut, propagateEnv
    )

    // While reading from the subprocess takes place on separate threads, we end
    // up serializing the received data and running the `onOut` and `onErr`
    // callbacks on the main thread to avoid the multithreaded nature of this
    // function being visible to the user (and possibly causing multithreading bugs!)
    val callbackQueue = new ArrayBlockingQueue[(Boolean, Array[Byte], Int)](1)

    // Ensure we do not start reading another block of data into the
    // outReader/errReader buffers until the main thread's user callback has
    // finished processing the data in that buffer and returns
    val errCallbackLock = new Semaphore(1, true)
    val outCallbackLock = new Semaphore(1, true)

    val outReader = new Thread(new Runnable {
      def run() = {
        Internals.transfer0(
          process.stdout,
          () => outCallbackLock.acquire(),
          (arr, n) => callbackQueue.put((true, arr, n))
        )
      }
    })

    val errReader = new Thread(new Runnable {
      def run() = {
        Internals.transfer0(
          process.stderr,
          () => errCallbackLock.acquire(),
          (arr, n) => callbackQueue.put((false, arr, n))
        )
      }
    })

    outReader.start()
    errReader.start()
    val startTime = System.currentTimeMillis()

    // We only check if the out/err readers and process are alive, and not the
    // inWriter. If the out/err readers and process are all dead, it doesn't
    // matter if there's more stuff waiting to be sent to the process's stdin:
    // it's already all over
    while ((outReader.isAlive || errReader.isAlive || process.isAlive)
           && System.currentTimeMillis() - startTime < timeout){
      callbackQueue.poll(1, TimeUnit.MILLISECONDS) match{
        case null => // do nothing
        case (out, arr, n) =>
          val callback = if (out) onOut else onErr
          callback(arr, n)
          val lock = if (out) outCallbackLock else errCallbackLock
          lock.release()
      }
    }

    if (System.currentTimeMillis() - startTime > timeout){
      process.destroy()
      process.destroyForcibly()
    }

    // If someone `Ctrl C`s the Ammonite REPL while we are waiting on a
    // subprocess, don't stop waiting!
    //
    // - For "well behaved" subprocess like `ls` or `yes`, they will terminate
    //   on their own and return control to us when they receive a `Ctrl C`
    //
    // - For "capturing" processes like `vim` or `python` or `bash`, those
    //   should *not* exit on Ctrl-C, and in fact we do not even receive an
    //   interrupt because they do terminal magic
    //
    // - For weird processes like `less` or `git log`, without this
    //   ignore-exceptions tail recursion it would stop waiting for the
    //   subprocess but the *less* subprocess will still be around! This messes
    //   up all our IO for as long as the subprocess lives. We can't force-quit
    //   the subprocess because *it's* children may hand around and do the same
    //   thing (e.g. in the case of `git log`, which leaves a `less` grandchild
    //   hanging around). Thus we simply don't let `Ctrl C` interrupt these
    //   fellas, and force you to use e.g. `q` to exit `less` gracefully.
    @tailrec def run(): Int =
      try {
        process.waitFor()
        process.exitCode()
      } catch {case e: Throwable => run() }

    run()
  }

  /**
    * The most flexible of the [[os.proc]] calls, `os.proc.spawn` simply configures
    * and starts a subprocess, and returns it as a `java.lang.Process` for you to
    * interact with however you like.
    */
  def spawn(cwd: Path = null,
            env: Map[String, String] = null,
            stdin: ProcessInput = Pipe,
            stdout: ProcessOutput = Pipe,
            stderr: ProcessOutput = Pipe,
            mergeErrIntoOut: Boolean = false,
            propagateEnv: Boolean = true): SubProcess = {
    val builder = new java.lang.ProcessBuilder()

    val baseEnv =
      if (propagateEnv) sys.env
      else Map()
    for ((k, v) <- baseEnv ++ Option(env).getOrElse(Map())){
      if (v != null) builder.environment().put(k, v)
      else builder.environment().remove(k)
    }
    builder.directory(Option(cwd).getOrElse(os.pwd).toIO)

    lazy val proc: SubProcess = new SubProcess(
      builder
        .command(command.flatMap(_.s):_*)
        .redirectInput(stdin.redirectFrom)
        .redirectOutput(stdout.redirectTo)
        .redirectError(stderr.redirectTo)
        .redirectErrorStream(mergeErrIntoOut)
        .start(),
      stdin.processInput(proc.stdin).map(new Thread(_)),
      stdout.processOutput(proc.stdout).map(new Thread(_)),
      stderr.processOutput(proc.stderr).map(new Thread(_))
    )

    proc.inputPumperThread.foreach(_.start())
    proc.outputPumperThread.foreach(_.start())
    proc.errorPumperThread.foreach(_.start())
    proc
  }
}

/**
  * Represents a spawn subprocess that has started and may or may not have
  * completed.
  */
class SubProcess(val wrapped: java.lang.Process,
                 val inputPumperThread: Option[Thread],
                 val outputPumperThread: Option[Thread],
                 val errorPumperThread: Option[Thread]){
  val stdin: SubProcess.Input = new SubProcess.Input(wrapped.getOutputStream)
  val stdout: SubProcess.Output = new SubProcess.Output(wrapped.getInputStream)
  val stderr: SubProcess.Output = new SubProcess.Output(wrapped.getErrorStream)

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
  class Input(val wrapped: java.io.OutputStream) extends java.io.OutputStream{
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
  }

  /**
    * A combination [[BufferedReader]] and [[java.io.InputStream]], this allows
    * you to read both bytes and lines, without worrying about the buffer used
    * for reading lines messing up your reading of bytes.
    */
  class Output(val wrapped: java.io.InputStream,
               bufferSize: Int = 8192) extends java.io.InputStream{
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
    def readBytes(): Array[Byte] = {
      val out = new ByteArrayOutputStream()
      Internals.transfer(this, out)
      out.toByteArray
    }

    /**
      * Read all bytes from this pipe from the subprocess as a string in the given
      * charset (defaults to UTF-8), blocking until it is complete.
      */
    def readString(charSet: Charset = java.nio.charset.StandardCharsets.UTF_8): String = {
      new String(readBytes(), charSet)
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
  }
}

/**
  * Represents the configuration of a SubProcess's input stream. Can either be
  * [[os.Inherit]], [[os.Pipe]], [[os.Redirect]] or a [[os.Source]]
  */
trait ProcessInput{
  def redirectFrom: ProcessBuilder.Redirect
  def processInput(stdin: => SubProcess.Input): Option[Runnable]
}
object ProcessInput{
  implicit def makeSourceInput[T](r: T)(implicit f: T => Source): ProcessInput = SourceInput(f(r))
  case class SourceInput(r: Source) extends ProcessInput {
    def redirectFrom = ProcessBuilder.Redirect.PIPE

    def processInput(stdin: => SubProcess.Input): Option[Runnable] = Some{
      new Runnable{def run() = os.Internals.transfer(r.getInputStream(), stdin)}
    }
  }
}

/**
  * Represents the configuration of a SubProcess's output or error stream. Can
  * either be [[os.Inherit]], [[os.Pipe]], [[os.Redirect]] or a [[os.ProcessOutput]]
  */
sealed trait ProcessOutput{
  def redirectTo: ProcessBuilder.Redirect
  def processOutput(out: => SubProcess.Output): Option[Runnable]
}
object ProcessOutput{
  def apply(f: (Array[Byte], Int) => Unit, preReadCallback: () => Unit = () => ()) =
    CallbackOutput(f, preReadCallback)

  case class CallbackOutput(f: (Array[Byte], Int) => Unit, preReadCallback: () => Unit){
    def redirectTo = ProcessBuilder.Redirect.PIPE
    def processOutput(stdin: => SubProcess.Output) = Some{
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
  def processInput(stdin: => SubProcess.Input) = None
  def processOutput(stdin: => SubProcess.Output) = None
}

/**
  * Pipe the input/output stream to the current process to be used via
  * `java.lang.Process#{getInputStream,getOutputStream,getErrorStream}`
  */
object Pipe extends ProcessInput with ProcessOutput {
  def redirectTo = ProcessBuilder.Redirect.PIPE
  def redirectFrom = ProcessBuilder.Redirect.PIPE
  def processInput(stdin: => SubProcess.Input) = None
  def processOutput(stdin: => SubProcess.Output) = None
}

/**
  * Redirect the input/output directly to a file on disk
  */
object Redirect{
  def apply(p: Path) = PathRedirect(p)
  def append(p: Path) = PathAppendRedirect(p)
}
case class PathRedirect(p: Path) extends ProcessInput with ProcessOutput{
  def redirectFrom = ProcessBuilder.Redirect.from(p.toIO)
  def processInput(stdin: => SubProcess.Input) = None
  def redirectTo = ProcessBuilder.Redirect.to(p.toIO)
  def processOutput(out: => SubProcess.Output) = None
}
case class PathAppendRedirect(p: Path) extends ProcessOutput{
  def redirectTo = ProcessBuilder.Redirect.appendTo(p.toIO)
  def processOutput(out: => SubProcess.Output) = None
}
