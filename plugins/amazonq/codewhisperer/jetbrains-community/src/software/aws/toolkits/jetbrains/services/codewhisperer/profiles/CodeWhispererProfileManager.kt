// Copyright 2025 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.codewhisperer.profiles

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.BaseState
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.project.Project
import com.intellij.util.xmlb.annotations.Property
import migration.software.aws.toolkits.jetbrains.services.codewhisperer.profiles.CodeWhispererProfileManager
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.codewhispererruntime.CodeWhispererRuntimeClient
import software.amazon.awssdk.services.codewhispererruntime.model.ListAvailableProfilesRequest
import software.amazon.awssdk.services.codewhispererruntime.model.Profile
import software.aws.toolkits.core.utils.debug
import software.aws.toolkits.core.utils.getLogger
import software.aws.toolkits.jetbrains.services.amazonq.calculateIfIamIdentityCenterConnection
import software.aws.toolkits.jetbrains.services.codewhisperer.credentials.CodeWhispererClientAdaptor
import software.aws.toolkits.jetbrains.services.codewhisperer.util.CodeWhispererConstants
import java.util.regex.Pattern

@Service(Service.Level.APP)
@State(name = "codeWhispererProfileStates", storages = [Storage("aws.xml")])
class DefaultCodeWhispererProfileManager: CodeWhispererProfileManager,
    PersistentStateComponent<CodeWhispererProfileState>, Disposable {

    private var profileState = CodeWhispererProfileState()


    private var selectedRegion: String? = null
    private var selectedEndPoint: String? = null
    private var selectedProfileArn: String? = null
    private var selectedProfileName: String? = null
    private var selectedProfileAccountId: String? = null


    override fun fetchAllAvailableProfiles(project: Project): List<ProfileUiItem>? {
        val isSso = calculateIfIamIdentityCenterConnection(project) {
            true
        }
        if (isSso == null) {
            LOG.debug { "fetchAllAvailableProfiles: Not SSO or no Project, return null" }
            return null
        }
        val profilesIad = fetchProfilesFromEndpoint(project, CodeWhispererConstants.Config.CODEWHISPERER_ENDPOINT, CodeWhispererConstants.Config.BearerClientRegion)
        val profilesFra = fetchProfilesFromEndpoint(project, CodeWhispererConstants.Config.CODEWHISPERER_ENDPOINT_FRA, CodeWhispererConstants.Config.BearerClientRegion_FRA)

        if (profilesIad.isEmpty() && profilesFra.isEmpty()) {
            //Todo exception?
            LOG.debug { "No Available Profiles" }
            return emptyList()
        }

        if (profilesIad.isNotEmpty() && profilesFra.isEmpty()) {
            setProfileAndNotify(CodeWhispererConstants.Config.CODEWHISPERER_ENDPOINT, CodeWhispererConstants.Config.BearerClientRegion, profilesIad.first().arn())
            return parseProfiles(profilesIad)
        }
        if (profilesIad.isEmpty() && profilesFra.isNotEmpty()) {
            setProfileAndNotify(CodeWhispererConstants.Config.CODEWHISPERER_ENDPOINT_FRA, CodeWhispererConstants.Config.BearerClientRegion_FRA, profilesFra.first().arn())
            return parseProfiles(profilesFra)
        }
        val combined = parseProfiles(profilesIad) + parseProfiles(profilesFra)
        return combined
    }

    private fun setProfileAndNotify(endpoint: String, region: Region, profileArn: String) {
        profileState.selectedEndpoint = endpoint
        profileState.selectedRegion = region.toString()
        profileState.selectedProfileArn = profileArn

        ApplicationManager.getApplication().messageBus
            .syncPublisher(ProfileSelectedListener.TOPIC)
            .profileSelected(endpoint, region, profileArn)
    }

    private fun fetchProfilesFromEndpoint(project: Project, endpoint: String, region: Region): List<Profile> {
        val tmpClient: CodeWhispererRuntimeClient = CodeWhispererClientAdaptor.getInstance(project).createTemporaryClientForEndpoint(
            endpoint = endpoint, region = region
        )
        val req = ListAvailableProfilesRequest.builder().maxResults(2).build()
        return try {
            val paginator = CodeWhispererClientAdaptor.getInstance(project).listAvailableProfilesPaginator(tmpClient, req)
            val list = paginator.flatMap { it.profiles() }.toList()
            LOG.debug { "fetchProfilesFromEndpoint($endpoint/$region) => count=${list.size}" }
            list
        } catch (e: Exception) {
            LOG.debug { "listAvailableProfiles failed for endpoint=$endpoint region=$region: ${e.message}" }
            emptyList()
        } finally {
            tmpClient.close()
        }
    }


    private fun parseProfiles(profiles: List<Profile>): List<ProfileUiItem> {
        val arnPattern = Pattern.compile("arn:aws:codewhisperer:([-\\.a-z0-9]{1,63}):(\\d{12}):profile/([a-zA-Z0-9]{12})")

        return profiles.mapNotNull { profile ->
            val matcher = arnPattern.matcher(profile.arn())

            if (matcher.matches()) {
                val region = matcher.group(1)
                val accountId = matcher.group(2)

                ProfileUiItem(
                    profileName = profile.profileName(),
                    accountId = accountId,
                    region = region,
                    arn = profile.arn()
                )
            } else {
                null
            }
        }
    }

    override fun getSelectedProfile(): ProfileUiItem? {
        return with(profileState) {
            if (!selectedProfileName.isNullOrEmpty() &&
                !selectedProfileAccountId.isNullOrEmpty() &&
                !selectedRegion.isNullOrEmpty() &&
                !selectedProfileArn.isNullOrEmpty())
            {
                ProfileUiItem(
                    profileName = selectedProfileName!!,
                    accountId = selectedProfileAccountId!!,
                    region = selectedRegion!!,
                    arn = selectedProfileArn!!
                )
            } else {
                null
            }
        }
    }



    companion object {
        private val LOG = getLogger<CodeWhispererProfileManager>()
    }

    override fun dispose() {}


    override fun getState(): CodeWhispererProfileState {
        val state = CodeWhispererProfileState()
        state.selectedEndpoint = this.selectedEndPoint
        state.selectedRegion = this.selectedRegion
        state.selectedProfileArn = this.selectedProfileArn
        state.selectedProfileName = this.selectedProfileName
        state.selectedProfileAccountId = this.selectedProfileAccountId
        return state
    }

    override fun loadState(state: CodeWhispererProfileState) {
        this.selectedEndPoint = state.selectedEndpoint
        this.selectedRegion = state.selectedRegion
        this.selectedProfileArn = state.selectedProfileArn
        this.selectedProfileName = state.selectedProfileName
        this.selectedProfileAccountId = state.selectedProfileAccountId
    }

}

class CodeWhispererProfileState: BaseState() {
    @get:Property
    var selectedEndpoint by string()

    @get:Property
    var selectedRegion by string()

    @get:Property
    var selectedProfileArn by string()

    @get:Property
    var selectedProfileName by string()

    @get:Property
    var selectedProfileAccountId by string()
}
data class ProfileUiItem(
    val profileName: String,
    val accountId: String,
    val region: String,
    val arn: String
)

