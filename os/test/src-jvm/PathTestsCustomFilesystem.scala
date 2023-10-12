package test.os

import java.nio.file.Paths

import os._
import utest._
import java.util.HashMap
import java.nio.file.FileSystems
import java.net.URI

object PathTestsCustomFilesystem extends TestSuite {
  val tests = Tests { // native doesnt support custom fs yet
    test("custom filesystem") { 
      val path: java.nio.file.Path = java.nio.file.Paths.get("foo.jar");
      val uri = new URI("jar", path.toUri().toString(), null);

      val env = new HashMap[String, String]();
      env.put("create", "true");

      val fileSystem = FileSystems.newFileSystem(uri, env);
      val p = os.root("/", fileSystem) / "test" / "dir"
      assert(p.root == "/")
      assert(p.fileSystem == fileSystem)
    }
  }
}