package com.swan1127.repland

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.ComposeContentTestRule

/** Expand the real preview before locating a today fixture in a retained QA store. */
internal fun expandTodayTaskGroup(rule: ComposeContentTestRule) {
    rule.onNodeWithTag("task-groups-scroll").performScrollToNode(hasTestTag("task-group-今天"))
    val expand = hasText("查看全部") and hasAnyAncestor(hasTestTag("task-group-今天"))
    if (rule.onAllNodes(expand).fetchSemanticsNodes().isNotEmpty()) {
        rule.onNode(expand).performScrollTo().assertIsDisplayed().performClick()
    }
}
