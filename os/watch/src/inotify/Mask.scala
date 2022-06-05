package os.watch.inotify

import scala.collection.mutable

case class Mask(mask: Int) {
  def |(rhs: Mask): Mask = Mask(mask|rhs.mask)
  def contains(rhs: Mask) : Boolean = (mask & rhs.mask) == rhs.mask

  override def toString: String = {

    val things = Mask.named_masks.toList.sortBy(_._1).flatMap { case(name,m) =>
      if (this.contains(m)) Seq(name) else Seq()
    }.mkString("+")

    f"Mask($mask%08x = $things)"
  }
}

object Mask {
  val named_masks : mutable.Map[String,Mask] = mutable.Map()

  private def named(bit: Int)(implicit name: sourcecode.Name): Mask = {
    val a = Mask(1 << bit)
    named_masks.put(name.value,a)
    a
  }

  val access: Mask = named(0)
  val modify: Mask = named(1)
  val attrib: Mask = named(2)
  val close_write: Mask = named(3)
  val close_nowrite: Mask = named(4)
  val open: Mask = named(5)
  val move_from: Mask = named(6)
  val move_to: Mask = named(7)
  val create: Mask = named(8)
  val delete: Mask = named(9)
  val delete_self: Mask = named(10)

  val unmount: Mask = named(13)
  val overflow: Mask = named(14)
  val ignored: Mask = named(15)

  val only_dir: Mask = named(24)
  val do_not_follow: Mask = named(25)
  val exclude_unlink: Mask = named(26)

  val mask_create: Mask = named(28)
  val mask_add: Mask = named(29)
  val is_dir: Mask = named(30)
  val one_shot: Mask = named(31)


  val close : Mask = close_write | close_nowrite
  val move : Mask = move_from | move_to

  val all_events = access | modify | attrib |
    close | open | move | create |
    delete | delete_self | unmount
}
