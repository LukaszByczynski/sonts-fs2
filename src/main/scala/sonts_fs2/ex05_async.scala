package sonts_fs2
import java.util.concurrent.Executors

import cats.effect.{ExitCode, IO, IOApp}
import cats.implicits._
import fs2._

import scala.concurrent.ExecutionContext

object ex05_async extends IOApp {

  def printWithThreadName(msg: String) = IO {
    println(s"${Thread.currentThread().getName}: $msg")
  }

  override def run(args: List[String]): IO[ExitCode] = {

    val threadPool = Executors.newSingleThreadExecutor
    val singleCs = IO.contextShift(ExecutionContext.fromExecutor(threadPool))

    Stream
      .range(1, 10)
      .covary[IO]
      .parEvalMap(4)(i => printWithThreadName(s"Value $i") >> IO.pure(i))
      .fold(0)(_ + _)
      .evalMap(result => IO.shift(singleCs) *> printWithThreadName(s"Result: $result"))
      .compile
      .lastOrError
      .flatMap(_ => IO(threadPool.shutdown()))
      .as(ExitCode.Success)
  }
}
