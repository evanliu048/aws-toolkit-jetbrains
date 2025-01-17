// Copyright 2025 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.codewhisperer.speechtotext

import com.google.cloud.speech.v1.*;
import com.google.protobuf.ByteString;
import com.intellij.ide.impl.ProjectUtil.getActiveProject
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindowManager
import software.aws.toolkits.jetbrains.services.amazonq.toolwindow.AMAZON_Q_WINDOW_ID
import software.aws.toolkits.jetbrains.services.codewhisperer.service.CodeWhispererService


object SpeechToTextProcessor {
    @Throws(Exception::class)
    fun processAudio(audioData: ByteArray?) {
        System.setProperty("GOOGLE_APPLICATION_CREDENTIALS", "/Users/evannliu/Documents/real-codewhisperer-c32330b73659.json");
        SpeechClient.create().use { speechClient ->
            val audioBytes: ByteString = ByteString.copyFrom(audioData)
            val config: RecognitionConfig = RecognitionConfig.newBuilder()
                .setEncoding(RecognitionConfig.AudioEncoding.LINEAR16)
                .setSampleRateHertz(16000)
                .setLanguageCode("en-US")
                .build()

            val audio: RecognitionAudio = RecognitionAudio.newBuilder()
                .setContent(audioBytes)
                .build()

            val response: RecognizeResponse = speechClient.recognize(config, audio)
            val results = response.getResultsList()
            for (result in results) {
                val prompt = result.getAlternatives(0).getTranscript()
                println("Transcription: " + prompt)
                CodeWhispererService.getInstance().prompt = prompt
                handleTranscription(prompt)
            }
        }
    }
    private fun handleTranscription(prompt: String) {
        val lowerPrompt = prompt.lowercase()

        when {
            "hi q" in lowerPrompt -> {
                openQChatPanel()
            }
            "chat" in lowerPrompt -> {
                // callChatApi()
            }
            "inline" in lowerPrompt -> {
                // callInlineCompletionApi()
            }
            "generate test" in lowerPrompt -> {
                // callUnitTestGeneration()
            }
            "generate docs" in lowerPrompt -> {
                //callGenerateDocs()
            }
            else -> {
                println("No matching case for transcription: $prompt")
            }
        }
    }

    private fun openQChatPanel() {
        println("try to Amazon Q Chat Panel ...")
        val project: Project? = getActiveProject()
        if (project != null) {
            val toolWindow = ToolWindowManager.getInstance(project).getToolWindow(AMAZON_Q_WINDOW_ID)
            toolWindow?.activate(null , true )
            println("Amazon Q Chat Panel is opened ...")

        }
    }

}
