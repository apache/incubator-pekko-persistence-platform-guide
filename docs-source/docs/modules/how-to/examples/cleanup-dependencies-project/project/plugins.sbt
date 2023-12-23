// tag::remove-grpc-plugin[]
addSbtPlugin("org.apache.pekko" % "pekko-grpc-sbt-plugin" % "1.0.0")
// end::remove-grpc-plugin[]

addSbtPlugin("com.typesafe.sbt" % "sbt-native-packager" % "1.8.1")
addSbtPlugin("com.dwijnand" % "sbt-dynver" % "4.1.1")

addSbtPlugin("org.scalameta" % "sbt-scalafmt" % "2.4.2")
