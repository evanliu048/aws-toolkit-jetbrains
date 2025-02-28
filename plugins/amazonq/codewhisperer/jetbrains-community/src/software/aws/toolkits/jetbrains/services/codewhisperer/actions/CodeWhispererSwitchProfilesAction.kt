// Copyright 2025 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.codewhisperer.actions
import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAware
import software.amazon.awssdk.profiles.Profile
import software.aws.toolkits.jetbrains.services.codewhisperer.profile.CodeWhispererProfileDialog
import software.aws.toolkits.resources.message

class CodeWhispererSwitchProfilesAction :
    AnAction(
        message("codewhisperer.actions.switch_profiles.title"),
        null,
        AllIcons.Actions.SwapPanels
    ),
    DumbAware {

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
            val mockProfiles = listOf(
                CodeWhispererProfileDialog.ProfileItem("ACME platform work - IAD", "Lorem ipsum dolor sit amet, consectetur adipiscing elit."),
                CodeWhispererProfileDialog.ProfileItem("EU Payments Team - FRA", "Lorem ipsum dolor sit amet, consectetur adipiscing elit."),
                CodeWhispererProfileDialog.ProfileItem("AWS Security Team - US", "Security compliance and monitoring.")
            )

            val defaultProfile = mockProfiles[1] // 预设默认 Profile（比如 "EU Payments Team - FRA"）


            CodeWhispererProfileDialog(
            project,
            profiles = mockProfiles,
            selectedProfile = defaultProfile).show()
    }
}

