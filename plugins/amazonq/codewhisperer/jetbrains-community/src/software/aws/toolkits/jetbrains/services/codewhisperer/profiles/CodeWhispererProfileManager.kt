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
import com.intellij.util.xmlb.annotations.MapAnnotation
import com.intellij.util.xmlb.annotations.Property
import migration.software.aws.toolkits.jetbrains.services.codewhisperer.profiles.CodeWhispererProfileManager
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.codewhispererruntime.CodeWhispererRuntimeClient
import software.amazon.awssdk.services.codewhispererruntime.model.ListAvailableProfilesRequest
import software.amazon.awssdk.services.codewhispererruntime.model.Profile
import software.aws.toolkits.core.region.AwsRegion
import software.aws.toolkits.core.utils.debug
import software.aws.toolkits.core.utils.getLogger
import software.aws.toolkits.core.utils.warn
import software.aws.toolkits.jetbrains.core.AwsClientManager
import software.aws.toolkits.jetbrains.core.credentials.AwsBearerTokenConnection
import software.aws.toolkits.jetbrains.core.credentials.ToolkitConnectionManager
import software.aws.toolkits.jetbrains.core.credentials.pinning.CodeWhispererConnection
import software.aws.toolkits.jetbrains.core.region.AwsRegionProvider
import software.aws.toolkits.jetbrains.services.ProfileSelectedListener
import software.aws.toolkits.jetbrains.services.amazonq.calculateIfIamIdentityCenterConnection
import software.aws.toolkits.jetbrains.services.codewhisperer.credentials.CodeWhispererClientAdaptor
import software.aws.toolkits.jetbrains.services.codewhisperer.util.CodeWhispererConstants
import java.util.Collections
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

    // Map to store connectionId to its active customization
    private val connectionIdToActiveProfile = Collections.synchronizedMap<String, ProfileUiItem>(mutableMapOf())



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
            setProfileAndNotify(project, profilesIad.first(), CodeWhispererConstants.Config.CODEWHISPERER_ENDPOINT, CodeWhispererConstants.Config.BearerClientRegion)
            return parseProfiles(profilesIad)
        }
        if (profilesIad.isEmpty() && profilesFra.isNotEmpty()) {
            setProfileAndNotify(project, profilesFra.first(), CodeWhispererConstants.Config.CODEWHISPERER_ENDPOINT_FRA, CodeWhispererConstants.Config.BearerClientRegion_FRA)
            return parseProfiles(profilesFra)
        }
        val combined = parseProfiles(profilesIad) + parseProfiles(profilesFra)
        return combined
    }

    override fun setProfileAndNotify(project: Project, profile: Profile, endpoint: String, region: Region) {
        calculateIfIamIdentityCenterConnection(project){
            val arnPattern = Pattern.compile("arn:aws:codewhisperer:([-\\.a-z0-9]{1,63}):(\\d{12}):profile/([a-zA-Z0-9]{12})")
            val matcher = arnPattern.matcher(profile.arn())
            if (matcher.matches()) {
                val accountId = matcher.group(2)
                connectionIdToActiveProfile[it.id] = ProfileUiItem (profile.profileName(),  accountId, region.id(), profile.arn(), endpoint)
            }
            else{
                LOG.warn { "setProfileAndNotify: profile arn is not valid" }
            }
            ApplicationManager.getApplication().messageBus
                .syncPublisher(ProfileSelectedListener.TOPIC)
                .profileSelected(endpoint, region, profile.arn())
        }

    }

    override fun activeProfile(project: Project): ProfileUiItem? {
        val selectedProfile = calculateIfIamIdentityCenterConnection(project) { connectionIdToActiveProfile[it.id] }
        return selectedProfile
    }

    override fun switchProfile(project: Project, newProfile: ProfileUiItem?) {
        calculateIfIamIdentityCenterConnection(project) {
            if (newProfile == null || newProfile.arn.isEmpty()) {
                return@calculateIfIamIdentityCenterConnection
            }
            val oldPro = connectionIdToActiveProfile[it.id]
            if (oldPro != newProfile) {
                newProfile.let { newPro ->
                    connectionIdToActiveProfile[it.id] = newPro
                }

                LOG.debug { "Switch from profile $oldPro to $newProfile" }

                //TODO refresh UI? if newProfileUiItem == null

            }
        }
    }
    private fun fetchProfilesFromEndpoint(project: Project, endpoint: String, region: Region): List<Profile> {
        val connection = ToolkitConnectionManager.getInstance(project).activeConnectionForFeature(CodeWhispererConnection.getInstance())
        connection as? AwsBearerTokenConnection ?: run {
            LOG.warn { "$connection is not a bearer token connection when fetch profiles from endpoint $endpoint" }
            return emptyList()
        }
        val awsRegion = AwsRegionProvider.getInstance()[region.id()] ?: error("unknown region returned from Q browser")
        val tmpClient: CodeWhispererRuntimeClient = AwsClientManager.getInstance().getClient<CodeWhispererRuntimeClient>(connection.getConnectionSettings().withRegion(awsRegion))

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
                    arn = profile.arn(),
                    endpoint = CodeWhispererConstants.Config.getEndpointForRegion(Region.of(region)))
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
        state.connectionIdToActiveProfile.putAll(this.connectionIdToActiveProfile)
        return state
    }

    override fun loadState(state: CodeWhispererProfileState) {
        this.selectedEndPoint = state.selectedEndpoint
        this.selectedRegion = state.selectedRegion
        this.selectedProfileArn = state.selectedProfileArn
        this.selectedProfileName = state.selectedProfileName
        this.selectedProfileAccountId = state.selectedProfileAccountId
        connectionIdToActiveProfile.clear()
        connectionIdToActiveProfile.putAll(state.connectionIdToActiveProfile)
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

    @get:Property
    @get:MapAnnotation
    val connectionIdToActiveProfile by map<String, ProfileUiItem>()

}
data class ProfileUiItem(
    val profileName: String,
    val accountId: String,
    val region: String,
    val arn: String,
    val endpoint: String
)

