@file:Suppress("UnstableApiUsage")

pluginManagement {
    repositories {
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
}

dependencyResolutionManagement {
    // Un module qui declare son propre depot contourne le catalogue et la revue :
    // on l'interdit plutot que de le decouvrir six mois plus tard.
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

// Les regles detekt maison sont un build inclus, pas un module du projet :
// elles s'executent sur la JVM de Gradle et n'ont rien a faire dans le graphe
// de dependances de l'application. Voir D16 dans docs/11-decisions.md.
includeBuild("build-logic")

// Permet d'ecrire projects.core.designsystem plutot que project(":core:designsystem") :
// une faute de frappe devient une erreur de compilation du script.
enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

rootProject.name = "hexaphore"

// Trois modules, et pas un de plus. Les treize autres de docs/06-architecture.md
// naissent le jour ou ils ont un fichier a contenir. Voir docs/12-plan-de-developpement.md.
include(":app")
include(":domain")
include(":core:designsystem")

// Tranche 1. Les implementations en memoire qui vivent ici ne sont pas des
// bequilles de test : ce sont les premieres implementations des ports, celles
// contre lesquelles l'ecran est ecrit avant que Room n'existe.
include(":core:testing")

// :core:common naît maintenant qu'il a de quoi exister : les implementations de
// Clock et DispatcherProvider, qui vivaient dans :app faute de mieux (D19).
include(":core:common")
include(":core:database")
include(":data:diary")
include(":feature:home")
