// Copyright 2025 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.codewhisperer.actions

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import software.aws.toolkits.jetbrains.services.codewhisperer.speechtotext.AudioRecorder
import software.aws.toolkits.jetbrains.services.codewhisperer.speechtotext.SpeechToTextProcessor


class StartSpeechToTextAction : AnAction("start speech to text") {
    override fun actionPerformed(e: AnActionEvent) {
        try {
            val audioData: ByteArray = AudioRecorder.captureAudio()
            SpeechToTextProcessor.processAudio(audioData)
        } catch (ex: Exception) {
            ex.printStackTrace()
        }
    }
}
