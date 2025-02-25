// Copyright 2025 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

// src/mynah-ui/ui/tabs/profileSelectionTabData.ts

// eslint-disable-next-line header/header
import {ChatItemType, MynahIcons, MynahUITabStoreTab} from '@aws/mynah-ui-chat'

/** Profile structure returned by API*/
export interface Profile {
    id: string
    name: string
    region?: string
    endpoint?: string
}

/**
 * generate a TabData to display “Select profile" card in UI，
 * each profile represents a button and will jump to Welcome page after selection.
 */
export function profileSelectionTabData(profiles: Profile[]): MynahUITabStoreTab {
    return {
        isSelected: true,
        store: {
            tabTitle: 'Welcome to Q',
            tabBackground: true,
            compactMode: true,
            promptInputVisible: false,
            promptInputDisabledState: true,
            chatItems: [
                {
                    type: ChatItemType.ANSWER,
                    messageId: 'profile-selection-card',
                    /**
                     * 核心：使用 informationCard 实现白色卡片风格
                     *  - title: 卡片标题
                     *  - status.icon: 卡片左上角的星星图标
                     *  - description: 标题下的灰色文字
                     *  - content.buttons: 显示多个按钮，每个代表一个profile
                     */
                    informationCard: {
                        title: 'Select profile',
                        description:
                            'You have multiple profiles assigned to you in this account please select one to continue.',
                        // 卡片主体内容：一组按钮
                        content: {
                            buttons: profiles.map(p => ({
                                // 每个profile对应一个按钮ID
                                id: `select-profile-${p.id}`,
                                // 按钮上的文字
                                text: p.name,
                                // 显示“radio circle”图标
                                icon: MynahIcons.OK_CIRCLED,
                                // 把图标放在右侧
                                position: 'outside',
                                // 用clear保证按钮背后不填充颜色
                                status: 'clear',
                                fillState: 'hover',
                                disabled: false
                            })),
                        },
                    },
                },
            ],
        },
    }
}
