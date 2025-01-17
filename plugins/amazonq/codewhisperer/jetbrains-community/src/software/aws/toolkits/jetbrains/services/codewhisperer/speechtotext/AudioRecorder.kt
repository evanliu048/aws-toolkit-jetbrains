// Copyright 2025 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.codewhisperer.speechtotext

import java.io.ByteArrayOutputStream
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.DataLine
import javax.sound.sampled.TargetDataLine


object AudioRecorder {
    @Throws(Exception::class)
    fun captureAudio(): ByteArray {
        val format = AudioFormat(16000f, 16, 1, true, false)
        val info = DataLine.Info(TargetDataLine::class.java, format)
        val microphone = AudioSystem.getLine(info) as TargetDataLine
        microphone.open(format)
        microphone.start()

        val out = ByteArrayOutputStream()
        val buffer = ByteArray(4096)
        println("Recording... Speak into the microphone!")
        val start = System.currentTimeMillis()
        while (true) {
            val bytesRead = microphone.read(buffer, 0, buffer.size)
            if (bytesRead > 0) {
                out.write(buffer, 0, bytesRead)
            }
//            println("current text: ${String(buffer)}")
            if (System.currentTimeMillis() - start > 2000) {
                break
            }
        }
        return out.toByteArray()
    }
}
