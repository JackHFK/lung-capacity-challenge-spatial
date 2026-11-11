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
        maven {
            url = uri("https://artifact.bytedance.com/repository/Volcengine")
            name = "VolcengineMaven"
        }
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        // PICO SDK 仅从公网仓库解析，避免混用内部制品。
        exclusiveContent {
            forRepository {
                maven {
                    url = uri("https://artifact.bytedance.com/repository/Volcengine")
                    name = "VolcengineMaven"
                }
            }
            filter {
                includeGroupByRegex("com\\.pico\\..*")
            }
        }
    }
}

rootProject.name = "LungCapacityChallenge"

include(":app")
