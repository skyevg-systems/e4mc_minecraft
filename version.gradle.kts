import com.modrinth.minotaur.dependencies.DependencyType
import com.modrinth.minotaur.dependencies.ModDependency
import org.gradle.configurationcache.extensions.capitalized
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import xyz.deftu.gradle.tools.minecraft.CurseRelation
import xyz.deftu.gradle.tools.minecraft.CurseRelationType

plugins {
    java
    kotlin("jvm")
    id("xyz.deftu.gradle.multiversion")
    id("xyz.deftu.gradle.tools")
    id("xyz.deftu.gradle.tools.minecraft.loom")
    id("xyz.deftu.gradle.tools.shadow")
    id("xyz.deftu.gradle.tools.minecraft.releases")
}

val bundle by configurations.creating {
    if (mcData.isFabric) {
        configurations.getByName("include").extendsFrom(this)
    } else configurations.getByName("shade").extendsFrom(this)
}

toolkitLoomHelper {
    if (mcData.isForge) {
        useForgeMixin("e4mc_minecraft.mixins.json", true)
    }
}

java {
    withSourcesJar()
}

toolkitReleases {
    gameVersions.set(when (mcData.version) {
        11904 -> listOf("1.19", "1.19.1", "1.19.2", "1.19.3", "1.19.4")
        11802 -> listOf("1.17", "1.17.1", "1.18", "1.18.1", "1.18.2")
        else -> listOf()
    })
    releaseName.set("[${when (mcData.version) {
        11904 -> "1.19-"
        11802 -> "1.17-1.18.2"
        else -> mcData.versionStr
    }}] [${mcData.loader.name.capitalized()}] ${modData.version}")
    if (mcData.isFabric) {
        loaders.set(listOf("fabric", "quilt"))
    }
    modrinth {
        projectId.set("qANg5Jrr")
        useSourcesJar.set(true)
        if (mcData.isFabric) {
            dependencies.set(
                listOf(
                    ModDependency("P7dR8mSH", DependencyType.REQUIRED),
                    ModDependency("Ha28R6CL", DependencyType.REQUIRED),
                )
            )
        } else {
            dependencies.set(
                listOf(
                    ModDependency("ordsPcFz", DependencyType.REQUIRED),
                )
            )
        }
    }
    curseforge {
        projectId.set("849519")
        if (mcData.isFabric) {
            relations.set(
                listOf(
                    CurseRelation("fabric-api", CurseRelationType.REQUIRED),
                    CurseRelation("fabric-language-kotlin", CurseRelationType.REQUIRED)
                )
            )
        } else {
            relations.set(
                listOf(
                    CurseRelation("kotlin-for-forge", CurseRelationType.REQUIRED)
                )
            )
        }
    }
}

repositories {
    maven("https://maven.terraformersmc.com/")
    mavenCentral()
    maven("https://thedarkcolour.github.io/KotlinForForge/")
}

dependencies {
    implementation(kotlin("stdlib"))

    if (mcData.isFabric) {
        modImplementation(
            "net.fabricmc.fabric-api:fabric-api:${
                when (mcData.version) {
                    11904 -> "0.78.0+1.19.4"
                    11802 -> "0.76.0+1.18.2"
                    else -> throw IllegalStateException("Invalid MC version: ${mcData.version}")
                }
            }"
        )

        modImplementation("net.fabricmc:fabric-language-kotlin:1.8.6+kotlin.1.7.21")
    } else if (mcData.isForge) {
        implementation("thedarkcolour:kotlinforforge:3.8.0")
    }
    bundle(implementation("org.java-websocket:Java-WebSocket:1.5.3") {
        exclude(group = "org.slf4j")
    })
}
