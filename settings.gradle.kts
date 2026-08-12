@file:Suppress("UnstableApiUsage")

pluginManagement {
    // Les regles detekt maison et les plugins de convention sont un build inclus,
    // pas un module du projet : ils s'executent sur la JVM de Gradle et n'ont rien
    // a faire dans le graphe de dependances de l'application. Voir D16 dans
    // docs/11-decisions.md.
    //
    // Ici, c'est ce qui rend les plugins `hexaphore.*` resolubles par identifiant.
    includeBuild("build-logic")

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

// Le meme build, declare une seconde fois hors de pluginManagement, et ce n'est pas
// une redite : les deux declarations ne font pas la meme chose. Celle de
// pluginManagement resout les identifiants de plugin ; celle-ci substitue le projet
// local a la coordonnee `app.hexaphore.buildlogic:detekt-rules` que le build racine
// declare en detektPlugins. Sans elle, Gradle va chercher cette coordonnee sur Maven
// Central, ou elle n'existe evidemment pas.
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

// Tranche 2. L'ecran de validation : le point de convergence des quatre modes de
// saisie, ecrit pour n lignes des le premier jour.
include(":feature:entry")

// Tranche 3. La recherche.
//
// :tooling:ciqual-import est le seul module qui n'entre dans aucun APK : il
// convertit le XML de l'ANSES en base SQLite, sur la machine de developpement.
// Il est ici et non dans build-logic parce que son parseur porte une regle du
// projet -- une valeur inconnue n'est pas un zero -- et qu'une regle du projet se
// teste avec `./gradlew check`.
include(":tooling:ciqual-import")
include(":data:food")
include(":feature:search")

// Tranche 4. Le profil, le journal de poids et les objectifs versionnes.
//
// :data:profile et non une extension de :data:diary : un objectif n'est pas une
// entree de journal, et le nom d'un module est ce qui empeche d'y ranger n'importe
// quoi. Il porte trois ports que rien d'autre ne lit.
include(":data:profile")
include(":feature:onboarding")

// :feature:settings naît avec la seule section qui a du contenu, « Profil et
// objectifs ». Les quatre autres que docs/02 prevoit dependent des tranches 6 et 8 ;
// le hub qui les rassemble naitra avec la deuxieme, faute de quoi il serait un ecran
// de transit vers une destination unique.
include(":feature:settings")

// Tranche 5. Le scan.
//
// :integration et non :data : un module :data adapte un port a un stockage qui
// nous appartient, celui-ci adapte un service tiers dont on ne decide ni le
// schema ni la disponibilite. Le nom dit lequel des deux on lit quand une reponse
// surprend.
include(":integration:openfoodfacts")
