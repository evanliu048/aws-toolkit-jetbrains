// Copyright 2025 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.codewhisperer.profiles

import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import migration.software.aws.toolkits.jetbrains.services.codewhisperer.profiles.CodeWhispererProfileManager
import software.amazon.awssdk.services.codewhispererruntime.model.CodeWhispererRuntimeException
import software.amazon.awssdk.services.codewhispererruntime.model.ListAvailableProfilesRequest
import software.amazon.awssdk.services.codewhispererruntime.model.Profile
import software.aws.toolkits.core.utils.debug
import software.aws.toolkits.core.utils.getLogger
import software.aws.toolkits.jetbrains.core.credentials.ToolkitConnection
import software.aws.toolkits.jetbrains.core.credentials.ToolkitConnectionManagerListener
import software.aws.toolkits.jetbrains.services.amazonq.calculateIfIamIdentityCenterConnection
import software.aws.toolkits.jetbrains.services.codewhisperer.credentials.CodeWhispererClientAdaptor

@Service(Service.Level.APP)
class DefaultCodeWhispererProfileManager: CodeWhispererProfileManager, ToolkitConnectionManagerListener, Disposable {

    private fun buildListAvailableProfilesRequest(): ListAvailableProfilesRequest {
        return ListAvailableProfilesRequest.builder()
            .maxResults(2)
            .build()
    }
    override fun fetchAllAvailableProfiles(project: Project) : List<Profile>? =
        calculateIfIamIdentityCenterConnection(project) {
            val listAvailableProfilesRequest = buildListAvailableProfilesRequest()
            println("request: $listAvailableProfilesRequest")
            val listAvailableProfilesResponse = try {
                CodeWhispererClientAdaptor.getInstance(project).listAvailableProfilesPaginator(listAvailableProfilesRequest)
            } catch (e: Exception) {
                val requestId = (e as? CodeWhispererRuntimeException)?.requestId()
                val logMessage = "ListAvailableProfilesResponse: failed due to unknown error ${e.message}, requestId: ${requestId.orEmpty()}"
                LOG.debug { logMessage }
                null
            }
        if (listAvailableProfilesResponse != null) {
            println("response: ${listAvailableProfilesResponse.first().profiles()}")
        }
            val profileList = listAvailableProfilesResponse?.flatMap { it.profiles() }
            ?.toList()
            return@calculateIfIamIdentityCenterConnection profileList
    }

    companion object {
        private val LOG = getLogger<CodeWhispererProfileManager>()
    }

    override fun dispose() {}
    override fun activeConnectionChanged(newConnection: ToolkitConnection?) {
        TODO("Not yet implemented")
    }
}
