package os

import collection.JavaConverters._
import java.lang.ProcessBuilder.Redirect
import os.SubProcess.InputStream
import java.io.IOException
import java.util.concurrent.LinkedBlockingQueue
import ProcessOps._

object call {

  /**
   * @see [[os.proc.call]]
   */
  def apply(
      cmd: Shellable,
      env: Map[String, String] = null,
      // Make sure `cwd` only comes after `env`, so `os.call("foo", path)` is a compile error
      // since the correct syntax is `os.call(("foo", path))`
      cwd: Path = null,
      stdin: ProcessInput = Pipe,
      stdout: ProcessOutput = Pipe,
      stderr: ProcessOutput = os.Inherit,
      mergeErrIntoOut: Boolean = false,
      timeout: Long = -1,
      check: Boolean = true,
      propagateEnv: Boolean = true,
      shutdownGracePeriod: Long = 100,
      destroyOnExit: Boolean = true
  ): CommandResult = {
    os.proc(cmd).call(
      cwd = cwd,
      env = env,
      stdin = stdin,
      stdout = stdout,
      stderr = stderr,
      mergeErrIntoOut = mergeErrIntoOut,
      timeout = timeout,
      check = check,
      propagateEnv = propagateEnv,
      shutdownGracePeriod = shutdownGracePeriod,
      destroyOnExit = destroyOnExit
    )
  }

  // Bincompat Forwarder
  def apply(
      cmd: Shellable,
      env: Map[String, String],
      // Make sure `cwd` only comes after `env`, so `os.call("foo", path)` is a compile error
      // since the correct syntax is `os.call(("foo", path))`
      cwd: Path,
      stdin: ProcessInput,
      stdout: ProcessOutput,
      stderr: ProcessOutput,
      mergeErrIntoOut: Boolean,
      timeout: Long,
      check: Boolean,
      propagateEnv: Boolean,
      timeoutGracePeriod: Long
  ): CommandResult = {
    call(
      cmd = cmd,
      cwd = cwd,
      env = env,
      stdin = stdin,
      stdout = stdout,
      stderr = stderr,
      mergeErrIntoOut = mergeErrIntoOut,
      timeout = timeout,
      check = check,
      propagateEnv = propagateEnv,
      shutdownGracePeriod = timeoutGracePeriod,
      destroyOnExit = true
    )
  }
}
object spawn {

  /**
   * @see [[os.proc.spawn]]
   */
  def apply(
      cmd: Shellable,
      // Make sure `cwd` only comes after `env`, so `os.spawn("foo", path)` is a compile error
      // since the correct syntax is `os.spawn(("foo", path))`
      env: Map[String, String] = null,
      cwd: Path = null,
      stdin: ProcessInput = Pipe,
      stdout: ProcessOutput = Pipe,
      stderr: ProcessOutput = os.Inherit,
      mergeErrIntoOut: Boolean = false,
      propagateEnv: Boolean = true,
      shutdownGracePeriod: Long = 100,
      destroyOnExit: Boolean = true
  ): SubProcess = {
    os.proc(cmd).spawn(
      cwd = cwd,
      env = env,
      stdin = stdin,
      stdout = stdout,
      stderr = stderr,
      mergeErrIntoOut = mergeErrIntoOut,
      propagateEnv = propagateEnv,
      shutdownGracePeriod = shutdownGracePeriod,
      destroyOnExit = destroyOnExit
    )
  }

  // Bincompat Forwarder
  def apply(
      cmd: Shellable,
      // Make sure `cwd` only comes after `env`, so `os.spawn("foo", path)` is a compile error
      // since the correct syntax is `os.spawn(("foo", path))`
      env: Map[String, String],
      cwd: Path,
      stdin: ProcessInput,
      stdout: ProcessOutput,
      stderr: ProcessOutput,
      mergeErrIntoOut: Boolean,
      propagateEnv: Boolean
  ): SubProcess = {
    spawn(
      cmd = cmd,
      cwd = cwd,
      env = env,
      stdin = stdin,
      stdout = stdout,
      stderr = stderr,
      mergeErrIntoOut = mergeErrIntoOut,
      propagateEnv = propagateEnv,
      shutdownGracePeriod = 100,
      destroyOnExit = true
    )
  }
}

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
  def commandChunks: Seq[String] = command.flatMap(_.value)

  /**
   * Invokes the given subprocess like a function, passing in input and returning a
   * [[CommandResult]]. You can then call `result.exitCode` to see how it exited, or
   * `result.out.bytes` or `result.err.text()` to access the aggregated stdout and
   * stderr of the subprocess in a number of convenient ways. If a non-zero exit code
   * is returned, this throws a [[os.SubprocessException]] containing the
   * [[CommandResult]], unless you pass in `check = false`.
   *
   * If you want to spawn an interactive subprocess, such as `vim`, `less`, or a
   * `python` shell, set all of `stdin`/`stdout`/`stderr` to [[os.Inherit]]
   *
   * `call` provides a number of parameters that let you configure how the subprocess
   * is run:
   *
   * @param cwd                the working directory of the subprocess
   * @param env                any additional environment variables you wish to set in the subprocess
   * @param stdin              any data you wish to pass to the subprocess's standard input
   * @param stdout             How the process's output stream is configured.
   * @param stderr             How the process's error stream is configured.
   * @param mergeErrIntoOut    merges the subprocess's stderr stream into it's stdout
   * @param timeout            how long to wait in milliseconds for the subprocess to complete
   *                           (-1 for no timeout)
   * @param check              disable this to avoid throwing an exception if the subprocess
   *                           fails with a non-zero exit code
   * @param propagateEnv       disable this to avoid passing in this parent process's
   *                           environment variables to the subprocess
   * @param shutdownGracePeriod if the timeout is enabled, how long in milliseconds for the
   *                           subprocess to gracefully terminate before attempting to
   *                           forcibly kill it
   *                           (-1 for no kill, 0 for always kill immediately)
   *
   * @note the issuing of `SIGTERM` instead of `SIGKILL` is implementation dependent on your JVM version. Pre-Java 9, no `SIGTERM` may be
   *       issued. Check the documentation for your JDK's `Process.destroy`.
   */
  def call(
      cwd: Path = null,
      env: Map[String, String] = null,
      stdin: ProcessInput = Pipe,
      stdout: ProcessOutput = Pipe,
      stderr: ProcessOutput = os.Inherit,
      mergeErrIntoOut: Boolean = false,
      timeout: Long = -1,
      check: Boolean = true,
      propagateEnv: Boolean = true,
      // this cannot be next to `timeout` as this will introduce a bin-compat break (default arguments are numbered in the bytecode)
      shutdownGracePeriod: Long = 100,
      destroyOnExit: Boolean = true
  ): CommandResult = {

    val chunks = new java.util.concurrent.ConcurrentLinkedQueue[Either[geny.Bytes, geny.Bytes]]

    val sub = spawn(
      cwd,
      env,
      stdin,
      if (stdout ne os.Pipe) stdout
      else os.ProcessOutput.ReadBytes((buf, n) =>
        chunks.add(Left(new geny.Bytes(java.util.Arrays.copyOf(buf, n))))
      ),
      if (stderr ne os.Pipe) stderr
      else os.ProcessOutput.ReadBytes((buf, n) =>
        chunks.add(Right(new geny.Bytes(java.util.Arrays.copyOf(buf, n))))
      ),
      mergeErrIntoOut,
      propagateEnv,
      shutdownGracePeriod = shutdownGracePeriod,
      destroyOnExit = destroyOnExit
    )

    sub.join(timeout, shutdownGracePeriod)

    val chunksSeq = chunks.iterator.asScala.toIndexedSeq
    val res = CommandResult(commandChunks, sub.exitCode(), chunksSeq)
    if (res.exitCode == 0 || !check) res
    else throw SubprocessException(res)
  }

  // forwarder for the new timeoutGracePeriod flag
  private[os] def call(
      cwd: Path,
      env: Map[String, String],
      stdin: ProcessInput,
      stdout: ProcessOutput,
      stderr: ProcessOutput,
      mergeErrIntoOut: Boolean,
      timeout: Long,
      check: Boolean,
      propagateEnv: Boolean
  ): CommandResult = call(
    cwd,
    env,
    stdin,
    stdout,
    stderr,
    mergeErrIntoOut,
    timeout,
    check,
    propagateEnv,
    shutdownGracePeriod = 100
  )

  // Bincompat Forwarder
  private[os] def call(
      cwd: Path,
      env: Map[String, String],
      stdin: ProcessInput,
      stdout: ProcessOutput,
      stderr: ProcessOutput,
      mergeErrIntoOut: Boolean,
      timeout: Long,
      check: Boolean,
      propagateEnv: Boolean,
      timeoutGracePeriod: Long
  ): CommandResult = call(
    cwd,
    env,
    stdin,
    stdout,
    stderr,
    mergeErrIntoOut,
    timeout,
    check,
    propagateEnv,
    timeoutGracePeriod,
    destroyOnExit = true
  )

  /**
   * The most flexible of the [[os.proc]] calls, `os.proc.spawn` simply configures
   * and starts a subprocess, and returns it as a `java.lang.Process` for you to
   * interact with however you like.
   *
   * Note that if you provide `ProcessOutput` callbacks to `stdout`/`stderr`,
   * the calls to those callbacks take place on newly spawned threads that
   * execute in parallel with the main thread. Thus make sure any data
   * processing you do in those callbacks is thread safe!
   */
  def spawn(
      cwd: Path = null,
      env: Map[String, String] = null,
      stdin: ProcessInput = Pipe,
      stdout: ProcessOutput = Pipe,
      stderr: ProcessOutput = os.Inherit,
      mergeErrIntoOut: Boolean = false,
      propagateEnv: Boolean = true,
      shutdownGracePeriod: Long = 100,
      destroyOnExit: Boolean = true
  ): SubProcess = {

    val cmdChunks = commandChunks
    val commandStr = cmdChunks.mkString(" ")

    def resolve[T](x: T, y: T) = if (x == os.Inherit) y else x
    val resolvedStdin = resolve(stdin, os.Inherit.in.value)
    val resolvedStdout = resolve(stdout, os.Inherit.out.value)
    val resolvedStderr = resolve(stderr, os.Inherit.err.value)

    val builder = buildProcess(
      commandChunks,
      cwd,
      env,
      resolvedStdin,
      resolvedStdout,
      resolvedStderr,
      mergeErrIntoOut,
      propagateEnv
    )

    lazy val shutdownHookThread =
      if (!destroyOnExit) None
      else Some(new Thread("subprocess-shutdown-hook") {
        override def run(): Unit = proc.destroy(shutdownGracePeriod)
      })

    lazy val shutdownHookMonitorThread = shutdownHookThread.map(t =>
      new Thread("subprocess-shutdown-hook-monitor") {
        override def run(): Unit = {
          while (proc.wrapped.isAlive) Thread.sleep(1)
          try Runtime.getRuntime().removeShutdownHook(t)
          catch {
            case e: Throwable => /*do nothing*/
          }
        }
      }
    )

    shutdownHookThread.foreach(Runtime.getRuntime().addShutdownHook)

    lazy val proc: SubProcess = new SubProcess(
      builder.start(),
      resolvedStdin.processInput(proc.stdin).map(new Thread(_, commandStr + " stdin thread")),
      resolvedStdout.processOutput(proc.stdout).map(new Thread(_, commandStr + " stdout thread")),
      resolvedStderr.processOutput(proc.stderr).map(new Thread(_, commandStr + " stderr thread")),
      shutdownGracePeriod = shutdownGracePeriod,
      shutdownHookMonitorThread = shutdownHookMonitorThread
    )

    shutdownHookMonitorThread.foreach(_.start())

    proc.inputPumperThread.foreach(_.start())
    proc.outputPumperThread.foreach(_.start())
    proc.errorPumperThread.foreach(_.start())
    proc
  }

  // Bincompat Forwarder
  def spawn(
      cwd: Path,
      env: Map[String, String],
      stdin: ProcessInput,
      stdout: ProcessOutput,
      stderr: ProcessOutput,
      mergeErrIntoOut: Boolean,
      propagateEnv: Boolean
  ): SubProcess = spawn(
    cwd = cwd,
    env = env,
    stdin = stdin,
    stdout = stdout,
    stderr = stderr,
    mergeErrIntoOut = mergeErrIntoOut,
    propagateEnv = propagateEnv,
    shutdownGracePeriod = 100,
    destroyOnExit = true
  )

  /**
   * Pipes the output of this process into the input of the [[next]] process. Returns a
   * [[ProcGroup]] containing both processes, which you can then either execute or
   * pipe further.
   */
  def pipeTo(next: proc): ProcGroup = ProcGroup(Seq(this, next))
}

/**
 * A group of processes that are piped together, corresponding to e.g. `ls -l | grep .scala`.
 * You can create a `ProcGroup` by calling `.pipeTo` on a [[proc]] multiple times.
 * Contains methods corresponding to the methods on [[proc]], but defined for pipelines
 * of processes.
 */
case class ProcGroup private[os] (commands: Seq[proc]) {
  assert(commands.size >= 2)

  private lazy val isWindows = sys.props("os.name").toLowerCase().contains("windows")

  /**
   * Invokes the given pipeline like a function, passing in input and returning a
   * [[CommandResult]]. You can then call `result.exitCode` to see how it exited, or
   * `result.out.bytes` or `result.err.string` to access the aggregated stdout and
   * stderr of the subprocess in a number of convenient ways. If a non-zero exit code
   * is returned, this throws a [[os.SubprocessException]] containing the
   * [[CommandResult]], unless you pass in `check = false`.
   *
   * For each process in pipeline, the output will be forwarded to the input of the next
   * process. Input of the first process is set to provided [[stdin]] The output of the last
   * process will be returned as the output of the pipeline. [[stderr]] is set for all processes.
   *
   * `call` provides a number of parameters that let you configure how the pipeline
   * is run:
   *
   * @param cwd                the working directory of the pipeline
   * @param env                any additional environment variables you wish to set in the pipeline
   * @param stdin              any data you wish to pass to the pipelines's standard input (to the first process)
   * @param stdout             How the pipelines's output stream is configured (the last process stdout)
   * @param stderr             How the process's error stream is configured (set for all processes)
   * @param mergeErrIntoOut    merges the pipeline's stderr stream into it's stdout. Note that then the
   *                           stderr will be forwarded with stdout to subsequent processes in the pipeline.
   * @param timeout            how long to wait in milliseconds for the pipeline to complete
   * @param check              disable this to avoid throwing an exception if the pipeline
   *                           fails with a non-zero exit code
   * @param propagateEnv       disable this to avoid passing in this parent process's
   *                           environment variables to the pipeline
   * @param pipefail           if true, the pipeline's exitCode will be the exit code of the first
   *                           failing process. If no process fails, the exit code will be 0.
   * @param handleBrokenPipe   if true, every [[java.io.IOException]] when redirecting output of a process
   *                           will be caught and handled by killing the writing process. This behaviour
   *                           is consistent with handlers of SIGPIPE signals in most programs
   *                           supporting interruptable piping. Disabled by default on Windows.
   * @param shutdownGracePeriod if the timeout is enabled, how long in milliseconds for the
   *                           subprocess to gracefully terminate before attempting to
   *                           forcibly kill it
   *                           (-1 for no kill, 0 for always kill immediately)
   *
   * @note the issuing of `SIGTERM` instead of `SIGKILL` is implementation dependent on your JVM version. Pre-Java 9, no `SIGTERM` may be
   *       issued. Check the documentation for your JDK's `Process.destroy`.
   */
  def call(
      cwd: Path = null,
      env: Map[String, String] = null,
      stdin: ProcessInput = Pipe,
      stdout: ProcessOutput = Pipe,
      stderr: ProcessOutput = os.Inherit,
      mergeErrIntoOut: Boolean = false,
      timeout: Long = -1,
      check: Boolean = true,
      propagateEnv: Boolean = true,
      pipefail: Boolean = true,
      handleBrokenPipe: Boolean = !isWindows,
      // this cannot be next to `timeout` as this will introduce a bin-compat break (default arguments are numbered in the bytecode)
      shutdownGracePeriod: Long = 100
  ): CommandResult = {
    val chunks = new java.util.concurrent.ConcurrentLinkedQueue[Either[geny.Bytes, geny.Bytes]]

    val sub = spawn(
      cwd,
      env,
      stdin,
      if (stdout ne os.Pipe) stdout
      else os.ProcessOutput.ReadBytes((buf, n) =>
        chunks.add(Left(new geny.Bytes(java.util.Arrays.copyOf(buf, n))))
      ),
      if (stderr ne os.Pipe) stderr
      else os.ProcessOutput.ReadBytes((buf, n) =>
        chunks.add(Right(new geny.Bytes(java.util.Arrays.copyOf(buf, n))))
      ),
      mergeErrIntoOut,
      propagateEnv,
      pipefail
    )

    sub.join(timeout, shutdownGracePeriod)

    val chunksSeq = chunks.iterator.asScala.toIndexedSeq
    val res =
      CommandResult(commands.flatMap(_.commandChunks :+ "|").init, sub.exitCode(), chunksSeq)
    if (res.exitCode == 0 || !check) res
    else throw SubprocessException(res)
  }

  private[os] def call(
      cwd: Path,
      env: Map[String, String],
      stdin: ProcessInput,
      stdout: ProcessOutput,
      stderr: ProcessOutput,
      mergeErrIntoOut: Boolean,
      timeout: Long,
      check: Boolean,
      propagateEnv: Boolean,
      pipefail: Boolean,
      handleBrokenPipe: Boolean
  ): CommandResult = call(
    cwd,
    env,
    stdin,
    stdout,
    stderr,
    mergeErrIntoOut,
    timeout,
    check,
    propagateEnv,
    pipefail,
    handleBrokenPipe,
    shutdownGracePeriod = 100
  )

  /**
   * The most flexible of the [[os.ProcGroup]] calls. It sets-up a pipeline of processes,
   * and returns a [[ProcessPipeline]] for you to interact with however you like.
   *
   * Note that if you provide `ProcessOutput` callbacks to `stdout`/`stderr`,
   * the calls to those callbacks take place on newly spawned threads that
   * execute in parallel with the main thread. Thus make sure any data
   * processing you do in those callbacks is thread safe!
   * @param cwd              the working directory of the pipeline
   * @param env              any additional environment variables you wish to set in the pipeline
   * @param stdin            any data you wish to pass to the pipelines's standard input (to the first process)
   * @param stdout           How the pipelines's output stream is configured (the last process stdout)
   * @param stderr           How the process's error stream is configured (set for all processes)
   * @param mergeErrIntoOut  merges the pipeline's stderr stream into it's stdout. Note that then the
   *                         stderr will be forwarded with stdout to subsequent processes in the pipeline.
   * @param propagateEnv     disable this to avoid passing in this parent process's
   *                         environment variables to the pipeline
   * @param pipefail         if true, the pipeline's exitCode will be the exit code of the first
   *                         failing process. If no process fails, the exit code will be 0.
   * @param handleBrokenPipe if true, every [[java.io.IOException]] when redirecting output of a process
   *                         will be caught and handled by killing the writing process. This behaviour
   *                         is consistent with handlers of SIGPIPE signals in most programs
   *                         supporting interruptable piping. Disabled by default on Windows.
   */
  def spawn(
      cwd: Path = null,
      env: Map[String, String] = null,
      stdin: ProcessInput = Pipe,
      stdout: ProcessOutput = Pipe,
      stderr: ProcessOutput = os.Inherit,
      mergeErrIntoOut: Boolean = false,
      propagateEnv: Boolean = true,
      pipefail: Boolean = true,
      handleBrokenPipe: Boolean = !isWindows
  ): ProcessPipeline = {
    val brokenPipeQueue = new LinkedBlockingQueue[Int]()
    val (_, procs) =
      commands.zipWithIndex.foldLeft((Option.empty[ProcessInput], Seq.empty[SubProcess])) {
        case ((None, _), (proc, _)) =>
          val spawned = proc.spawn(cwd, env, stdin, Pipe, stderr, mergeErrIntoOut, propagateEnv)
          (Some(spawned.stdout), Seq(spawned))
        case ((Some(input), acc), (proc, index)) if index == commands.length - 1 =>
          val spawned = proc.spawn(
            cwd,
            env,
            wrapWithBrokenPipeHandler(input, index - 1, brokenPipeQueue),
            stdout,
            stderr,
            mergeErrIntoOut,
            propagateEnv
          )
          (None, acc :+ spawned)
        case ((Some(input), acc), (proc, index)) =>
          val spawned = proc.spawn(
            cwd,
            env,
            wrapWithBrokenPipeHandler(input, index - 1, brokenPipeQueue),
            Pipe,
            stderr,
            mergeErrIntoOut,
            propagateEnv
          )
          (Some(spawned.stdout), acc :+ spawned)
      }
    val pipeline =
      new ProcessPipeline(procs, pipefail, if (handleBrokenPipe) Some(brokenPipeQueue) else None)
    pipeline.brokenPipeHandler.foreach(_.start())
    pipeline
  }

  private def wrapWithBrokenPipeHandler(
      wrapped: ProcessInput,
      index: Int,
      queue: LinkedBlockingQueue[Int]
  ) =
    new ProcessInput {
      override def redirectFrom: Redirect = wrapped.redirectFrom
      override def processInput(stdin: => InputStream): Option[Runnable] =
        wrapped.processInput(stdin).map { runnable =>
          new Runnable {
            def run() = {
              try {
                runnable.run()
              } catch {
                case e: IOException =>
                  queue.put(index)
              }
            }
          }
        }
    }

  /**
   * Pipes the output of this pipeline into the input of the [[next]] process.
   */
  def pipeTo(next: proc) = ProcGroup(commands :+ next)
}

@experimental
object ProcessOps {
  val spawnHook = new scala.util.DynamicVariable[os.Path => Unit]({ p => () })
  def buildProcess(
      command: Seq[String],
      cwd: Path = null,
      env: Map[String, String] = null,
      stdin: ProcessInput = Pipe,
      stdout: ProcessOutput = Pipe,
      stderr: ProcessOutput = os.Inherit,
      mergeErrIntoOut: Boolean = false,
      propagateEnv: Boolean = true
  ): ProcessBuilder = {
    val builder = new java.lang.ProcessBuilder()

    val environment = builder.environment()

    def addToProcessEnv(env: Map[String, String]) =
      if (env != null) {
        for ((k, v) <- env) {
          if (v != null) environment.put(k, v)
          else environment.remove(k)
        }
      }

    os.SubProcess.env.value match {
      case null =>
        if (!propagateEnv) {
          environment.clear()
        }
      case subProcessEnvValue =>
        environment.clear()
        if (propagateEnv) {
          addToProcessEnv(subProcessEnvValue)
        }
    }

    addToProcessEnv(env)

    val dir = Option(cwd).getOrElse(os.pwd)
    builder.directory(dir.toIO)
    spawnHook.value.apply(dir)

    builder
      .command(command: _*)
      .redirectInput(stdin.redirectFrom)
      .redirectOutput(stdout.redirectTo)
      .redirectError(stderr.redirectTo)
      .redirectErrorStream(mergeErrIntoOut)
  }
}
