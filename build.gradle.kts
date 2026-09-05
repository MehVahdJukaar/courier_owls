plugins {
    id("com.possible-triangle.core")
    id("com.possible-triangle.common") apply false
    id("com.possible-triangle.fabric") apply false
    id("com.possible-triangle.neoforge") apply false
    id("net.mehvahdjukaar.candlelight") version "1.2.4" apply false
}

mod {
    additional.add("mod_description")
    additional.add("mod_credits")
    additional.add("mod_license")
    additional.add("mod_homepage")
    additional.add("mod_authors")
    additional.add("mod_github")
    additional.add("moonlight_min_version")
}


subprojects {

    apply(plugin = "com.possible-triangle.core")
    apply(plugin = "net.mehvahdjukaar.candlelight")
    apply(plugin = "maven-publish")

    dependencies {
        compileOnly("net.mehvahdjukaar:candlelight:1.2.6")
    }

    upload {
        maven {
            nexus()
        }
        curseforge {
            dependencies {
                required("selene")
                optional("supplementaries")
            }
        }
        modrinth {
            dependencies {
                required("moonlight")
                optional("supplementaries")
            }
        }

        forEach {
            changelog = rootProject.file("changelog.md").readText()
            versionName = "${mod.id.get()}-${mod.version.get()}-${project.name}"
        }
    }

    tasks.withType<JavaCompile> {
        options.compilerArgs.addAll(listOf("-Xmaxerrs", "4000"))
    }

    repositories {
        nexus()

        mavenLocal()
        mavenCentral()

        flatDir {
            dirs("mods")
        }

        maven { url = uri("https://jitpack.io") }
        maven {
            url = uri("https://cursemaven.com")
            content { includeGroup("curse.maven") }
        }
        maven {
            url = uri("https://api.modrinth.com/maven") // Modrinth, group is maven.modrinth
            content { includeGroup("maven.modrinth") }
        }

        maven { url = uri("https://maven.neoforged.net/releases") }
        maven { url = uri("https://maven.architectury.dev") }
        maven { url = uri("https://maven.parchmentmc.org") }
        maven { url = uri("https://maven.neoforged.net") }
    }
}
