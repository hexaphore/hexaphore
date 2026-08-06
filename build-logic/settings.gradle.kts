@file:Suppress("UnstableApiUsage")

pluginManagement {
    repositories {
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        // AGP n'est publie que sur le depot Google. Les plugins de convention
        // compilent contre son API, donc ce build en a besoin comme le principal.
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
    versionCatalogs {
        // Le build inclus lit le catalogue du build principal : sans ca, il aurait
        // ses propres versions, et la regle du catalogue unique serait fausse des
        // le premier fichier.
        create("libs") {
            from(files("../gradle/libs.versions.toml"))
        }
    }
}

rootProject.name = "build-logic"

include(":detekt-rules")

// Les plugins de convention. Ils vivent a cote des regles detekt parce qu'ils ont
// la meme nature : du code qui s'execute sur la JVM de Gradle et jamais sur un
// telephone. Voir D16 dans docs/11-decisions.md.
include(":convention")
