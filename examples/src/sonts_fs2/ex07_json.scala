 package sonts_fs2

 import java.util.UUID

 import cats.effect._
 import cats.implicits._
 import org.http4s.dsl.Http4sDsl
 import org.http4s.server.blaze.BlazeBuilder

 import scala.concurrent.ExecutionContext

 object domain {
   import java.util.UUID

   import slick.jdbc.H2Profile.api._

   class DbCustomer(tag: Tag) extends Table[(UUID, String)](tag, "customers") {
     def id: Rep[UUID] = column[UUID]("id", O.PrimaryKey)

     def email: Rep[String] = column[String]("email")

     def * = (id, email)
   }
 }

 object slick_infrastructure {

   import domain._
   import slick.jdbc.H2Profile
   import slick.jdbc.H2Profile.api._

   class DatabaseProvider[F[_]: ContextShift: Sync: Async] {

     val customers = TableQuery[DbCustomer]

     private val db: H2Profile.backend.Database = H2Profile.api.Database.forConfig("h2mem1")

     private def runAsync[A](action: DBIO[A]): F[A] = {
       import scala.util.{Failure, Success}

       Sync[F].guarantee[A](
         Async[F].async { cb =>
           implicit val executionContext: ExecutionContext = ExecutionContext.global

           db.run(action).onComplete {
             case Success(value) => cb(Right(value))
             case Failure(error) => cb(Left(error))
           }
         }
       )(ContextShift[F].shift)
     }

     def runStream[A](
       action: StreamingDBIO[_, A]
     )(implicit C: ConcurrentEffect[F]): fs2.Stream[F, A] = {
       import fs2.interop.reactivestreams._

       db.stream(action).toStream.covary[F]
     }
   }
   object DatabaseProvider {
     def apply[F[_]: Sync: Async: ContextShift]: F[DatabaseProvider[F]] = {
       implicit val executionContext: ExecutionContext = ExecutionContext.global

       val dbProvider = new DatabaseProvider[F]
       val actions = DBIO.seq(
         dbProvider.customers.schema.create,
         dbProvider.customers ++= Seq.fill(100000)(
           (UUID.randomUUID(), s"${UUID.randomUUID().toString.replace("-", "")}@test.com")
         )
       )

       dbProvider.runAsync(actions).map(_ => dbProvider)
     }
   }
 }

 object json {
   import io.circe.Encoder
   import io.circe.generic.semiauto._

   final case class CustomerJson(uuid: UUID, email: String)
   implicit val customerJsonEncoder: Encoder[CustomerJson] = deriveEncoder[CustomerJson]
 }

 object ex07_json extends IOApp with Http4sDsl[IO] {

   import io.circe.syntax._
   import json._
   import org.http4s.HttpRoutes
   import slick.jdbc.H2Profile.api._
   import slick_infrastructure._

   override def run(args: List[String]): IO[ExitCode] =
     DatabaseProvider[IO].flatMap { implicit dbProvider =>
       def findAll: fs2.Stream[IO, (UUID, String)] =
         dbProvider.runStream(dbProvider.customers.result)

       val service: HttpRoutes[IO] = HttpRoutes.of[IO] {
         case _ @GET -> Root =>
           Ok(
             fs2.Stream.emit("[") ++
             findAll.map { case (id, email) => CustomerJson(id, email).asJson.noSpaces }.intersperse(",") ++
             fs2.Stream.emit("]")
           )
       }

       BlazeBuilder[IO]
         .bindHttp(8080, "0.0.0.0")
         .mountService(service, "/")
         .serve
         .compile
         .last
         .map {
           case Some(ExitCode.Success) => ExitCode.Success
           case _ => ExitCode.Error
         }
     }
 }
