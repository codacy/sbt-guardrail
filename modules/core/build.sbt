import codacy.CodacySbt.autoImport._

enablePlugins(SbtPlugin)
enablePlugins(BuildInfoPlugin)

name := "sbt-guardrail-core"
description := "Core of sbt-guardrail plugin, for custom forks of guardrail"

addCompilerPlugin("org.typelevel" %% "kind-projector" % "0.13.4" cross CrossVersion.full)

// Explicitly pinning this to a binding so we can grep for it.
// Must be a version of com.codacy guardrail-core published to the Codacy private S3 bucket.
val guardrailCoreVersion = "0.0.1"

// The s3:// URL handler is only installed by the S3 resolver plugin in onLoad, i.e. after
// build.sbt is evaluated, so `url("s3://...")` (and thus `privateMvnResolver`) fails here.
// Build the URL with an explicit dummy handler instead; Ivy resolves it through the S3 downloader.
resolvers += {
  val noHandler = new java.net.URLStreamHandler {
    def openConnection(u: java.net.URL): java.net.URLConnection = throw new java.io.IOException("s3 is handled by Ivy")
  }
  Resolver.url("Codacy Private Mvn bucket", new java.net.URL(null, "s3://private.mvn.codacy.com/", noHandler))(Resolver.ivyStylePatterns)
}

// Dependencies
libraryDependencies ++= Seq(
  "com.codacy" %% "guardrail-core" % guardrailCoreVersion,
  "org.snakeyaml" % "snakeyaml-engine" % "3.2"
)

buildInfoKeys := Seq[BuildInfoKey](organization, version)
buildInfoPackage := "dev.guardrail.sbt"
