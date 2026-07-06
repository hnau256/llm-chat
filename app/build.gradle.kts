plugins {
    application
    id(hnau.plugins.hnau.jvm.get().pluginId)
}

dependencies {
    implementation(libs.kotlinx.cli)
    implementation(libs.koog.agents)
    implementation(libs.tgbotapi)
}

application {
    mainClass = "org.hnau.llmchat.app.MainKt"
}