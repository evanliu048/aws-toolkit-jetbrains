// Copyright 2025 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.uitests

import org.junit.jupiter.api.Assertions.assertEquals
import java.io.File
import java.nio.file.Files
import java.nio.file.Paths
import java.nio.file.StandardOpenOption

private const val TEST_RESOURCES_PATH = "src/test/tstData"
fun executePuppeteerScript(scriptContent: String): String {
    val scriptFile = File("$TEST_RESOURCES_PATH/temp-script.js")
    scriptFile.parentFile.mkdirs()
    scriptFile.writeText(scriptContent)

    val process = ProcessBuilder()
        .command("node", scriptFile.absolutePath)
        .redirectErrorStream(true)
        .start()

    val output = process.inputStream.bufferedReader().use { it.readText() }
    val exitCode = process.waitFor()

    scriptFile.delete()

    assertEquals(0, exitCode, "Script execution failed with output: $output")
    return output
}

fun useExistingConnectionForTest() {
    val testStartUrl = System.getenv("TEST_START_URL")
    val testRegion = System.getenv("TEST_REGION")
    val configContent =
        """
       <application>
        <component name="authManager">
            <option name="ssoProfiles">
                <list>
                    <ManagedSsoProfile>
                        <option name="scopes">
                            <list>
                                <option value="codewhisperer:conversations" />
                                <option value="codewhisperer:transformations" />
                                <option value="codewhisperer:taskassist" />
                                <option value="codewhisperer:completions" />
                                <option value="codewhisperer:analysis" />
                            </list>
                        </option>
                        <option name="ssoRegion" value="$testRegion" />
                        <option name="startUrl" value="$testStartUrl" />
                    </ManagedSsoProfile>
                </list>
            </option>
        </component>
        <component name="connectionPinningManager">
            <option name="pinnedConnections">
                <map>
                    <entry key="aws.codewhisperer" value="sso;$testRegion;$testStartUrl" />
                    <entry key="aws.q" value="sso;$testRegion;$testStartUrl" />
                </map>
            </option>
        </component>
      <component name="meetQPage">
        <option name="shouldDisplayPage" value="false" />
      </component>
    </application>
        """.trimIndent()
    writeToAwsXml(configContent)
}

fun clearAwsXmlFile() {
    val configContent =
        """
       <application>
        
    </application>
        """.trimIndent()
    writeToAwsXml(configContent)
}

fun setupTestEnvironment() {
    // Ensure Puppeteer is installed
    val npmInstall = ProcessBuilder()
        .command("npm", "install", "puppeteer")
        .inheritIO()
        .start()
        .waitFor()

    assertEquals(0, npmInstall, "Failed to install Puppeteer")
}

fun writeToAwsXml(configContent: String) {
    val path = Paths.get("tstData", "configAmazonQTests", "options", "aws.xml")

    Files.createDirectories(path.parent)
    Files.write(
        path,
        configContent.toByteArray(),
        StandardOpenOption.CREATE,
        StandardOpenOption.TRUNCATE_EXISTING
    )
}
