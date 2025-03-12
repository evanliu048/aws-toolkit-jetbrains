// Copyright 2025 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package migration.software.aws.toolkits.jetbrains.services.codewhisperer.profiles

import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.codewhispererruntime.model.Profile
import software.aws.toolkits.jetbrains.services.codewhisperer.profiles.ProfileUiItem

interface CodeWhispererProfileManager {


    fun fetchAllAvailableProfiles(project: Project) : List<ProfileUiItem>?

    fun setProfileAndNotify(project: Project, profile: Profile, endpoint: String, region: Region)

    fun activeProfile(project: Project): ProfileUiItem?

    fun switchProfile(project: Project, profileUiItem: ProfileUiItem?)



        companion object {
        fun getInstance(): CodeWhispererProfileManager = service()
    }
}
