package test.os
import utest._
import TestUtil._
object ReadingWritingTests extends TestSuite {
  def tests = Tests{
    'read - {
      * - prep{ wd =>
        os.read(wd / "File.txt") ==> "I am cow"
        os.read(wd / "folder1" / "one.txt") ==> "Contents of folder one"
        os.read(wd / "Multi Line.txt") ==>
          """I am cow
            |Hear me moo
            |I weigh twice as much as you
            |And I look good on the barbecue""".stripMargin
      }
      'inputStream - {
        * - prep{wd =>
          val is = os.read.inputStream(wd / "File.txt") // ==> "I am cow"
          is.read() ==> 'I'
          is.read() ==> ' '
          is.read() ==> 'a'
          is.read() ==> 'm'
          is.read() ==> ' '
          is.read() ==> 'c'
          is.read() ==> 'o'
          is.read() ==> 'w'
          is.read() ==> -1
          is.close()
        }
      }
      'bytes - {
        * - prep{ wd =>
          os.read.bytes(wd / "File.txt") ==> "I am cow".getBytes
          os.read.bytes(wd / "misc" / "binary.png").length ==> 711
        }
      }
      'lines - {
        * - prep{ wd =>
          os.read.lines(wd / "File.txt") ==> Seq("I am cow")
          os.read.lines(wd / "Multi Line.txt") ==> Seq(
            "I am cow",
            "Hear me moo",
            "I weigh twice as much as you",
            "And I look good on the barbecue"
          )
        }
        'stream - {
          * - prep{ wd =>
            os.read.lines.stream(wd / "File.txt").count() ==> 1
            os.read.lines.stream(wd / "Multi Line.txt").count() ==> 4

            // Streaming the lines to the console
            for(line <- os.read.lines.stream(wd / "Multi Line.txt")){
              println(line)
            }
          }
        }
      }
    }

    'write - {
      * - prep{ wd =>
        os.write(wd / "New File.txt", "New File Contents")
        os.read(wd / "New File.txt") ==> "New File Contents"

        os.write(wd / "NewBinary.bin", Array[Byte](0, 1, 2, 3))
        os.read.bytes(wd / "NewBinary.bin") ==> Array[Byte](0, 1, 2, 3)
      }
      'append - {
        * - prep{ wd =>
          os.read(wd / "File.txt") ==> "I am cow"

          os.write.append(wd / "File.txt", ", hear me moo")
          os.read(wd / "File.txt") ==> "I am cow, hear me moo"

          os.write.append(wd / "File.txt", ",\nI weigh twice as much as you")
          os.read(wd / "File.txt") ==>
            "I am cow, hear me moo,\nI weigh twice as much as you"

          os.read.bytes(wd / "misc" / "binary.png").length ==> 711
          os.write.append(wd / "misc" / "binary.png", Array[Byte](1, 2, 3))
          os.read.bytes(wd / "misc" / "binary.png").length ==> 714
        }
      }
      'over - {
        * - prep{ wd =>
          os.read(wd / "File.txt") ==> "I am cow"
          os.write.over(wd / "File.txt", "You are cow")

          os.read(wd / "File.txt") ==> "You are cow"

          os.write.over(wd / "File.txt", "We ", truncate = false)
          os.read(wd / "File.txt") ==> "We  are cow"

          os.write.over(wd / "File.txt", "s", offset = 8, truncate = false)
          os.read(wd / "File.txt") ==> "We  are sow"
        }
      }
      'inputStream - {
        * - prep{ wd =>
          val out = os.write.outputStream(wd / "New File.txt")
          out.write('H')
          out.write('e')
          out.write('l')
          out.write('l')
          out.write('o')
          out.close()

          os.read(wd / "New File.txt") ==> "Hello"
        }
      }
    }
  }
}

