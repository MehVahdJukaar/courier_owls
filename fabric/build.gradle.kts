plugins {
    id("com.possible-triangle.fabric")
}

fabric {
    dependOn(project(":common"))
    accessWidener(project(":common"))
}

val moonlight_version = extra["moonlight_version"] as String
val codecui_version = extra["codecui_version"] as String
val fabric_api_version = extra["fabric_api_version"] as String
val supplementaries_version = extra["supplementaries_version"] as String

dependencies {
    modImplementation("net.fabricmc.fabric-api:fabric-api:${fabric_api_version}")
    modImplementation("net.mehvahdjukaar:moonlight-fabric:${moonlight_version}")
    modRuntimeOnly("net.mehvahdjukaar:codecui-fabric:${codecui_version}")

    //soft dep, compile only: the common sources are built again in here and one of them touches its api
    modCompileOnly("net.mehvahdjukaar:supplementaries-neoforge:${supplementaries_version}:neoforge@jar")
}
