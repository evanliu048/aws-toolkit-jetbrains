// Copyright 2024 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.amazonqFeatureDev.session

import software.aws.toolkits.core.utils.getLogger
import software.aws.toolkits.core.utils.warn
import software.aws.toolkits.jetbrains.services.amazonq.messages.MessagePublisher
import software.aws.toolkits.jetbrains.services.amazonqFeatureDev.FEATURE_NAME
import software.aws.toolkits.jetbrains.services.amazonqFeatureDev.messages.sendAnswerPart
import software.aws.toolkits.jetbrains.services.amazonqFeatureDev.messages.sendUpdatePlaceholder
import software.aws.toolkits.jetbrains.services.amazonqFeatureDev.util.CancellationTokenSource
import software.aws.toolkits.jetbrains.services.amazonqFeatureDev.util.deleteUploadArtifact
import software.aws.toolkits.jetbrains.services.amazonqFeatureDev.util.uploadArtifactToS3
import software.aws.toolkits.jetbrains.services.cwc.controller.chat.telemetry.getStartUrl
import software.aws.toolkits.jetbrains.settings.CodeWhispererSettings
import software.aws.toolkits.resources.message
import software.aws.toolkits.telemetry.AmazonqTelemetry
import software.aws.toolkits.telemetry.AmazonqUploadIntent
import software.aws.toolkits.telemetry.Result
import java.util.UUID

private val logger = getLogger<PrepareCodeGenerationState>()

class PrepareCodeGenerationState(
    override var tabID: String,
    override var token: CancellationTokenSource?,
    override var approach: String,
    private var config: SessionStateConfig,
    val filePaths: List<NewFileZipInfo>,
    val deletedFiles: List<DeletedFileInfo>,
    val references: List<CodeReferenceGenerated>,
    var uploadId: String,
    override var currentIteration: Int?,
    private var messenger: MessagePublisher,
    override var codeGenerationRemainingIterationCount: Int? = null,
    override var codeGenerationTotalIterationCount: Int? = null,
    override var diffMetricsProcessed: DiffMetricsProcessed,
) : SessionState {
    override val phase = SessionStatePhase.CODEGEN
    override suspend fun interact(action: SessionStateAction): SessionStateInteraction<SessionState> {
        val startTime = System.currentTimeMillis()
        var result: Result = Result.Succeeded
        var failureReason: String? = null
        var failureReasonDesc: String? = null
        var zipFileLength: Long? = null
        val nextState: SessionState
        try {
            messenger.sendAnswerPart(tabId = this.tabID, message = message("amazonqFeatureDev.chat_message.uploading_code"))
            messenger.sendUpdatePlaceholder(tabId = this.tabID, newPlaceholder = message("amazonqFeatureDev.chat_message.uploading_code"))

            val isAutoBuildFeatureEnabled = CodeWhispererSettings.getInstance().isAutoBuildFeatureEnabled(this.config.repoContext.workspaceRoot.path)
            val repoZipResult = config.repoContext.getProjectZip(isAutoBuildFeatureEnabled = isAutoBuildFeatureEnabled)
            val zipFileChecksum = repoZipResult.checksum
            zipFileLength = repoZipResult.contentLength
            val fileToUpload = repoZipResult.payload

            val uploadId = UUID.randomUUID()
            val uploadUrlResponse = config.featureDevService.createUploadUrl(
                config.conversationId,
                zipFileChecksum,
                zipFileLength,
                uploadId.toString()
            )

            uploadArtifactToS3(uploadUrlResponse.uploadUrl(), fileToUpload, zipFileChecksum, zipFileLength, uploadUrlResponse.kmsKeyArn())
            deleteUploadArtifact(fileToUpload)

            this.uploadId = uploadId.toString()
            messenger.sendAnswerPart(tabId = this.tabID, message = message("amazonqFeatureDev.placeholder.context_gathering_complete"))
            messenger.sendUpdatePlaceholder(tabId = this.tabID, newPlaceholder = message("amazonqFeatureDev.placeholder.context_gathering_complete"))
            nextState = CodeGenerationState(
                tabID = this.tabID,
                approach = "", // No approach needed,
                config = this.config,
                uploadId = this.uploadId,
                currentIteration = this.currentIteration,
                repositorySize = zipFileLength.toDouble(),
                messenger = messenger,
                token = this.token,
                diffMetricsProcessed = diffMetricsProcessed
            )
        } catch (e: Exception) {
            result = Result.Failed
            failureReason = e.javaClass.simpleName
            failureReasonDesc = e.message
            logger.warn(e) { "$FEATURE_NAME: Code uploading failed: ${e.message}" }
            throw e
        } finally {
            AmazonqTelemetry.createUpload(
                amazonqConversationId = config.conversationId,
                amazonqRepositorySize = zipFileLength?.toDouble(),
                amazonqUploadIntent = AmazonqUploadIntent.TASKASSISTPLANNING,
                result = result,
                reason = failureReason,
                reasonDesc = failureReasonDesc,
                duration = (System.currentTimeMillis() - startTime).toDouble(),
                credentialStartUrl = getStartUrl(config.featureDevService.project)
            )
        }
        // It is essential to interact with the next state outside try-catch block for  the telemetry to capture events for the states separately
        return nextState.interact(action)
    }
}
