package org.hnau.llmchat.app.chat

data class ButtonIcon(
    val emoji: String,
) {

    override fun toString(): String = emoji

    companion object {

        val success = ButtonIcon("✅")
        val error = ButtonIcon("❌")
        val cancel = ButtonIcon("✖️")
        val warning = ButtonIcon("⚠️")
        val delete = ButtonIcon("🗑️")
        val pin = ButtonIcon("📌")
        val edit = ButtonIcon("✏\uFE0F")

        val home = ButtonIcon("🏠")
        val back = ButtonIcon("⬅")
        val next = ButtonIcon("➡️")
        val search = ButtonIcon("🔍")
        val mainMenu = ButtonIcon("📜")
        val download = ButtonIcon("📥")
        val upload = ButtonIcon("📤")

        val profile = ButtonIcon("👤")
        val settings = ButtonIcon("⚙️")
        val language = ButtonIcon("🌐")
        val security = ButtonIcon("🔒")
        val key = ButtonIcon("🔑")

        val balance = ButtonIcon("💰")
        val card = ButtonIcon("💳")
        val cash = ButtonIcon("💵")
        val shoppingCart = ButtonIcon("🛒")
        val catalog = ButtonIcon("🛍️")

        val help = ButtonIcon("❓")
        val info = ButtonIcon("ℹ️")
        val support = ButtonIcon("👨‍💻")
        val channel = ButtonIcon("📢")
        val feedback = ButtonIcon("💬")
        val rules = ButtonIcon("📝")

        val bonus = ButtonIcon("🎁")
        val fire = ButtonIcon("🔥")
        val star = ButtonIcon("⭐")
        val stats = ButtonIcon("📊")
        val notification = ButtonIcon("🔔")
        val calendar = ButtonIcon("📅")
    }
}