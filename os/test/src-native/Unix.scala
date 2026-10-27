package test.os

import scala.scalanative.meta.LinktimeInfo.isWindows

object Unix {
  def apply(): Boolean = !isWindows
}
