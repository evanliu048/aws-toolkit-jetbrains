// Copyright 2025 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.codewhisperer.profile

import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogPanel
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.dsl.builder.*
import migration.software.aws.toolkits.jetbrains.services.codewhisperer.profiles.CodeWhispererProfileManager
import software.aws.toolkits.jetbrains.utils.notifyInfo
import software.aws.toolkits.resources.message
import javax.swing.ButtonGroup
import javax.swing.JComponent

class CodeWhispererProfileDialog(
    private val project: Project,
    private val profiles: List<ProfileItem>,
    private var selectedProfile: ProfileItem //default
) : DialogWrapper(project), Disposable {

    private lateinit var panel: DialogPanel
    private var selectedOption: ProfileItem? = selectedProfile //user selected

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
//        val profileList = CodeWhispererProfileManager.getInstance().fetchAllAvailableProfiles(project);
//        println(profileList)
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
                        val radioButton = radioButton(profile.name, profile)
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
            selectedOption?.let { switchProfile(it) }
        }

        close(OK_EXIT_CODE)
    }

    private fun switchProfile(profile: ProfileItem) {
        notifyInfo(
            title = message("codewhisperer.switchProfiles.dialog.panel.title"),
            content = message("codewhisperer.profile.usage", profile.name),
            project = project
        )
        // TODO: Implement actual profile switching logic
    }
    data class ProfileItem(
        val name: String,
        val description: String
    )

}
