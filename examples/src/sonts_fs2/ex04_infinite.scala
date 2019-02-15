package sonts_fs2

import cats.implicits._
import cats.effect.{ExitCode, IO, IOApp}
import fs2._

object ex04_infinite extends IOApp {

  override def run(args: List[String]): IO[ExitCode] = {

    val tick = System.currentTimeMillis()
    Stream
      .repeatEval(IO(System.currentTimeMillis()))
      .take(100)
      .fold(0l)((_, r) => r)
      .compile
      .lastOrError
      .flatMap { result =>
        IO(println(result - tick))
      }
      .as(ExitCode.Success)
  }

}
