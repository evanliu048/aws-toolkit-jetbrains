// Copyright 2024 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.amazonqFeatureDev.session
import software.aws.toolkits.core.utils.getLogger
import software.aws.toolkits.core.utils.warn
import software.aws.toolkits.jetbrains.services.amazonqFeatureDev.FEATURE_NAME
import software.aws.toolkits.jetbrains.services.amazonqFeatureDev.util.deleteUploadArtifact
import software.aws.toolkits.jetbrains.services.amazonqFeatureDev.util.uploadArtifactToS3
import software.aws.toolkits.jetbrains.services.cwc.controller.chat.telemetry.getStartUrl
import software.aws.toolkits.telemetry.AmazonqTelemetry
import software.aws.toolkits.telemetry.AmazonqUploadIntent
import software.aws.toolkits.telemetry.Result

private val logger = getLogger<PrepareCodeGenerationState>()
class PrepareRefinementState(
    override var approach: String,
    override var tabID: String,
    var config: SessionStateConfig
) : SessionState {
    override val phase = SessionStatePhase.APPROACH
    override suspend fun interact(action: SessionStateAction): SessionStateInteraction {
        val startTime = System.currentTimeMillis()
        var result: Result = Result.Succeeded
        var failureReason: String? = null
        var zipFileLength: Long? = null
        try {
            val repoZipResult = config.repoContext.getProjectZip()
            val zipFileChecksum = repoZipResult.checksum
            zipFileLength = repoZipResult.contentLength
            val fileToUpload = repoZipResult.payload

            val uploadUrlResponse = config.featureDevService.createUploadUrl(
                config.conversationId,
                zipFileChecksum,
                zipFileLength
            )

            uploadArtifactToS3(uploadUrlResponse.uploadUrl(), fileToUpload, zipFileChecksum, zipFileLength, uploadUrlResponse.kmsKeyArn())

            deleteUploadArtifact(fileToUpload)

            val nextState = RefinementState(approach, tabID, config, uploadUrlResponse.uploadId(), 0)
            return nextState.interact(action)
        } catch (e: Exception) {
            result = Result.Failed
            failureReason = e.javaClass.simpleName
            logger.warn(e) { "$FEATURE_NAME: Code uploading failed: ${e.message}" }
            throw e
        } finally {
            AmazonqTelemetry.createUpload(
                amazonqConversationId = config.conversationId,
                amazonqRepositorySize = zipFileLength?.toDouble(),
                amazonqUploadIntent = AmazonqUploadIntent.TASKASSISTPLANNING,
                result = result,
                reason = failureReason,
                duration = (System.currentTimeMillis() - startTime).toDouble(),
                credentialStartUrl = getStartUrl(config.featureDevService.project)
            )
        }
    }
}
