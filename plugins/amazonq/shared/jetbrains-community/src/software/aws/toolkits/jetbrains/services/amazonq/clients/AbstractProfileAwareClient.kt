// Copyright 2025 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.amazonq.clients

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.codewhispererruntime.CodeWhispererRuntimeClient
import software.aws.toolkits.core.region.AwsRegion
import software.aws.toolkits.core.utils.error
import software.aws.toolkits.core.utils.getLogger
import software.aws.toolkits.core.utils.info
import software.aws.toolkits.jetbrains.core.AwsClientManager
import software.aws.toolkits.jetbrains.core.credentials.ToolkitConnectionManager
import software.aws.toolkits.jetbrains.core.credentials.pinning.QConnection
import software.aws.toolkits.jetbrains.core.region.AwsRegionProvider
import software.aws.toolkits.jetbrains.services.ProfileSelectedListener

abstract class AbstractProfileAwareClient(
    protected open val project: Project
) : Disposable {
    @Volatile
    protected var bearerClient: CodeWhispererRuntimeClient? = null

    init {
        initProfileSelectedListener()
    }

    // 监听 ProfileSelectedListener
    private fun initProfileSelectedListener() {
        ApplicationManager.getApplication().messageBus.connect(this).subscribe(
            ProfileSelectedListener.TOPIC,
            object : ProfileSelectedListener {
                override fun profileSelected(endpoint: String, region: Region, profileArn: String) {
                    updateBearerClient(endpoint, region)
                }
            }
        )
    }

    protected open fun updateBearerClient(endpoint: String, region: Region) {
        try {
            val awsRegion = AwsRegionProvider.getInstance()[region.id()] ?: error("unknown region returned from Q browser")
            val newConnectionSettings = connection().getConnectionSettings().withRegion(awsRegion)
            bearerClient = AwsClientManager.getInstance().getClient<CodeWhispererRuntimeClient>(newConnectionSettings)
            LOG.info { "Successfully updated bearerClient with endpoint: $endpoint, region: $region" }
        } catch (e: Exception) {
            LOG.error(e) { "Failed to update bearerClient with endpoint: $endpoint, region: $region" }
        }
    }

    fun connection() = ToolkitConnectionManager
        .getInstance(project)
        .activeConnectionForFeature(QConnection.getInstance())
        ?: error("Attempted to use connection while one does not exist")

    protected fun bearerClient(): CodeWhispererRuntimeClient {
        if (bearerClient != null) return bearerClient as CodeWhispererRuntimeClient
        bearerClient = AwsClientManager.getInstance().getClient<CodeWhispererRuntimeClient>(connection().getConnectionSettings())
        return bearerClient as CodeWhispererRuntimeClient
    }

    override fun dispose() {
        bearerClient?.close()
        bearerClient = null
    }

    companion object {
        private val LOG = getLogger<AbstractProfileAwareClient>()
    }
}
