name := "serv-chisel"
version := "0.1"
scalaVersion := "2.12.7"

scalacOptions := Seq("-deprecation", "-unchecked", "-Xsource:2.11")

libraryDependencies += "edu.berkeley.cs" %% "chisel3" % "3.3.0-RC1"
libraryDependencies += "edu.berkeley.cs" %% "chisel-testers2" % "0.2.0-RC1" % Test

scalaSource in Compile := baseDirectory.value / "src"
scalaSource in Test := baseDirectory.value / "test"
