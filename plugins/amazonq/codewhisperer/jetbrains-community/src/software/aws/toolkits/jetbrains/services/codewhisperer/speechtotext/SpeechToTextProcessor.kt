// Copyright 2025 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.codewhisperer.speechtotext

import com.google.cloud.speech.v1.*;
import com.google.protobuf.ByteString;
import software.aws.toolkits.jetbrains.services.codewhisperer.service.CodeWhispererService


object SpeechToTextProcessor {
    @Throws(Exception::class)
    fun processAudio(audioData: ByteArray?) {
        System.setProperty("GOOGLE_APPLICATION_CREDENTIALS", "/Users/yuxqiang/real-codewhisperer-a466656d2f33.json");
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
                CodeWhispererService.getInstance().in
            }
        }
    }
}
