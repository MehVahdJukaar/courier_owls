plugins {
    id("com.possible-triangle.neoforge")
}

neoforge {
    dependOn(project(":common"))
    accessWidener(project(":common"))
}

val moonlight_curse = extra["moonlight_curse_neoforge"] as String
val moonlight_version = extra["moonlight_version"] as String
val codecui_version = extra["codecui_version"] as String
val supplementaries_version = extra["supplementaries_version"] as String

dependencies {
    modImplementation("curse.maven:selene-499980:${moonlight_curse}")
    //curse jar has no module metadata so the at variant isnt there, take it from the maven one
    accessTransformers("net.mehvahdjukaar:moonlight-neoforge:${moonlight_version}")
    modRuntimeOnly("net.mehvahdjukaar:codecui-neoforge:${codecui_version}")

    //soft dep, compile only: the common sources are built again in here and one of them touches its api
    modCompileOnly("net.mehvahdjukaar:supplementaries-neoforge:${supplementaries_version}:neoforge@jar")

}
