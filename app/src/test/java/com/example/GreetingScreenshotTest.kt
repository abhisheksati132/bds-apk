package com.example

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.example.data.model.ConversationEntity
import com.example.ui.screens.ChatListScreen
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.viewmodel.UiState
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel8, sdk = [36])
class GreetingScreenshotTest {

  @get:Rule val composeTestRule = createComposeRule()

  @Test
  fun greeting_screenshot() {
    val dummyConvs = listOf(
      ConversationEntity(
        id = 1,
        peerId = "u_julianne",
        peerName = "Julianne Deff",
        peerHandle = "julianne.7492",
        avatarBgColorHex = "#DDE1FF",
        avatarTextColorHex = "#001453",
        lastMessage = "I'll check the server logs right now...",
        lastTimestamp = System.currentTimeMillis(),
        unreadCount = 0,
        isOnline = true
      )
    )

    composeTestRule.setContent {
      MyApplicationTheme {
        ChatListScreen(
          uiState = UiState(),
          conversations = dummyConvs,
          onOpenConversation = {},
          onOpenNewChat = {},
          onOpenVault = {},
          onSearchQueryChanged = {},
          onFilterSelected = {}
        )
      }
    }

    composeTestRule.onRoot().captureRoboImage(filePath = "src/test/screenshots/greeting.png")
  }
}
