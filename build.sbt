name := "sonts-fs2"
version := "0.1"
scalaVersion := "2.12.8"

scalacOptions ++= Seq(
  "-deprecation",
  "-encoding", "utf-8",
  "-explaintypes",
  "-feature",
  "-language:existentials",
  "-language:higherKinds",
  "-Ypartial-unification"
)

libraryDependencies ++= Seq(
  "co.fs2" %% "fs2-core" % "1.0.1",
  "co.fs2" %% "fs2-io"   % "1.0.1"
)
