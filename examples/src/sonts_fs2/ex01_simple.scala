package sonts_fs2

import fs2._

object ex01_simple extends App {

  val composed = Stream.range(1, 4) ++ Stream("a", "b", "c")
  println(composed.compile.toList)

  val zipped = Stream
    .range(1, 4)
    .zip(Stream("a", "b", "c"))
    .map { case (l, r) => s"$l$r" }

  println(zipped.compile.toList)
}
