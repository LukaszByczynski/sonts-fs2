name := "sonts-fs2"
version := "0.1"
scalaVersion := "2.12.8"

scalacOptions ++= Seq(
  "-deprecation",
  "-encoding",
  "utf-8",
  "-explaintypes",
  "-feature",
  "-language:existentials",
  "-language:higherKinds",
  "-Ypartial-unification"
)

libraryDependencies ++= Seq(
  "co.fs2"                %% "fs2-core"             % "1.0.1",
  "co.fs2"                %% "fs2-io"               % "1.0.1",
  "com.typesafe.slick"    %% "slick"                % "3.2.3",
  "com.typesafe.slick"    %% "slick-hikaricp"       % "3.2.3",
  "com.github.zainab-ali" %% "fs2-reactive-streams" % "0.8.0",
  "com.h2database"        % "h2"                    % "1.4.197",
  "org.http4s"            %% "http4s-blaze-server"  % "0.19.0",
  "org.http4s"            %% "http4s-circe"         % "0.19.0",
  "org.http4s"            %% "http4s-dsl"           % "0.19.0",
  "io.circe"              %% "circe-generic"        % "0.11.0",
  "io.circe"              %% "circe-literal"        % "0.11.0",
  "ch.qos.logback"        % "logback-classic"       % "1.2.3"
)
