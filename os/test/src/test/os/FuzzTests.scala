package test.os

import java.io._
import java.nio.charset.StandardCharsets

import os._
import utest._

import scala.collection.mutable

object FuzzTests extends TestSuite{
  val tests = Tests {

    def readUntilNull(f: () => String) = {
      val list = collection.mutable.Buffer.empty[String]
      while({
        f() match{
          case null => false
          case s =>
            list.append(s)
            true
        }
      })()
      list
    }
    test("output"){
      // Make sure the os.SubProcess.OutputStream matches the behavior of
      // java.io.BufferedReader, when run on tricky combinations of \r and \n
      def check(s: String) = {
        val stream1 = new BufferedReader(new InputStreamReader(
          new ByteArrayInputStream(s.getBytes())
        ))

        val list1 = readUntilNull(stream1.readLine _)
        for(bufferSize <- Range.inclusive(1, 10)){
          val stream2 = new os.SubProcess.OutputStream(
            new ByteArrayInputStream(s.getBytes),
            bufferSize
          )
          val list2 = readUntilNull(stream2.readLine _)
          assert(list1 == list2)
        }

        val p = os.proc("cat").spawn()
        p.stdin.write(s)
        p.stdin.close()

        val list3 = readUntilNull(p.stdout.readLine _)
        assert(list1 == list3)
      }
      check("\r")
      check("\r\r")
      check("\r\n")
      check("\n\r")
      check("\n\n")
      check("\n")

      check("a\r")
      check("a\r\r")
      check("a\r\n")
      check("a\n\r")
      check("a\n\n")
      check("a\n")

      check("\rb")
      check("\r\rb")
      check("\r\nb")
      check("\n\rb")
      check("\n\nb")
      check("\nb")

      check("a\rb")
      check("a\r\rb")
      check("a\r\nb")
      check("a\n\rb")
      check("a\n\nb")
      check("a\nb")


      check("a\nc\rb")
      check("a\nc\r\rb")
      check("a\nc\nb")
      check("a\nc\n\nb")
      check("a\nc\r\nb")
      check("a\nc\n\rb")

      check("a\rc\rb")
      check("a\rc\r\rb")
      check("a\rc\nb")
      check("a\rc\n\nb")
      check("a\rc\r\nb")
      check("a\rc\n\rb")

      check("a\n\rc\rb")
      check("a\n\rc\r\rb")
      check("a\n\rc\nb")
      check("a\n\rc\n\nb")
      check("a\n\rc\r\nb")
      check("a\n\rc\n\rb")

      check("a\r\nc\rb")
      check("a\r\nc\r\rb")
      check("a\r\nc\nb")
      check("a\r\nc\n\nb")
      check("a\r\nc\r\nb")
      check("a\r\nc\n\rb")


      check("\na\r")
      check("\na\r\r")
      check("\na\r\n")
      check("\na\n\r")
      check("\na\n\n")
      check("\na\n")

      check("\ra\r")
      check("\ra\r\r")
      check("\ra\r\n")
      check("\ra\n\r")
      check("\ra\n\n")
      check("\ra\n")

      check("\n\ra\r")
      check("\n\ra\r\r")
      check("\n\ra\r\n")
      check("\n\ra\n\r")
      check("\n\ra\n\n")
      check("\n\ra\n")

      check("\r\na\r")
      check("\r\na\r\r")
      check("\r\na\r\n")
      check("\r\na\n\r")
      check("\r\na\n\n")
      check("\r\na\n")
    }
    test("inAndOut"){
      val random = new scala.util.Random(313373)

      val cat = proc('cat).spawn()

      for (n0 <- Range(0, 20000)) {
        val n = random.nextInt(n0 + 1)
        val chunk = new Array[Byte](n)
        random.nextBytes(chunk)
        cat.stdin.write(chunk)
        cat.stdin.flush()
        val out = new Array[Byte](n)
        cat.stdout.readFully(out)

        assert {
          identity(n)
          java.util.Arrays.equals(chunk, out)
        }
      }
      cat.stdin.close()
    }
    test("uneven"){
      val random = new scala.util.Random(313373)

      val cat = proc('cat).spawn()
      val output = new ByteArrayOutputStream()
      val input = new ByteArrayOutputStream()
      val drainer = new Thread(
        new Runnable {
          def run(): Unit = {
            val readBuffer = new Array[Byte](1337)
            while ( {
              cat.stdout.read(readBuffer) match {
                case -1 => false
                case n =>
                  output.write(readBuffer, 0, n)
                  true
              }
            }) ()
          }
        }
      )

      drainer.start()
      for (n0 <- Range(0, 20000)) {
        val n = random.nextInt(n0 + 1)
        val chunk = new Array[Byte](n)
        random.nextBytes(chunk)
        cat.stdin.write(chunk)
        cat.stdin.flush()
        input.write(chunk)
      }
      cat.stdin.close()
      drainer.join()
      assert(java.util.Arrays.equals(input.toByteArray, output.toByteArray))
    }
    test("lines"){
      val random = new scala.util.Random(313373)

      val cat = proc('cat).spawn()
      val output = mutable.Buffer.empty[String]
      val input = new ByteArrayOutputStream()
      val drainer = new Thread(
        new Runnable {
          def run(): Unit = {
            while({
              cat.stdout.readLine(StandardCharsets.UTF_8) match{
                case null => false
                case line =>
                  output.append(line)
                  true
              }
            })()
          }
        }
      )

      drainer.start()
      for (n <- Range(0, 5000)) {
        for(_ <- Range(0, random.nextInt(n + 1))){
          val c = random.nextInt()
          cat.stdin.write(c)
          input.write(c)
        }
        val newline = random.nextInt(4) match{
          case 0 => "\n"
          case 1 => "\r"
          case 2 => "\r\n"
          case 3 => "\n\r"
        }

        cat.stdin.write(newline)
        input.write(newline.getBytes)
        cat.stdin.flush()
      }

      val inputReader = new BufferedReader(
        new InputStreamReader(
          new ByteArrayInputStream(
            input.toByteArray
          ),
          StandardCharsets.UTF_8
        )
      )
      val inputLines = readUntilNull(inputReader.readLine _)

      cat.stdin.close()
      drainer.join()

      assert(inputLines == output)
    }
  }
}
