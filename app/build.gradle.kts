plugins {
    application
    id(hnau.plugins.hnau.jvm.get().pluginId)
}

dependencies {
    implementation(libs.koog.agents)
    implementation(libs.tgbotapi)
    implementation(libs.ktor.http)
    implementation(libs.ktor.server.cio)
}

application {
    mainClass = "org.hnau.llmchat.app.MainKt"
}