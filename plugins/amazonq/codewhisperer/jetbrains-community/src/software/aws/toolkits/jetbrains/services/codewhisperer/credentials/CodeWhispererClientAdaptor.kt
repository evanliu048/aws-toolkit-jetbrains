// Copyright 2022 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.codewhisperer.credentials

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.util.text.nullize
import software.amazon.awssdk.auth.credentials.AnonymousCredentialsProvider
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.codewhisperer.CodeWhispererClient
import software.amazon.awssdk.services.codewhisperer.model.CreateCodeScanRequest
import software.amazon.awssdk.services.codewhisperer.model.CreateCodeScanResponse
import software.amazon.awssdk.services.codewhisperer.model.GetCodeScanRequest
import software.amazon.awssdk.services.codewhisperer.model.GetCodeScanResponse
import software.amazon.awssdk.services.codewhisperer.model.ListCodeScanFindingsRequest
import software.amazon.awssdk.services.codewhisperer.model.ListCodeScanFindingsResponse
import software.amazon.awssdk.services.codewhispererruntime.CodeWhispererRuntimeClient
import software.amazon.awssdk.services.codewhispererruntime.model.ChatInteractWithMessageEvent
import software.amazon.awssdk.services.codewhispererruntime.model.ChatMessageInteractionType
import software.amazon.awssdk.services.codewhispererruntime.model.CompletionType
import software.amazon.awssdk.services.codewhispererruntime.model.CreateUploadUrlRequest
import software.amazon.awssdk.services.codewhispererruntime.model.CreateUploadUrlResponse
import software.amazon.awssdk.services.codewhispererruntime.model.Dimension
import software.amazon.awssdk.services.codewhispererruntime.model.GenerateCompletionsRequest
import software.amazon.awssdk.services.codewhispererruntime.model.GenerateCompletionsResponse
import software.amazon.awssdk.services.codewhispererruntime.model.GetCodeFixJobRequest
import software.amazon.awssdk.services.codewhispererruntime.model.GetCodeFixJobResponse
import software.amazon.awssdk.services.codewhispererruntime.model.GetTestGenerationResponse
import software.amazon.awssdk.services.codewhispererruntime.model.IdeCategory
import software.amazon.awssdk.services.codewhispererruntime.model.InlineChatUserDecision
import software.amazon.awssdk.services.codewhispererruntime.model.ListAvailableCustomizationsRequest
import software.amazon.awssdk.services.codewhispererruntime.model.ListAvailableProfilesRequest
import software.amazon.awssdk.services.codewhispererruntime.model.ListAvailableProfilesResponse
import software.amazon.awssdk.services.codewhispererruntime.model.ListFeatureEvaluationsResponse
import software.amazon.awssdk.services.codewhispererruntime.model.SendTelemetryEventResponse
import software.amazon.awssdk.services.codewhispererruntime.model.StartCodeFixJobRequest
import software.amazon.awssdk.services.codewhispererruntime.model.StartCodeFixJobResponse
import software.amazon.awssdk.services.codewhispererruntime.model.StartTestGenerationResponse
import software.amazon.awssdk.services.codewhispererruntime.model.SuggestionState
import software.amazon.awssdk.services.codewhispererruntime.model.TargetCode
import software.amazon.awssdk.services.codewhispererruntime.model.UserIntent
import software.aws.toolkits.core.utils.debug
import software.aws.toolkits.core.utils.getLogger
import software.aws.toolkits.core.utils.warn
import software.aws.toolkits.jetbrains.core.AwsClientManager
import software.aws.toolkits.jetbrains.core.awsClient
import software.aws.toolkits.jetbrains.core.credentials.AwsBearerTokenConnection
import software.aws.toolkits.jetbrains.core.credentials.ToolkitConnection
import software.aws.toolkits.jetbrains.core.credentials.ToolkitConnectionManager
import software.aws.toolkits.jetbrains.core.credentials.ToolkitConnectionManagerListener
import software.aws.toolkits.jetbrains.core.credentials.pinning.CodeWhispererConnection
import software.aws.toolkits.jetbrains.services.amazonq.codeWhispererUserContext
import software.aws.toolkits.jetbrains.services.codewhisperer.customization.CodeWhispererCustomization
import software.aws.toolkits.jetbrains.services.codewhisperer.explorer.CodeWhispererExplorerActionManager
import software.aws.toolkits.jetbrains.services.codewhisperer.language.CodeWhispererProgrammingLanguage
import software.aws.toolkits.jetbrains.services.codewhisperer.model.SessionContextNew
import software.aws.toolkits.jetbrains.services.codewhisperer.profiles.ProfileSelectedListener
import software.aws.toolkits.jetbrains.services.codewhisperer.service.RequestContext
import software.aws.toolkits.jetbrains.services.codewhisperer.service.RequestContextNew
import software.aws.toolkits.jetbrains.services.codewhisperer.service.ResponseContext
import software.aws.toolkits.jetbrains.services.codewhisperer.util.CodeWhispererConstants
import software.aws.toolkits.jetbrains.services.codewhisperer.util.CodeWhispererUtil.getTelemetryOptOutPreference
import software.aws.toolkits.jetbrains.services.codewhisperer.util.transform
import software.aws.toolkits.telemetry.CodewhispererCompletionType
import software.aws.toolkits.telemetry.CodewhispererSuggestionState
import java.time.Instant
import java.util.concurrent.TimeUnit
import kotlin.reflect.KProperty0
import kotlin.reflect.jvm.isAccessible

// TODO: move this file to package "/client"
// As the connection is project-level, we need to make this project-level too
@Deprecated("Methods can throw a NullPointerException if callee does not check if connection is valid")
interface CodeWhispererClientAdaptor : Disposable {
    val project: Project

    fun generateCompletionsPaginator(
        firstRequest: GenerateCompletionsRequest,
    ): Sequence<GenerateCompletionsResponse>

    fun createUploadUrl(
        request: CreateUploadUrlRequest,
    ): CreateUploadUrlResponse

    fun createCodeScan(
        request: CreateCodeScanRequest,
        isSigv4: Boolean = shouldUseSigv4Client(project),
    ): CreateCodeScanResponse

    fun getCodeScan(
        request: GetCodeScanRequest,
        isSigv4: Boolean = shouldUseSigv4Client(project),
    ): GetCodeScanResponse

    fun listCodeScanFindings(
        request: ListCodeScanFindingsRequest,
        isSigv4: Boolean = shouldUseSigv4Client(project),
    ): ListCodeScanFindingsResponse

    fun startCodeFixJob(request: StartCodeFixJobRequest): StartCodeFixJobResponse

    fun getCodeFixJob(request: GetCodeFixJobRequest): GetCodeFixJobResponse

    fun listAvailableCustomizations(): List<CodeWhispererCustomization>

    fun listAvailableProfilesPaginator(
        bearerClient: CodeWhispererRuntimeClient,
        request: ListAvailableProfilesRequest
    ): Sequence<ListAvailableProfilesResponse>

    fun createTemporaryClientForEndpoint(endpoint: String, region: Region): CodeWhispererRuntimeClient

    fun startTestGeneration(uploadId: String, targetCode: List<TargetCode>, userInput: String): StartTestGenerationResponse

    fun getTestGeneration(jobId: String, jobGroupName: String): GetTestGenerationResponse

    fun sendUserTriggerDecisionTelemetry(
        requestContext: RequestContext,
        responseContext: ResponseContext,
        completionType: CodewhispererCompletionType,
        suggestionState: CodewhispererSuggestionState,
        suggestionReferenceCount: Int,
        lineCount: Int,
        numberOfRecommendations: Int,
        acceptedCharCount: Int,
    ): SendTelemetryEventResponse

    fun sendUserTriggerDecisionTelemetry(
        sessionContext: SessionContextNew,
        requestContext: RequestContextNew,
        responseContext: ResponseContext,
        completionType: CodewhispererCompletionType,
        suggestionState: CodewhispererSuggestionState,
        suggestionReferenceCount: Int,
        lineCount: Int,
        numberOfRecommendations: Int,
        acceptedCharCount: Int,
    ): SendTelemetryEventResponse

    fun sendCodePercentageTelemetry(
        language: CodeWhispererProgrammingLanguage,
        customizationArn: String?,
        acceptedTokenCount: Long,
        totalTokenCount: Long,
        unmodifiedAcceptedTokenCount: Long?,
        userWrittenCodeCharacterCount: Long?,
        userWrittenCodeLineCount: Long?,
    ): SendTelemetryEventResponse

    fun sendUserModificationTelemetry(
        sessionId: String,
        requestId: String,
        language: CodeWhispererProgrammingLanguage,
        customizationArn: String?,
        acceptedCharacterCount: Int,
        unmodifiedAcceptedTokenCount: Int,
    ): SendTelemetryEventResponse

    fun sendCodeScanTelemetry(
        language: CodeWhispererProgrammingLanguage,
        codeScanJobId: String?,
        scope: CodeWhispererConstants.CodeAnalysisScope,
    ): SendTelemetryEventResponse

    fun sendCodeScanSucceededTelemetry(
        language: CodeWhispererProgrammingLanguage,
        codeScanJobId: String?,
        scope: CodeWhispererConstants.CodeAnalysisScope,
        findings: Int,
    ): SendTelemetryEventResponse

    fun sendCodeScanFailedTelemetry(
        language: CodeWhispererProgrammingLanguage,
        codeScanJobId: String?,
        scope: CodeWhispererConstants.CodeAnalysisScope,
    ): SendTelemetryEventResponse

    fun sendCodeFixGenerationTelemetry(
        language: CodeWhispererProgrammingLanguage,
        codeFixJobId: String?,
        ruleId: String?,
        detectorId: String?,
        findingId: String?,
        linesOfCodeGenerated: Int?,
        charsOfCodeGenerated: Int?,
    ): SendTelemetryEventResponse

    fun sendCodeFixAcceptanceTelemetry(
        language: CodeWhispererProgrammingLanguage,
        codeFixJobId: String?,
        ruleId: String?,
        detectorId: String?,
        findingId: String?,
        linesOfCodeGenerated: Int?,
        charsOfCodeGenerated: Int?,
    ): SendTelemetryEventResponse

    fun sendCodeScanRemediationTelemetry(
        language: CodeWhispererProgrammingLanguage?,
        codeScanRemediationEventType: String?,
        detectorId: String?,
        findingId: String?,
        ruleId: String?,
        component: String?,
        reason: String?,
        result: String?,
        includesFix: Boolean?,
    ): SendTelemetryEventResponse

    fun sendTestGenerationEvent(
        jobId: String,
        groupName: String,
        language: CodeWhispererProgrammingLanguage?,
        ideCategory: IdeCategory?,
        numberOfUnitTestCasesGenerated: Int?,
        numberOfUnitTestCasesAccepted: Int?,
        linesOfCodeGenerated: Int?,
        linesOfCodeAccepted: Int?,
        charsOfCodeGenerated: Int?,
        charsOfCodeAccepted: Int?,
    ): SendTelemetryEventResponse

    fun listFeatureEvaluations(): ListFeatureEvaluationsResponse

    fun sendMetricDataTelemetry(eventName: String, metadata: Map<String, Any?>): SendTelemetryEventResponse

    fun sendChatAddMessageTelemetry(
        sessionId: String,
        requestId: String,
        userIntent: UserIntent?,
        hasCodeSnippet: Boolean?,
        programmingLanguage: String?,
        activeEditorTotalCharacters: Int?,
        timeToFirstChunkMilliseconds: Double?,
        timeBetweenChunks: List<Double>?,
        fullResponselatency: Double?,
        requestLength: Int?,
        responseLength: Int?,
        numberOfCodeBlocks: Int?,
        hasProjectLevelContext: Boolean?,
        customization: CodeWhispererCustomization?,
    ): SendTelemetryEventResponse

    fun sendChatInteractWithMessageTelemetry(
        sessionId: String,
        requestId: String,
        interactionType: ChatMessageInteractionType?,
        interactionTarget: String?,
        acceptedCharacterCount: Int?,
        acceptedSnippetHasReference: Boolean?,
        hasProjectLevelContext: Boolean?,
    ): SendTelemetryEventResponse

    fun sendChatInteractWithMessageTelemetry(event: ChatInteractWithMessageEvent): SendTelemetryEventResponse

    fun sendChatUserModificationTelemetry(
        sessionId: String,
        requestId: String,
        language: CodeWhispererProgrammingLanguage,
        modificationPercentage: Double,
        hasProjectLevelContext: Boolean?,
        customization: CodeWhispererCustomization?,
    ): SendTelemetryEventResponse

    fun sendInlineChatTelemetry(
        requestId: String,
        inputLength: Int?,
        numSelectedLines: Int?,
        codeIntent: Boolean?,
        userDecision: InlineChatUserDecision?,
        responseStartLatency: Double?,
        responseEndLatency: Double?,
        numSuggestionAddChars: Int?,
        numSuggestionAddLines: Int?,
        numSuggestionDelChars: Int?,
        numSuggestionDelLines: Int?,
        programmingLanguage: String?,
    ): SendTelemetryEventResponse

    companion object {
        fun getInstance(project: Project): CodeWhispererClientAdaptor = project.service()

        private fun shouldUseSigv4Client(project: Project) =
            CodeWhispererExplorerActionManager.getInstance().checkActiveCodeWhispererConnectionType(project) == CodeWhispererLoginType.Accountless

        const val INVALID_CODESCANJOBID = "Invalid_CodeScanJobID"
        const val INVALID_CODEFIXJOBID = "Invalid_CodeFixJobID"
    }
}

open class CodeWhispererClientAdaptorImpl(override val project: Project) : CodeWhispererClientAdaptor {
    private val mySigv4Client by lazy { createUnmanagedSigv4Client() }

    @Volatile
    private var myBearerClient: CodeWhispererRuntimeClient? = null

    private val KProperty0<*>.isLazyInitialized: Boolean
        get() {
            isAccessible = true
            return (getDelegate() as Lazy<*>).isInitialized()
        }

    init {
        initClientUpdateListener()
        initProfileSelectedListener()
    }

    private fun initClientUpdateListener() {
        ApplicationManager.getApplication().messageBus.connect(this).subscribe(
            ToolkitConnectionManagerListener.TOPIC,
            object : ToolkitConnectionManagerListener {
                override fun activeConnectionChanged(newConnection: ToolkitConnection?) {
                    if (newConnection is AwsBearerTokenConnection) {
                        myBearerClient = getBearerClient(newConnection.getConnectionSettings().providerId)
                    }
                }
            }
        )
    }

    private fun initProfileSelectedListener() {
        ApplicationManager.getApplication().messageBus.connect(this).subscribe(
            ProfileSelectedListener.TOPIC,
            object : ProfileSelectedListener {
                override fun profileSelected(endpoint: String, region: Region, profileArn: String) {
                    myBearerClient?.close()
                    myBearerClient = null
                    myBearerClient = AwsClientManager.getInstance().createUnmanagedClient(
                        AnonymousCredentialsProvider.create(), //TODO which provider is needed here
                        region,
                        endpoint
                    )
                }
            }
        )
    }

    private fun bearerClient(): CodeWhispererRuntimeClient {
        if (myBearerClient != null) return myBearerClient as CodeWhispererRuntimeClient
        myBearerClient = getBearerClient()
        return myBearerClient as CodeWhispererRuntimeClient
    }

    override fun generateCompletionsPaginator(firstRequest: GenerateCompletionsRequest) = sequence<GenerateCompletionsResponse> {
        var nextToken: String? = firstRequest.nextToken()
        do {
            val response = bearerClient().generateCompletions(firstRequest.copy { it.nextToken(nextToken) })
            nextToken = response.nextToken()
            yield(response)
        } while (!nextToken.isNullOrEmpty())
    }

    override fun createUploadUrl(request: CreateUploadUrlRequest): CreateUploadUrlResponse =
        bearerClient().createUploadUrl(request)

    override fun createCodeScan(request: CreateCodeScanRequest, isSigv4: Boolean): CreateCodeScanResponse =
        if (isSigv4) {
            mySigv4Client.createCodeScan(request)
        } else {
            bearerClient().startCodeAnalysis(request.transform()).transform()
        }

    override fun getCodeScan(request: GetCodeScanRequest, isSigv4: Boolean): GetCodeScanResponse =
        if (isSigv4) {
            mySigv4Client.getCodeScan(request)
        } else {
            bearerClient().getCodeAnalysis(request.transform()).transform()
        }

    override fun listCodeScanFindings(request: ListCodeScanFindingsRequest, isSigv4: Boolean): ListCodeScanFindingsResponse =
        if (isSigv4) {
            mySigv4Client.listCodeScanFindings(request)
        } else {
            bearerClient().listCodeAnalysisFindings(request.transform()).transform()
        }

    override fun startCodeFixJob(request: StartCodeFixJobRequest): StartCodeFixJobResponse = bearerClient().startCodeFixJob(request)

    override fun getCodeFixJob(request: GetCodeFixJobRequest): GetCodeFixJobResponse = bearerClient().getCodeFixJob(request)

    // DO NOT directly use this method to fetch customizations, use wrapper [CodeWhispererModelConfigurator.listCustomization()] instead
    override fun listAvailableCustomizations(): List<CodeWhispererCustomization> =
        bearerClient().listAvailableCustomizationsPaginator(ListAvailableCustomizationsRequest.builder().build())
            .stream()
            .toList()
            .flatMap { resp ->
                LOG.debug {
                    "listAvailableCustomizations: requestId: ${resp.responseMetadata().requestId()}, customizations: ${
                        resp.customizations().map { it.name() }
                    }"
                }
                resp.customizations().map {
                    CodeWhispererCustomization(
                        arn = it.arn(),
                        name = it.name(),
                        description = it.description()
                    )
                }
            }

    override fun listAvailableProfilesPaginator(bearerClient: CodeWhispererRuntimeClient, request: ListAvailableProfilesRequest) = sequence<ListAvailableProfilesResponse> {
        var nextToken: String? = request.nextToken()
        do {
            val response = bearerClient.listAvailableProfiles(request.copy { it.nextToken(nextToken) })
            nextToken = response.nextToken()
            yield(response)
        } while (!nextToken.isNullOrEmpty())
    }

    override fun createTemporaryClientForEndpoint(endpoint: String, region: Region): CodeWhispererRuntimeClient {
//        TODO "what provider is needed here
        return AwsClientManager.getInstance().createUnmanagedClient(
            AnonymousCredentialsProvider.create(), // 或者你也可以换成 SSO BearerTokenProvider
            region,
            endpoint
        )
    }

    override fun startTestGeneration(uploadId: String, targetCode: List<TargetCode>, userInput: String): StartTestGenerationResponse =
        bearerClient().startTestGeneration { builder ->
            builder.uploadId(uploadId)
            builder.targetCodeList(targetCode)
            builder.userInput(userInput)
            // TODO: client token
        }

    override fun getTestGeneration(jobId: String, jobGroupName: String): GetTestGenerationResponse =
        bearerClient().getTestGeneration { builder ->
            builder.testGenerationJobId(jobId)
            builder.testGenerationJobGroupName(jobGroupName)
        }

    override fun sendUserTriggerDecisionTelemetry(
        requestContext: RequestContext,
        responseContext: ResponseContext,
        completionType: CodewhispererCompletionType,
        suggestionState: CodewhispererSuggestionState,
        suggestionReferenceCount: Int,
        lineCount: Int,
        numberOfRecommendations: Int,
        acceptedCharCount: Int,
    ): SendTelemetryEventResponse {
        val fileContext = requestContext.fileContextInfo
        val programmingLanguage = fileContext.programmingLanguage
        var e2eLatency = requestContext.latencyContext.getCodeWhispererEndToEndLatency()

        // When we send a userTriggerDecision for neither Accept nor Reject, service side should not use this value
        // and client side will set this value to 0.0.
        if (suggestionState != CodewhispererSuggestionState.Accept &&
            suggestionState != CodewhispererSuggestionState.Reject
        ) {
            e2eLatency = 0.0
        }

        return bearerClient().sendTelemetryEvent { requestBuilder ->
            requestBuilder.telemetryEvent { telemetryEventBuilder ->
                telemetryEventBuilder.userTriggerDecisionEvent {
                    it.requestId(requestContext.latencyContext.firstRequestId)
                    it.completionType(completionType.toCodeWhispererSdkType())
                    it.programmingLanguage { builder -> builder.languageName(programmingLanguage.toCodeWhispererRuntimeLanguage().languageId) }
                    it.sessionId(responseContext.sessionId)
                    it.recommendationLatencyMilliseconds(e2eLatency)
                    it.triggerToResponseLatencyMilliseconds(requestContext.latencyContext.paginationFirstCompletionTime)
                    it.perceivedLatencyMilliseconds(requestContext.latencyContext.perceivedLatency)
                    it.suggestionState(suggestionState.toCodeWhispererSdkType())
                    it.timestamp(Instant.now())
                    it.suggestionReferenceCount(suggestionReferenceCount)
                    it.generatedLine(lineCount)
                    it.customizationArn(requestContext.customizationArn.nullize(nullizeSpaces = true))
                    it.numberOfRecommendations(numberOfRecommendations)
                    it.acceptedCharacterCount(acceptedCharCount)
                }
            }
            requestBuilder.optOutPreference(getTelemetryOptOutPreference())
            requestBuilder.userContext(codeWhispererUserContext())
        }
    }

    override fun sendUserTriggerDecisionTelemetry(
        sessionContext: SessionContextNew,
        requestContext: RequestContextNew,
        responseContext: ResponseContext,
        completionType: CodewhispererCompletionType,
        suggestionState: CodewhispererSuggestionState,
        suggestionReferenceCount: Int,
        lineCount: Int,
        numberOfRecommendations: Int,
        acceptedCharCount: Int,
    ): SendTelemetryEventResponse {
        val fileContext = requestContext.fileContextInfo
        val programmingLanguage = fileContext.programmingLanguage
        var e2eLatency = sessionContext.latencyContext.getCodeWhispererEndToEndLatency()

        // When we send a userTriggerDecision of Empty or Discard, we set the time users see the first
        // suggestion to be now.
        if (e2eLatency < 0) {
            e2eLatency = TimeUnit.NANOSECONDS.toMillis(
                System.nanoTime() - sessionContext.latencyContext.codewhispererEndToEndStart
            ).toDouble()
        }
        return bearerClient().sendTelemetryEvent { requestBuilder ->
            requestBuilder.telemetryEvent { telemetryEventBuilder ->
                telemetryEventBuilder.userTriggerDecisionEvent {
                    it.requestId(sessionContext.latencyContext.firstRequestId)
                    it.completionType(completionType.toCodeWhispererSdkType())
                    it.programmingLanguage { builder -> builder.languageName(programmingLanguage.toCodeWhispererRuntimeLanguage().languageId) }
                    it.sessionId(responseContext.sessionId)
                    it.recommendationLatencyMilliseconds(e2eLatency)
                    it.triggerToResponseLatencyMilliseconds(sessionContext.latencyContext.paginationFirstCompletionTime)
                    it.perceivedLatencyMilliseconds(sessionContext.latencyContext.perceivedLatency)
                    it.suggestionState(suggestionState.toCodeWhispererSdkType())
                    it.timestamp(Instant.now())
                    it.suggestionReferenceCount(suggestionReferenceCount)
                    it.generatedLine(lineCount)
                    it.customizationArn(requestContext.customizationArn.nullize(nullizeSpaces = true))
                    it.numberOfRecommendations(numberOfRecommendations)
                    it.acceptedCharacterCount(acceptedCharCount)
                }
            }
            requestBuilder.optOutPreference(getTelemetryOptOutPreference())
            requestBuilder.userContext(codeWhispererUserContext())
        }
    }

    override fun sendCodePercentageTelemetry(
        language: CodeWhispererProgrammingLanguage,
        customizationArn: String?,
        acceptedTokenCount: Long,
        totalTokenCount: Long,
        unmodifiedAcceptedTokenCount: Long?,
        userWrittenCodeCharacterCount: Long?,
        userWrittenCodeLineCount: Long?,
    ): SendTelemetryEventResponse = bearerClient().sendTelemetryEvent { requestBuilder ->
        requestBuilder.telemetryEvent { telemetryEventBuilder ->
            telemetryEventBuilder.codeCoverageEvent {
                it.programmingLanguage { languageBuilder -> languageBuilder.languageName(language.toCodeWhispererRuntimeLanguage().languageId) }
                it.customizationArn(customizationArn.nullize(nullizeSpaces = true))
                it.acceptedCharacterCount(acceptedTokenCount.toInt())
                it.totalCharacterCount(totalTokenCount.toInt())
                it.timestamp(Instant.now())
                it.unmodifiedAcceptedCharacterCount(unmodifiedAcceptedTokenCount?.toInt())
                it.userWrittenCodeCharacterCount(userWrittenCodeLineCount?.toInt())
                it.userWrittenCodeLineCount(userWrittenCodeLineCount?.toInt())
            }
        }
        requestBuilder.optOutPreference(getTelemetryOptOutPreference())
        requestBuilder.userContext(codeWhispererUserContext())
    }

    override fun sendUserModificationTelemetry(
        sessionId: String,
        requestId: String,
        language: CodeWhispererProgrammingLanguage,
        customizationArn: String?,
        acceptedCharacterCount: Int,
        unmodifiedAcceptedTokenCount: Int,
    ): SendTelemetryEventResponse = bearerClient().sendTelemetryEvent { requestBuilder ->
        requestBuilder.telemetryEvent { telemetryEventBuilder ->
            telemetryEventBuilder.userModificationEvent {
                it.sessionId(sessionId)
                it.requestId(requestId)
                it.programmingLanguage { languageBuilder ->
                    languageBuilder.languageName(language.toCodeWhispererRuntimeLanguage().languageId)
                }
                it.customizationArn(customizationArn.nullize(nullizeSpaces = true))
                // deprecated field, service side should not use this % anymore
                it.modificationPercentage(0.0)
                it.timestamp(Instant.now())
                it.acceptedCharacterCount(acceptedCharacterCount)
                it.unmodifiedAcceptedCharacterCount(unmodifiedAcceptedTokenCount)
            }
        }
        requestBuilder.optOutPreference(getTelemetryOptOutPreference())
        requestBuilder.userContext(codeWhispererUserContext())
    }

    override fun sendCodeScanTelemetry(
        language: CodeWhispererProgrammingLanguage,
        codeScanJobId: String?,
        scope: CodeWhispererConstants.CodeAnalysisScope,
    ): SendTelemetryEventResponse = bearerClient().sendTelemetryEvent { requestBuilder ->
        requestBuilder.telemetryEvent { telemetryEventBuilder ->
            telemetryEventBuilder.codeScanEvent {
                it.programmingLanguage { languageBuilder ->
                    languageBuilder.languageName(language.toCodeWhispererRuntimeLanguage().languageId)
                }
                it.codeScanJobId(if (codeScanJobId.isNullOrEmpty()) CodeWhispererClientAdaptor.INVALID_CODESCANJOBID else codeScanJobId)
                it.timestamp(Instant.now())
                it.codeAnalysisScope(scope.value)
            }
        }
        requestBuilder.optOutPreference(getTelemetryOptOutPreference())
        requestBuilder.userContext(codeWhispererUserContext())
    }

    override fun sendCodeScanSucceededTelemetry(
        language: CodeWhispererProgrammingLanguage,
        codeScanJobId: String?,
        scope: CodeWhispererConstants.CodeAnalysisScope,
        findings: Int,
    ): SendTelemetryEventResponse = bearerClient().sendTelemetryEvent { requestBuilder ->
        requestBuilder.telemetryEvent { telemetryEventBuilder ->
            telemetryEventBuilder.codeScanSucceededEvent {
                it.programmingLanguage { languageBuilder ->
                    languageBuilder.languageName(language.toCodeWhispererRuntimeLanguage().languageId)
                }
                it.codeScanJobId(if (codeScanJobId.isNullOrEmpty()) CodeWhispererClientAdaptor.INVALID_CODESCANJOBID else codeScanJobId)
                it.timestamp(Instant.now())
                it.codeAnalysisScope(scope.value)
                it.numberOfFindings(findings)
                it.timestamp(Instant.now())
            }
        }
        requestBuilder.optOutPreference(getTelemetryOptOutPreference())
        requestBuilder.userContext(codeWhispererUserContext())
    }

    override fun sendCodeScanFailedTelemetry(
        language: CodeWhispererProgrammingLanguage,
        codeScanJobId: String?,
        scope: CodeWhispererConstants.CodeAnalysisScope,
    ): SendTelemetryEventResponse = bearerClient().sendTelemetryEvent { requestBuilder ->
        requestBuilder.telemetryEvent { telemetryEventBuilder ->
            telemetryEventBuilder.codeScanFailedEvent {
                it.programmingLanguage { languageBuilder ->
                    languageBuilder.languageName(language.toCodeWhispererRuntimeLanguage().languageId)
                }
                it.codeScanJobId(if (codeScanJobId.isNullOrEmpty()) CodeWhispererClientAdaptor.INVALID_CODESCANJOBID else codeScanJobId)
                it.timestamp(Instant.now())
                it.codeAnalysisScope(scope.value)
            }
        }
        requestBuilder.optOutPreference(getTelemetryOptOutPreference())
        requestBuilder.userContext(codeWhispererUserContext())
    }

    override fun sendCodeFixGenerationTelemetry(
        language: CodeWhispererProgrammingLanguage,
        codeFixJobId: String?,
        ruleId: String?,
        detectorId: String?,
        findingId: String?,
        linesOfCodeGenerated: Int?,
        charsOfCodeGenerated: Int?,
    ): SendTelemetryEventResponse = bearerClient().sendTelemetryEvent { requestBuilder ->
        requestBuilder.telemetryEvent { telemetryEventBuilder ->
            telemetryEventBuilder.codeFixGenerationEvent {
                it.programmingLanguage { languageBuilder ->
                    languageBuilder.languageName(language.toCodeWhispererRuntimeLanguage().languageId)
                }
                it.jobId(if (codeFixJobId.isNullOrEmpty()) CodeWhispererClientAdaptor.INVALID_CODEFIXJOBID else codeFixJobId)
                it.ruleId(ruleId)
                it.detectorId(detectorId)
                it.findingId(findingId)
                it.linesOfCodeGenerated(linesOfCodeGenerated)
                it.charsOfCodeGenerated(charsOfCodeGenerated)
            }
        }
        requestBuilder.optOutPreference(getTelemetryOptOutPreference())
        requestBuilder.userContext(codeWhispererUserContext())
    }

    override fun sendCodeFixAcceptanceTelemetry(
        language: CodeWhispererProgrammingLanguage,
        codeFixJobId: String?,
        ruleId: String?,
        detectorId: String?,
        findingId: String?,
        linesOfCodeGenerated: Int?,
        charsOfCodeGenerated: Int?,
    ): SendTelemetryEventResponse = bearerClient().sendTelemetryEvent { requestBuilder ->
        requestBuilder.telemetryEvent { telemetryEventBuilder ->
            telemetryEventBuilder.codeFixAcceptanceEvent {
                it.programmingLanguage { languageBuilder ->
                    languageBuilder.languageName(language.toCodeWhispererRuntimeLanguage().languageId)
                }
                it.jobId(if (codeFixJobId.isNullOrEmpty()) CodeWhispererClientAdaptor.INVALID_CODEFIXJOBID else codeFixJobId)
                it.ruleId(ruleId)
                it.detectorId(detectorId)
                it.findingId(findingId)
                it.linesOfCodeAccepted(linesOfCodeGenerated)
                it.charsOfCodeAccepted(charsOfCodeGenerated)
            }
        }
        requestBuilder.optOutPreference(getTelemetryOptOutPreference())
        requestBuilder.userContext(codeWhispererUserContext())
    }

    override fun sendCodeScanRemediationTelemetry(
        language: CodeWhispererProgrammingLanguage?,
        codeScanRemediationEventType: String?,
        detectorId: String?,
        findingId: String?,
        ruleId: String?,
        component: String?,
        reason: String?,
        result: String?,
        includesFix: Boolean?,
    ): SendTelemetryEventResponse = bearerClient().sendTelemetryEvent { requestBuilder ->
        requestBuilder.telemetryEvent { telemetryEventBuilder ->
            telemetryEventBuilder.codeScanRemediationsEvent {
                it.programmingLanguage { languageBuilder ->
                    languageBuilder.languageName(language?.toCodeWhispererRuntimeLanguage()?.languageId)
                }
                it.codeScanRemediationsEventType(codeScanRemediationEventType)
                it.detectorId(detectorId)
                it.findingId(findingId)
                it.ruleId(ruleId)
                it.component(component)
                it.reason(reason)
                it.result(result)
                it.includesFix(includesFix)
                it.timestamp(Instant.now())
            }
        }
        requestBuilder.optOutPreference(getTelemetryOptOutPreference())
        requestBuilder.userContext(codeWhispererUserContext())
    }

    override fun sendTestGenerationEvent(
        jobId: String,
        groupName: String,
        language: CodeWhispererProgrammingLanguage?,
        ideCategory: IdeCategory?,
        numberOfUnitTestCasesGenerated: Int?,
        numberOfUnitTestCasesAccepted: Int?,
        linesOfCodeGenerated: Int?,
        linesOfCodeAccepted: Int?,
        charsOfCodeGenerated: Int?,
        charsOfCodeAccepted: Int?,
    ): SendTelemetryEventResponse = bearerClient().sendTelemetryEvent { requestBuilder ->
        requestBuilder.telemetryEvent { telemetryEventBuilder ->
            telemetryEventBuilder.testGenerationEvent {
                it.programmingLanguage { languageBuilder ->
                    languageBuilder.languageName(language?.toCodeWhispererRuntimeLanguage()?.languageId)
                }
                it.jobId(jobId)
                it.groupName(groupName)
                it.ideCategory(ideCategory)
                it.numberOfUnitTestCasesGenerated(numberOfUnitTestCasesGenerated)
                it.numberOfUnitTestCasesAccepted(numberOfUnitTestCasesAccepted)
                it.linesOfCodeGenerated(linesOfCodeGenerated)
                it.linesOfCodeAccepted(linesOfCodeAccepted)
                it.charsOfCodeGenerated(charsOfCodeGenerated)
                it.charsOfCodeAccepted(charsOfCodeAccepted)
                it.timestamp(Instant.now())
            }
        }
        requestBuilder.optOutPreference(getTelemetryOptOutPreference())
        requestBuilder.userContext(codeWhispererUserContext())
    }

    override fun listFeatureEvaluations(): ListFeatureEvaluationsResponse = bearerClient().listFeatureEvaluations {
        it.userContext(codeWhispererUserContext())
    }

    override fun sendMetricDataTelemetry(eventName: String, metadata: Map<String, Any?>): SendTelemetryEventResponse =
        bearerClient().sendTelemetryEvent { requestBuilder ->
            requestBuilder.telemetryEvent { telemetryEventBuilder ->
                telemetryEventBuilder.metricData { metricBuilder ->
                    metricBuilder.metricName(eventName)
                    metricBuilder.metricValue(1.0)
                    metricBuilder.timestamp(Instant.now())
                    metricBuilder.dimensions(metadata.filter { it.value != null }.map { Dimension.builder().name(it.key).value(it.value.toString()).build() })
                }
            }
            requestBuilder.optOutPreference(getTelemetryOptOutPreference())
            requestBuilder.userContext(codeWhispererUserContext())
        }

    override fun sendChatAddMessageTelemetry(
        sessionId: String,
        requestId: String,
        userIntent: UserIntent?,
        hasCodeSnippet: Boolean?,
        programmingLanguage: String?,
        activeEditorTotalCharacters: Int?,
        timeToFirstChunkMilliseconds: Double?,
        timeBetweenChunks: List<Double>?,
        fullResponselatency: Double?,
        requestLength: Int?,
        responseLength: Int?,
        numberOfCodeBlocks: Int?,
        hasProjectLevelContext: Boolean?,
        customization: CodeWhispererCustomization?,
    ): SendTelemetryEventResponse = bearerClient().sendTelemetryEvent { requestBuilder ->
        requestBuilder.telemetryEvent { telemetryEventBuilder ->
            telemetryEventBuilder.chatAddMessageEvent {
                it.conversationId(sessionId)
                it.messageId(requestId)
                it.userIntent(userIntent)
                it.hasCodeSnippet(hasCodeSnippet)
                if (programmingLanguage != null) it.programmingLanguage { langBuilder -> langBuilder.languageName(programmingLanguage) }
                it.activeEditorTotalCharacters(activeEditorTotalCharacters)
                it.timeToFirstChunkMilliseconds(timeToFirstChunkMilliseconds)
                it.timeBetweenChunks(timeBetweenChunks)
                it.fullResponselatency(fullResponselatency)
                it.requestLength(requestLength)
                it.responseLength(responseLength)
                it.numberOfCodeBlocks(numberOfCodeBlocks)
                it.hasProjectLevelContext(hasProjectLevelContext)
                it.customizationArn(customization?.arn.nullize(nullizeSpaces = true))
            }
        }
        requestBuilder.optOutPreference(getTelemetryOptOutPreference())
        requestBuilder.userContext(codeWhispererUserContext())
    }

    override fun sendChatInteractWithMessageTelemetry(
        sessionId: String,
        requestId: String,
        interactionType: ChatMessageInteractionType?,
        interactionTarget: String?,
        acceptedCharacterCount: Int?,
        acceptedSnippetHasReference: Boolean?,
        hasProjectLevelContext: Boolean?,
    ): SendTelemetryEventResponse = sendChatInteractWithMessageTelemetry(
        ChatInteractWithMessageEvent.builder().apply {
            conversationId(sessionId)
            messageId(requestId)
            interactionType(interactionType)
            interactionTarget(interactionTarget)
            acceptedCharacterCount(acceptedCharacterCount)
            acceptedSnippetHasReference(acceptedSnippetHasReference)
            hasProjectLevelContext(hasProjectLevelContext)
        }.build()
    )

    override fun sendChatInteractWithMessageTelemetry(event: ChatInteractWithMessageEvent): SendTelemetryEventResponse =
        bearerClient().sendTelemetryEvent { requestBuilder ->
            requestBuilder.telemetryEvent { telemetryEventBuilder ->
                telemetryEventBuilder.chatInteractWithMessageEvent(event)
            }
            requestBuilder.optOutPreference(getTelemetryOptOutPreference())
            requestBuilder.userContext(codeWhispererUserContext())
        }

    override fun sendChatUserModificationTelemetry(
        sessionId: String,
        requestId: String,
        language: CodeWhispererProgrammingLanguage,
        modificationPercentage: Double,
        hasProjectLevelContext: Boolean?,
        customization: CodeWhispererCustomization?,
    ): SendTelemetryEventResponse = bearerClient().sendTelemetryEvent { requestBuilder ->
        requestBuilder.telemetryEvent { telemetryEventBuilder ->
            telemetryEventBuilder.chatUserModificationEvent {
                it.conversationId(sessionId)
                it.messageId(requestId)
                it.programmingLanguage { langBuilder ->
                    langBuilder.languageName(language.toCodeWhispererRuntimeLanguage().languageId)
                }
                it.modificationPercentage(modificationPercentage)
                it.hasProjectLevelContext(hasProjectLevelContext)
                it.customizationArn(customization?.arn.nullize(nullizeSpaces = true))
            }
        }
        requestBuilder.optOutPreference(getTelemetryOptOutPreference())
        requestBuilder.userContext(codeWhispererUserContext())
    }

    override fun sendInlineChatTelemetry(
        requestId: String,
        inputLength: Int?,
        numSelectedLines: Int?,
        codeIntent: Boolean?,
        userDecision: InlineChatUserDecision?,
        responseStartLatency: Double?,
        responseEndLatency: Double?,
        numSuggestionAddChars: Int?,
        numSuggestionAddLines: Int?,
        numSuggestionDelChars: Int?,
        numSuggestionDelLines: Int?,
        programmingLanguage: String?,
    ): SendTelemetryEventResponse = bearerClient().sendTelemetryEvent { requestBuilder ->
        requestBuilder.telemetryEvent { telemetryEventBuilder ->
            telemetryEventBuilder.inlineChatEvent {
                it.requestId(requestId)
                it.inputLength(inputLength)
                it.numSelectedLines(numSelectedLines)
                it.codeIntent(codeIntent)
                it.userDecision(userDecision)
                it.responseStartLatency(responseStartLatency)
                it.responseEndLatency(responseEndLatency)
                it.numSuggestionAddChars(numSuggestionAddChars)
                it.numSuggestionAddLines(numSuggestionAddLines)
                it.numSuggestionDelChars(numSuggestionDelChars)
                it.numSuggestionDelLines(numSuggestionDelLines)
                if (programmingLanguage != null) it.programmingLanguage { langBuilder -> langBuilder.languageName(programmingLanguage) }
                it.timestamp(Instant.now())
            }
        }
        requestBuilder.optOutPreference(getTelemetryOptOutPreference())
        requestBuilder.userContext(codeWhispererUserContext())
    }

    override fun dispose() {
        if (this::mySigv4Client.isLazyInitialized) {
            mySigv4Client.close()
        }
        myBearerClient?.close()
    }

    /**
     * Every different SSO/AWS Builder ID connection requires a new client which has its corresponding bearer token provider,
     * thus we have to create them dynamically.
     * Invalidate and recycle the old client first, and create a new client with the new connection.
     * This makes sure when we invoke CW, we always use the up-to-date connection.
     * In case this fails to close the client, myBearerClient is already set to null thus next time when we invoke CW,
     * it will go through this again which should get the current up-to-date connection. This stale client would be
     * unused and stay in memory for a while until eventually closed by ToolkitClientManager.
     */
    open fun getBearerClient(oldProviderIdToRemove: String = ""): CodeWhispererRuntimeClient? {
        myBearerClient = null

        val connection = ToolkitConnectionManager.getInstance(project).activeConnectionForFeature(CodeWhispererConnection.getInstance())
        connection as? AwsBearerTokenConnection ?: run {
            LOG.warn { "$connection is not a bearer token connection" }
            return null
        }

        return AwsClientManager.getInstance().getClient<CodeWhispererRuntimeClient>(connection.getConnectionSettings())
    }

    companion object {
        private val LOG = getLogger<CodeWhispererClientAdaptorImpl>()
        private fun createUnmanagedSigv4Client(): CodeWhispererClient = AwsClientManager.getInstance().createUnmanagedClient(
            AnonymousCredentialsProvider.create(),
            CodeWhispererConstants.Config.Sigv4ClientRegion,
            CodeWhispererConstants.Config.CODEWHISPERER_ENDPOINT
        )
    }
}

class MockCodeWhispererClientAdaptor(override val project: Project) : CodeWhispererClientAdaptorImpl(project) {
    override fun getBearerClient(oldProviderIdToRemove: String): CodeWhispererRuntimeClient = project.awsClient()
    override fun dispose() {}
}

private fun CodewhispererSuggestionState.toCodeWhispererSdkType() = when {
    this == CodewhispererSuggestionState.Accept -> SuggestionState.ACCEPT
    this == CodewhispererSuggestionState.Reject -> SuggestionState.REJECT
    this == CodewhispererSuggestionState.Empty -> SuggestionState.EMPTY
    this == CodewhispererSuggestionState.Discard -> SuggestionState.DISCARD
    else -> SuggestionState.UNKNOWN_TO_SDK_VERSION
}

private fun CodewhispererCompletionType.toCodeWhispererSdkType() = when {
    this == CodewhispererCompletionType.Line -> CompletionType.LINE
    this == CodewhispererCompletionType.Block -> CompletionType.BLOCK
    else -> CompletionType.UNKNOWN_TO_SDK_VERSION
}
