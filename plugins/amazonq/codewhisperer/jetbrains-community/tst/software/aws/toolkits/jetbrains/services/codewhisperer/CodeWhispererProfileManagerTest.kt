// Copyright 2025 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.codewhisperer

import com.intellij.util.xmlb.XmlSerializer
import org.assertj.core.api.Assertions.assertThat
import org.jdom.Element
import org.jdom.output.XMLOutputter
import org.junit.Before
import org.junit.Test
import software.aws.toolkits.jetbrains.services.codewhisperer.customization.CodeWhispererCustomization
import software.aws.toolkits.jetbrains.services.codewhisperer.customization.DefaultCodeWhispererModelConfigurator
import software.aws.toolkits.jetbrains.services.codewhisperer.profiles.CodeWhispererProfileState
import software.aws.toolkits.jetbrains.services.codewhisperer.profiles.DefaultCodeWhispererProfileManager
import software.aws.toolkits.jetbrains.services.codewhisperer.profiles.ProfileUiItem
import software.aws.toolkits.jetbrains.utils.xmlElement

class CodeWhispererProfileManagerTest {
    private lateinit var sut: DefaultCodeWhispererProfileManager

    @Before
    fun setup() {
        sut = DefaultCodeWhispererProfileManager()
    }


    @Test
    fun `serialize profile data`() {
        val element = software.aws.toolkits.jetbrains.utils.xmlElement(
            """
            <component name="codeWhispererProfileStates">
  </component>
            """.trimIndent()
        )

        val state = CodeWhispererProfileState().apply {
            this.connectionIdToActiveProfile.putAll(
                mapOf(
                    "connection_1" to ProfileUiItem(
                        profileName = "Profile1",
                        accountId = "123456789012",
                        region = "us-east-1",
                        arn = "arn:aws:codewhisperer:us-east-1:123456789012:profile/Profile1",
                        endpoint = "https://codewhisperer.us-east-1.amazonaws.com"
                    )
                )
            )
        }

        XmlSerializer.serializeInto(state, element)

        val actual = XMLOutputter().outputString(element)
        val expected = """
            <component name="codeWhispererProfileStates">
                <option name="connectionIdToActiveProfile">
                    <map>
                        <entry key="connection_1">
                            <value>
                                <ProfileUiItem>
                                    <option name="profileName" value="Profile1"/>
                                    <option name="accountId" value="123456789012"/>
                                    <option name="region" value="us-east-1"/>
                                    <option name="arn" value="arn:aws:codewhisperer:us-east-1:123456789012:profile/Profile1"/>
                                    <option name="endpoint" value="https://codewhisperer.us-east-1.amazonaws.com"/>
                                </ProfileUiItem>
                            </value>
                        </entry>
                    </map>
                </option>
            </component>
        """.trimIndent()

        assertThat(actual).isEqualToIgnoringWhitespace(expected)
    }

    @Test
    fun `deserialize empty data`() {
        val element = xmlElement(
            """
            <component name="codeWhispererProfileStates">
            </component>
            """
        )

        val actual = XmlSerializer.deserialize(element, CodeWhispererProfileState::class.java)

        assertThat(actual.connectionIdToActiveProfile).isEmpty()
    }

    @Test
    fun `deserialize profile data`() {
        val element = xmlElement(
            """
            <component name="codeWhispererProfileStates">
                <option name="connectionIdToActiveProfile">
                    <map>
                        <entry key="connection_1">
                            <value>
                                <ProfileUiItem>
                                    <option name="profileName" value="Profile1"/>
                                    <option name="accountId" value="123456789012"/>
                                    <option name="region" value="us-east-1"/>
                                    <option name="arn" value="arn:aws:codewhisperer:us-east-1:123456789012:profile/Profile1"/>
                                    <option name="endpoint" value="https://codewhisperer.us-east-1.amazonaws.com"/>
                                </ProfileUiItem>
                            </value>
                        </entry>
                    </map>
                </option>
            </component>
            """
        )

        val actual = XmlSerializer.deserialize(element, CodeWhispererProfileState::class.java)

        assertThat(actual.connectionIdToActiveProfile).hasSize(1)
        assertThat(actual.connectionIdToActiveProfile["connection_1"]).isEqualTo(
            ProfileUiItem(
                profileName = "Profile1",
                accountId = "123456789012",
                region = "us-east-1",
                arn = "arn:aws:codewhisperer:us-east-1:123456789012:profile/Profile1",
                endpoint = "https://codewhisperer.us-east-1.amazonaws.com"
            )
        )
    }

    @Test
    fun `deserialize multiple profiles`() {
        val element = xmlElement(
            """
            <component name="codeWhispererProfileStates">
                <option name="connectionIdToActiveProfile">
                    <map>
                        <entry key="connection_1">
                            <value>
                                <ProfileUiItem>
                                    <option name="profileName" value="Profile1"/>
                                    <option name="accountId" value="123456789012"/>
                                    <option name="region" value="us-east-1"/>
                                    <option name="arn" value="arn:aws:codewhisperer:us-east-1:123456789012:profile/Profile1"/>
                                    <option name="endpoint" value="https://codewhisperer.us-east-1.amazonaws.com"/>
                                </ProfileUiItem>
                            </value>
                        </entry>
                        <entry key="connection_2">
                            <value>
                                <ProfileUiItem>
                                    <option name="profileName" value="Profile2"/>
                                    <option name="accountId" value="987654321098"/>
                                    <option name="region" value="us-west-2"/>
                                    <option name="arn" value="arn:aws:codewhisperer:us-west-2:987654321098:profile/Profile2"/>
                                    <option name="endpoint" value="https://codewhisperer.us-west-2.amazonaws.com"/>
                                </ProfileUiItem>
                            </value>
                        </entry>
                    </map>
                </option>
            </component>
            """
        )

        val actual = XmlSerializer.deserialize(element, CodeWhispererProfileState::class.java)

        assertThat(actual.connectionIdToActiveProfile).hasSize(2)
        assertThat(actual.connectionIdToActiveProfile["connection_1"]?.profileName).isEqualTo("Profile1")
        assertThat(actual.connectionIdToActiveProfile["connection_2"]?.profileName).isEqualTo("Profile2")
    }
}
