name := "serv-chisel"
version := "0.1"
scalaVersion := "2.12.12"

scalacOptions := Seq("-deprecation", "-unchecked", "-Xsource:2.11")

// SNAPSHOT repositories
resolvers ++= Seq(Resolver.sonatypeRepo("snapshots"), Resolver.sonatypeRepo("releases"))

// paso is published on github
val ghrealm = "GitHub Package Registry"
val ghurl = "https://maven.pkg.github.com/ekiwi/paso"
credentials ++= sys.env.get("GITHUB_TOKEN").map(t => Credentials(ghrealm, "maven.pkg.github.com", "_", t))
resolvers += s"Github Package Registry at $ghurl" at ghurl

libraryDependencies += "edu.berkeley.cs" %% "chisel3" % "3.4.1"
libraryDependencies += "edu.berkeley.cs" %% "chiseltest" % "0.3.1" % Test
libraryDependencies += "edu.berkeley.cs" %% "paso" % "0.4.1-27-86e1f0ee" % Test
libraryDependencies += "org.scalatest" %% "scalatest" % "3.2.2" % Test

scalaSource in Compile := baseDirectory.value / "src"
scalaSource in Test := baseDirectory.value / "test"
resourceDirectory in Test := baseDirectory.value / "test" / "resources"
