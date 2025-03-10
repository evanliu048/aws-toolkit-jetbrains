// Copyright 2025 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.codewhisperer.actions
import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAware
import migration.software.aws.toolkits.jetbrains.services.codewhisperer.profiles.CodeWhispererProfileManager
import software.aws.toolkits.jetbrains.services.codewhisperer.profile.CodeWhispererProfileDialog
import software.aws.toolkits.jetbrains.services.codewhisperer.profiles.ProfileUiItem
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
            var mockProfiles = CodeWhispererProfileManager.getInstance().fetchAllAvailableProfiles(project)
            mockProfiles = listOf(
                ProfileUiItem("ACME platform work", "533267146179", "us-east-1", "arn:aws:codewhisperer:us-west-2:533267146179:profile/PYWHHDDNKQP9"),
                ProfileUiItem("EU Payments Team","123122323123", "eu-central-1","arn:aws:codewhisperer:us-west-2:123122323123:profile/PYWHHDDNKQP9")
//                ProfileUiItem("AWS Security Team", "143342324234", "eu-central-2","arn:aws:codewhisperer:us-west-2:143342324234:profile/PYWHHDDNKQP9")
            )
        var selectedProfile = CodeWhispererProfileManager.getInstance().getSelectedProfile()
        selectedProfile = mockProfiles[0]
        //Todo handle no profiles case
//        if(!mockProfiles.isEmpty() &&selectedProfile !=null ){
            CodeWhispererProfileDialog(
                project,
                profiles = mockProfiles,
                selectedProfile = selectedProfile
            ).show()
//        }
    }
}

