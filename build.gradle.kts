plugins {
    id("xyz.deftu.gradle.multiversion-root")
}

preprocess {
    val fabric11904 = createNode("1.19.4-fabric", 11903, "yarn")
    val fabric11802 = createNode("1.18.2-fabric", 11802, "yarn")
    val forge11904 = createNode("1.19.4-forge", 11903, "srg")
    val forge11802 = createNode("1.18.2-forge", 11802, "srg")

    fabric11904.link(forge11904)
    forge11904.link(forge11802)
    forge11802.link(fabric11802)
}

val releaseAllVersions by tasks.registering {
    listOf(
        "1.18.2-fabric",
        "1.19.4-fabric",
        "1.18.2-forge",
        "1.19.4-forge"
    ).forEach { version ->
        dependsOn(":$version:releaseProject")
    }
}