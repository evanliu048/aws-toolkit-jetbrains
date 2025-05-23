// Copyright 2024 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.amazonq.startup

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.runInEdt
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.waitForSmartMode
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.openapi.wm.ToolWindowManager
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.time.withTimeout
import software.aws.toolkits.core.utils.getLogger
import software.aws.toolkits.core.utils.warn
import software.aws.toolkits.jetbrains.core.credentials.AwsBearerTokenConnection
import software.aws.toolkits.jetbrains.core.credentials.ToolkitConnectionManager
import software.aws.toolkits.jetbrains.core.credentials.pinning.QConnection
import software.aws.toolkits.jetbrains.core.gettingstarted.emitUserState
import software.aws.toolkits.jetbrains.services.amazonq.CodeWhispererFeatureConfigService
import software.aws.toolkits.jetbrains.services.amazonq.lsp.AmazonQLspService
import software.aws.toolkits.jetbrains.services.amazonq.profile.QRegionProfileManager
import software.aws.toolkits.jetbrains.services.amazonq.project.ProjectContextController
import software.aws.toolkits.jetbrains.services.amazonq.toolwindow.AmazonQToolWindow
import software.aws.toolkits.jetbrains.services.amazonq.toolwindow.AmazonQToolWindowFactory
import software.aws.toolkits.jetbrains.services.codewhisperer.explorer.CodeWhispererExplorerActionManager
import software.aws.toolkits.jetbrains.services.cwc.inline.InlineChatController
import software.aws.toolkits.jetbrains.settings.CodeWhispererSettings
import java.lang.management.ManagementFactory
import java.time.Duration
import java.util.concurrent.atomic.AtomicBoolean

class AmazonQStartupActivity : ProjectActivity {
    private val runOnce = AtomicBoolean(false)

    override suspend fun execute(project: Project) {
        if (ApplicationManager.getApplication().isUnitTestMode) return

        ToolkitConnectionManager.getInstance(project).activeConnectionForFeature(QConnection.getInstance())?.let {
            if (it is AwsBearerTokenConnection && CodeWhispererFeatureConfigService.getInstance().getChatWSContext()) {
                CodeWhispererSettings.getInstance().toggleProjectContextEnabled(value = true, passive = true)
            }
        }

        // initialize html contents in BGT so users don't have to wait when they open the tool window
        AmazonQToolWindow.getInstance(project)
        InlineChatController.getInstance(project)

        if (CodeWhispererExplorerActionManager.getInstance().getIsFirstRestartAfterQInstall()) {
            runInEdt {
                val toolWindow = ToolWindowManager.getInstance(project).getToolWindow(AmazonQToolWindowFactory.WINDOW_ID) ?: return@runInEdt
                toolWindow.show()
                CodeWhispererExplorerActionManager.getInstance().setIsFirstRestartAfterQInstall(false)
            }
        }

        QRegionProfileManager.getInstance().validateProfile(project)

        AmazonQLspService.getInstance(project)
        startLsp(project)
        if (runOnce.get()) return
        emitUserState(project)
        runOnce.set(true)
    }

    private suspend fun startLsp(project: Project) {
        // Automatically start the project context LSP after some delay when average CPU load is below 30%.
        // The CPU load requirement is to avoid competing with native JetBrains indexing and other CPU expensive OS processes
        // In the future we will decouple LSP start and indexing start to let LSP perform other tasks.
        val startLspIndexingDuration = Duration.ofMinutes(30)
        project.waitForSmartMode()
        delay(30_000) // Wait for 30 seconds for systemLoadAverage to be more accurate
        try {
            withTimeout(startLspIndexingDuration) {
                while (true) {
                    val cpuUsage = ManagementFactory.getOperatingSystemMXBean().systemLoadAverage
                    if (cpuUsage > 0 && cpuUsage < 30) {
                        ProjectContextController.getInstance(project = project)
                        break
                    } else {
                        delay(60_000) // Wait for 60 seconds
                    }
                }
            }
        } catch (e: TimeoutCancellationException) {
            LOG.warn { "Failed to start LSP server due to time out" }
        } catch (e: Exception) {
            LOG.warn { "Failed to start LSP server" }
        }
    }

    companion object {
        private val LOG = getLogger<AmazonQStartupActivity>()
    }
}
