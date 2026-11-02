pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven("https://jitpack.io")
    }
}

rootProject.name = "SekaiTuneDesktop"

include(":morideobfuscator")
project(":morideobfuscator").projectDir = file("../morideobfuscator")

include(":core")
project(":core").projectDir = file("../core")

include(":lyrics:betterlyrics")
project(":lyrics:betterlyrics").projectDir = file("../lyrics/betterlyrics")

include(":lyrics:kugou")
project(":lyrics:kugou").projectDir = file("../lyrics/kugou")

include(":lyrics:lrclib")
project(":lyrics:lrclib").projectDir = file("../lyrics/lrclib")

include(":lyrics:paxsenix")
project(":lyrics:paxsenix").projectDir = file("../lyrics/paxsenix")

include(":lyrics:simpmusic")
project(":lyrics:simpmusic").projectDir = file("../lyrics/simpmusic")

include(":lyrics:unison")
project(":lyrics:unison").projectDir = file("../lyrics/unison")

include(":lyrics:youlyplus")
project(":lyrics:youlyplus").projectDir = file("../lyrics/youlyplus")

include(":shared")
include(":desktopUi")
include(":desktopApp")
