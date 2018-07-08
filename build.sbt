import sbt.Keys.publishMavenStyle
import sbt.url
import xerial.sbt.Sonatype._

val scala211 = "2.11.12"
val scala212 = "2.12.6"

parallelExecution in ThisBuild := false

lazy val commonSettings = Seq(
  organization := "com.github.simerplaha",
  version := "0.2.1",
  scalaVersion := scala212
)

val publishSettings = Seq[Setting[_]](
  crossScalaVersions := Seq(scala211, scala212),
  sonatypeProfileName := "com.github.simerplaha",
  publishMavenStyle := true,
  licenses := Seq("APL2" -> url("http://www.apache.org/licenses/LICENSE-2.0.txt")),
  publish := {},
  publishLocal := {},
  sonatypeProjectHosting := Some(GitHubHosting("simerplaha", "Actor", "simer.j@gmail.com")),
  developers := List(
    Developer(id = "simerplaha", name = "Simer Plaha", email = "simer.j@gmail.com", url = url("https://github.com/simerplaha/Actor"))
  ),
  publishTo := sonatypePublishTo.value
)

lazy val Actor =
  (project in file("."))
    .settings(commonSettings)
    .settings(publishSettings)
    .settings(
      libraryDependencies += "org.scalatest" %% "scalatest" % "3.0.5" % Test,
      libraryDependencies += "io.reactivex" % "rxnetty-tcp" % "0.5.3-rc.1"

    )
