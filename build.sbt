name := "serv-chisel"
version := "0.1"
scalaVersion := "2.12.12"

scalacOptions := Seq("-deprecation", "-unchecked", "-Xsource:2.11")

// SNAPSHOT repositories
resolvers ++= Seq(Resolver.sonatypeRepo("snapshots"), Resolver.sonatypeRepo("releases"))

libraryDependencies += "edu.berkeley.cs" %% "chisel3" % "3.4.0"
libraryDependencies += "edu.berkeley.cs" %% "chiseltest" % "0.3.0" % Test

scalaSource in Compile := baseDirectory.value / "src"
scalaSource in Test := baseDirectory.value / "test"
resourceDirectory in Test := baseDirectory.value / "test" / "resources"
