plugins {
    id(hnau.plugins.ksp.get().pluginId)
    id(hnau.plugins.hnau.jvm.get().pluginId)
}


dependencies {
    implementation(libs.tgbotapi)
    implementation(libs.ktor.http)
    implementation(libs.ktor.server)
    implementation(libs.commonmark)
    implementation(project(":common"))
    implementation(project(":chat:api"))
}
