package test.os

object Unix {
  def apply(): Boolean = !scala.util.Properties.isWin
}
