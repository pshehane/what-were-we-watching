pluginManagement {
    repositories {
        // [.] instead of an escaped dot: same regex, and it survives every editor
        // and shell that might touch this file.
        google {
            content {
                includeGroupByRegex("androidx.*")
                includeGroupByRegex("com[.]android.*")
                includeGroupByRegex("com[.]google.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google {
            content {
                includeGroupByRegex("androidx.*")
                includeGroupByRegex("com[.]android.*")
                includeGroupByRegex("com[.]google.*")
            }
        }
        mavenCentral()
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

rootProject.name = "WhatWereWeWatching"
include(":app")
