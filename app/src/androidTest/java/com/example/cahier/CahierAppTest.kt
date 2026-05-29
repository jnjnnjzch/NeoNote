/*
 *
 *  *
 *  *  * Copyright 2025 Google LLC. All rights reserved.
 *  *  *
 *  *  * Licensed under the Apache License, Version 2.0 (the "License");
 *  *  * you may not use this file except in compliance with the License.
 *  *  * You may obtain a copy of the License at
 *  *  *
 *  *  *     http://www.apache.org/licenses/LICENSE-2.0
 *  *  *
 *  *  * Unless required by applicable law or agreed to in writing, software
 *  *  * distributed under the License is distributed on an "AS IS" BASIS,
 *  *  * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  *  * See the License for the specific language governing permissions and
 *  *  * limitations under the License.
 *  *
 *
 */


package com.example.cahier

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@HiltAndroidTest
class CahierAppTest {

    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun homeScreen_displaysAndNavigatesToUnifiedNote() {
        composeTestRule.waitUntil(timeoutMillis = 5000) {
            composeTestRule
                .onAllNodesWithTag("btn-new-note")
                .fetchSemanticsNodes().isNotEmpty()
        }

        composeTestRule.onNodeWithText("Home").assertExists()
        composeTestRule.onNodeWithText("Settings").assertExists()

        composeTestRule.onNodeWithTag("btn-new-note").performClick()

        composeTestRule.onNodeWithText("Drawing title", useUnmergedTree = true).assertExists()
        composeTestRule.onNodeWithTag("text-container-editor").assertExists()
        composeTestRule.onNodeWithContentDescription("Brush").assertExists()
        composeTestRule.onNodeWithContentDescription("Color").assertExists()
        composeTestRule.onNodeWithContentDescription("Eraser").assertExists()
    }

    @Test
    fun homeScreen_showsNeoNoteBrand_andSingleNormalNewNoteEntry() {
        composeTestRule.onNodeWithText("NeoNote").assertExists()
        composeTestRule.onNodeWithText("Table-first ink canvas for S Pen").assertExists()
        composeTestRule.onNodeWithTag("btn-new-note").assertExists()
        composeTestRule.onNodeWithTag("build-badge").assertDoesNotExist()
        composeTestRule.onNodeWithTag("btn-new-table").assertDoesNotExist()
        composeTestRule.onNodeWithTag("btn-open-last").assertDoesNotExist()
        composeTestRule.onNodeWithTag("btn-stress-test").assertDoesNotExist()
        composeTestRule.onNodeWithTag("btn-export-test").assertDoesNotExist()
        composeTestRule.onNodeWithTag("btn-build-info").assertDoesNotExist()
    }

    @Test
    fun tableStarter_isOpenedFromDebugCenterOnly() {
        composeTestRule.onNodeWithText("Settings").performClick()
        composeTestRule.onNodeWithTag("switch-debug-mode").performClick()
        composeTestRule.onNodeWithTag("btn-open-debug-center").performClick()
        composeTestRule.onNodeWithText("TextContainer/Table Lab").performClick()

        composeTestRule.onNodeWithTag("text-container-editor").assertExists()
        composeTestRule.onNodeWithTag("tc-paragraph-before").assertExists()
        composeTestRule.onNodeWithTag("tc-paragraph-after").assertExists()
        composeTestRule.onNodeWithTag("table-cell-0-0").assertExists()
        composeTestRule.onNodeWithContentDescription("Brush").assertExists()
        composeTestRule.onNodeWithContentDescription("Color").assertExists()
    }

    @Test
    fun settings_still_shows_build_info() {
        composeTestRule.onNodeWithText("Settings").performClick()
        composeTestRule.onNodeWithTag("switch-debug-mode").performClick()
        composeTestRule.onNodeWithText("applicationId:", substring = true).assertExists()
        composeTestRule.onNodeWithText("versionName:", substring = true).assertExists()
        composeTestRule.onNodeWithText("gitSha:", substring = true).assertExists()
    }

    @Test
    fun homeScreen_navigateToSettings() {
        composeTestRule.onNodeWithText("Settings").assertExists().performClick()
        composeTestRule.onNodeWithText("Default notes app").assertExists()
    }

    @Test
    fun legacyTextCanvas_isNotEnteredFromNormalHome() {
        composeTestRule.onNodeWithText("Text note").assertDoesNotExist()
        composeTestRule.onNodeWithContentDescription("Text note").assertDoesNotExist()
    }
}
