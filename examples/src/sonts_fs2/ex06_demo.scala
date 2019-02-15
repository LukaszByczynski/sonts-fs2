package sonts_fs2

import java.nio.file.{Files, Path}

import cats._
import cats.effect.{ExitCode, IO, IOApp, Sync}
import cats.implicits._
import fs2._

object infrastructure {

  trait Logger[F[_]] {
    def info(msg: String): F[Unit]
    def error(msg: String, th: Throwable): F[Unit]
  }
  object Logger {
    def apply[F[_]](clazz: Class[_])(implicit S: Sync[F]): F[Logger[F]] = S.delay {
      new Logger[F] {
        private val className: String = clazz.getName

        override def info(msg: String): F[Unit] = S.delay(println(s"[info:$className]: $msg"))
        override def error(msg: String, th: Throwable): F[Unit] = S.delay(println(s"[error:$className]: $msg: $th"))
      }
    }
  }

  trait ParquetWriter[F[_]] {
    def path: F[Path]
    def write[A](element: A): F[Unit]
    def close(): F[Unit]
  }
  object ParquetWriter {
    def apply[F[_]: FlatMap](filePath: Path)(implicit S: Sync[F]): F[ParquetWriter[F]] =
      for {
        logger <- Logger[F](getClass)
        writer <- S.delay(
          new ParquetWriter[F] {
            override def path: F[Path] =
              S.pure(filePath)
            override def write[A](element: A): F[Unit] =
              logger.info(s"Write to element: $element to parquet file")
            override def close(): F[Unit] =
              logger.info(s"Close parquet writer for file: $filePath")
          }
        )
        _ <- logger.info(s"Parquet writer for file: $filePath created")
      } yield writer
  }

  trait Uploader[F[_]] {
    def upload(from: Path, to: String): F[Unit]
  }
  object Uploader {
    def apply[F[_]](implicit S: Sync[F]): F[Uploader[F]] =
      for {
        logger <- Logger[F](getClass)
        writer <- S.delay(
          new Uploader[F] {
            override def upload(from: Path, to: String): F[Unit] =
              logger.info(s"Uploaded file from $from to $to on HDFS")
          }
        )
        _ <- logger.info("HDFS uploader created")
      } yield writer
  }
}

object streaming {

  import infrastructure._

  private class ParquetPipe[F[_], A](chunkSize: Int)(implicit S: Sync[F]) {


    private val emptyInputStream = Pull.done
    private def endOfStream(writer: ParquetWriter[F]) = {
      val closeWriter = Pull
        .acquire(
          for {
            _ <- writer.close()
            path <- writer.path
          } yield path
        )(path => S.delay(if (path.toFile.exists()) path.toFile.delete()))

      closeWriter.flatMap(Pull.output1) >> emptyInputStream
    }

    private def createNewWriter(chunk: Chunk[A], tailStream: Stream[F, A]) =
      Pull
        .eval(ParquetWriter[F](Files.createTempFile("scala", "rocks")))
        .flatMap { writer =>
          go(Stream.chunk(chunk) ++ tailStream, Some(writer))
            .handleErrorWith(err => Pull.eval(writer.close()) >> fs2.Pull.raiseError[F](err))
        }

    private def writeToParquet(writer: ParquetWriter[F], chunk: Chunk[A], tailStream: Stream[F, A]) =
      Pull.eval(chunk.traverse(writer.write)) >> go(tailStream, Some(writer))

    /*_*/
    private def go(inputStream: Stream[F, A], state: Option[ParquetWriter[F]]): Pull[F, Path, Unit] =
      inputStream.pull.unconsN(chunkSize, allowFewer = true).flatMap {
        (_, state) match {
          case (None, None) => emptyInputStream
          case (None, Some(writer)) => endOfStream(writer)
          case (Some((chunk, tailStream)), None) => createNewWriter(chunk, tailStream)
          case (Some((chunk, tailStream)), Some(writer)) => writeToParquet(writer, chunk, tailStream)
        }
      }

    def pipe: Pipe[F, A, Path] = in => go(in, None).stream
  }
  object ParquetPipe {
    def apply[F[_], A](chunkSize: Int)(implicit S: Sync[F]): Pipe[F, A, Path] = new ParquetPipe[F, A](chunkSize).pipe
  }
}

object ex06_demo extends IOApp {

  import infrastructure._
  import streaming._

  override def run(args: List[String]): IO[ExitCode] =
    Stream
      .eval(Uploader[IO])
      .flatMap { uploader =>
        Stream
          .range(1, 10)
          .covary[IO]
          .through(ParquetPipe[IO, Int](2))
          .zipWithIndex
          .evalMap {
            case (path, index) =>
              for {
                _ <- uploader.upload(path, s"/test/hdfs/test-$index.parquet")
                _ <- IO(path.toFile.delete())
              } yield index
          }
      }
      .compile
      .lastOrError
      .as(ExitCode.Success)

}
