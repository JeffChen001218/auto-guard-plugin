apply(plugin = "maven-publish")

configure<PublishingExtension> {
    publications.create<MavenPublication>("release") {
        groupId = "com.github.jeffchen001218"
        artifactId = "${properties["plugin_name"]}"
        version = "1.0.0"
        pom.packaging = "jar"
        artifact("$buildDir/libs/${artifactId}-${properties["plugin_version"]}.jar")

    }
    repositories {
        mavenLocal()
    }
}