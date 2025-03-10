// Copyright 2025 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.codewhisperer.profiles

import com.intellij.util.messages.Topic
import software.amazon.awssdk.regions.Region

interface ProfileSelectedListener {
    companion object {
        val TOPIC = Topic.create("CodeWhispererProfileSelected", ProfileSelectedListener::class.java)
    }

    fun profileSelected(endpoint: String, region: Region, profileArn: String)
}
