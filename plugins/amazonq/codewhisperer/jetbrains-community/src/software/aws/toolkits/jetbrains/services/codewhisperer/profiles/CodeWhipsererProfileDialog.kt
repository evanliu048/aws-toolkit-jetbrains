// Copyright 2025 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.codewhisperer.profile

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogPanel
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.dsl.builder.*
import com.intellij.util.application
import migration.software.aws.toolkits.jetbrains.services.codewhisperer.profiles.CodeWhispererProfileManager
import software.amazon.awssdk.regions.Region
import software.aws.toolkits.jetbrains.core.credentials.ToolkitConnectionManagerListener
import software.aws.toolkits.jetbrains.services.codewhisperer.profiles.ProfileSelectedListener
import software.aws.toolkits.jetbrains.services.codewhisperer.profiles.ProfileUiItem
import software.aws.toolkits.jetbrains.services.codewhisperer.util.CodeWhispererConstants
import software.aws.toolkits.jetbrains.utils.notifyInfo
import software.aws.toolkits.resources.message
import javax.swing.ButtonGroup
import javax.swing.JComponent

class CodeWhispererProfileDialog(
    private var project: Project,
    private var profiles: List<ProfileUiItem>,
    private var selectedProfile: ProfileUiItem //default
) : DialogWrapper(project), Disposable {

    private lateinit var panel: DialogPanel
    private var selectedOption: ProfileUiItem? = selectedProfile //user selected

    override fun dispose() {
        super.dispose()
    }

    init {
        title = message("codewhisperer.switchProfiles.dialog.title")
        setOKButtonText(message("general.ok"))
        setCancelButtonText(message("general.cancel"))
        init()
    }

    // Todo: decide if need an external link, will link to JB help doc otherwise
    override fun getHelpId(): String {
        return ""
    }
    override fun createCenterPanel(): JComponent {
        panel = panel {
            row {
                label(message("codewhisperer.switchProfiles.dialog.panel.title")).bold()
            }
            row {
                text(message("codewhisperer.switchProfiles.dialog.panel.description"))
            }.bottomGap(BottomGap.MEDIUM)

            lateinit var group: ButtonGroup
            buttonsGroup {
                profiles.forEach { profile ->
                    row {
                        val radioButton = radioButton(profile.profileName, profile)
                        radioButton.actionListener { _, component ->
                            if (component.isSelected) {
                                selectedOption = profile
                            }
                        }
                    }
                }
            }.bind({ selectedOption!! }, { selectedOption = it })

        }
        return panel
    }
    override fun doOKAction() {
        panel.apply()
        if (selectedOption != selectedProfile) {
            notifyInfo(
                title = message("codewhisperer.switchProfiles.dialog.panel.title"),
                content = message("codewhisperer.profile.usage", selectedProfile.profileName),
                project = project
            )

            if (selectedProfile.region === CodeWhispererConstants.Config.BearerClientRegion.toString()) {
                ApplicationManager.getApplication().messageBus
                    .syncPublisher(ProfileSelectedListener.TOPIC)
                    .profileSelected(CodeWhispererConstants.Config.CODEWHISPERER_ENDPOINT, Region.of(selectedProfile.region), selectedProfile.arn)

            } else if (selectedProfile.region === CodeWhispererConstants.Config.BearerClientRegion_FRA.toString()) {
                ApplicationManager.getApplication().messageBus
                    .syncPublisher(ProfileSelectedListener.TOPIC)
                    .profileSelected(CodeWhispererConstants.Config.CODEWHISPERER_ENDPOINT_FRA, Region.of(selectedProfile.region), selectedProfile.arn)

            }
            close(OK_EXIT_CODE)
        }
    }


    data class ProfileItem(
        val name: String,
        val description: String
    )

}
