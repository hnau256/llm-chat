plugins {
    application
    id(hnau.plugins.ksp.get().pluginId)
    id(hnau.plugins.hnau.jvm.get().pluginId)
}

dependencies {
    implementation(libs.koog.agents)
    implementation(libs.tgbotapi)
    implementation(libs.ktor.http)
    implementation(libs.ktor.server.cio)
    implementation(libs.sqlite.jdbc)
    implementation(libs.flyway.core)
}

application {
    mainClass = "org.hnau.llmchat.app.MainKt"
}