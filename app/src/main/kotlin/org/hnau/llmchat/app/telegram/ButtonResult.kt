package org.hnau.llmchat.app.telegram

data class ButtonResult(
    val navigateBackCount: Int,
) {

    companion object {

        val noNavigate = ButtonResult(
            navigateBackCount = 0,
        )

        val navigateBack = ButtonResult(
            navigateBackCount = 1,
        )
    }
}