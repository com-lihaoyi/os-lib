package os

import java.util.concurrent.{ArrayBlockingQueue, Semaphore, TimeUnit}

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
    * @param stdout          How the process's output stream is configured.
    * @param stderr          How the process's error stream is configured.
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
    * All calls to the `onOut`/`onErr` callbacks take place on the main thread.
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
    *
    * To implement pipes, you can spawn a process, take it's stdout, and pass it
    * as the stdin of a second spawned process.
    *
    * Note that if you provide `ProcessOutput` callbacks to `stdout`/`stderr`,
    * the calls to those callbacks take place on newly spawned threads that
    * execute in parallel with the main thread. Thus make sure any data
    * processing you do in those callbacks is thread safe!
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

    val cmd = 
      if(scala.util.Properties.isWin) Vector("cmd.exe", "/C") ++ command.flatMap(_.value)
      else command.flatMap(_.value)

    lazy val proc: SubProcess = new SubProcess(
      builder
        .command(cmd:_*)
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

