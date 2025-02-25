<!-- Copyright 2025 Amazon.com, Inc. or its affiliates. All Rights Reserved. -->
<!-- SPDX-License-Identifier: Apache-2.0 -->

<script setup lang="ts">

</script>

<template>
    <div class="profile-picker">
        <div class="profile-picker__header">
            <h2 class="profile-picker__title">Select profile</h2>
        </div>

        <p class="profile-picker__description">
            You have multiple profiles assigned to you in this account, please select one to continue.
        </p>

        <!-- 动态渲染 Profiles -->
        <div class="profile-picker__options">
            <label
                v-for="profile in profiles"
                :key="profile.arn"
                class="profile-picker__option"
            >
                <span>{{ profile.name }} ({{ profile.region }})</span>
                <input
                    type="radio"
                    name="profile"
                    :value="profile.arn"
                    v-model="selectedProfile"
                />
            </label>
        </div>

        <!-- 确认按钮 -->
        <button class="profile-picker__confirm" @click="confirmProfile">
            Confirm
        </button>
    </div>
</template>

<script setup lang="ts">
import { ref, defineProps } from 'vue'

// Profile 数据结构
interface Profile {
    arn: string
    name: string
    region: string
}

// 从父组件 / WebView Shell 处传入的 Profiles
const props = defineProps<{
    profiles: Profile[]
}>()

// 当前选中的 Profile.arn
const selectedProfile = ref<string>("")

function confirmProfile() {
    if (!selectedProfile.value) {
        alert("Please select a profile first.")
        return
    }

    // 将所选 Profile 通知 JetBrains 插件 (Kotlin 端)
    window.ideApi.postMessage({
        command: 'setProfile',
        profileArn: selectedProfile.value
    })
}
</script>

<style scoped>
.profile-picker {
    width: 280px;
    border: 1px solid #ccc;
    border-radius: 6px;
    padding: 16px;
    font-family: system-ui, sans-serif;
    background-color: #fff;
    box-sizing: border-box;
}

.profile-picker__header {
    display: flex;
    align-items: center;
    margin-bottom: 12px;
}

.profile-picker__star {
    color: #f7c544;
    font-size: 24px;
    margin-right: 8px;
}

.profile-picker__title {
    margin: 0;
    font-size: 16px;
    font-weight: bold;
}

.profile-picker__description {
    margin: 0 0 16px 0;
    font-size: 14px;
    color: #333;
    line-height: 1.4;
}

.profile-picker__options {
    display: flex;
    flex-direction: column;
    gap: 8px;
}

.profile-picker__option {
    display: flex;
    align-items: center;
    justify-content: space-between;
    padding: 10px;
    border: 1px solid #ddd;
    border-radius: 4px;
    cursor: pointer;
}

.profile-picker__option:hover {
    background-color: #f8f8f8;
}

.profile-picker__option input[type="radio"] {
    cursor: pointer;
}

.profile-picker__confirm {
    margin-top: 12px;
    padding: 8px 16px;
    background-color: #0073e6;
    color: #fff;
    border: none;
    border-radius: 4px;
    cursor: pointer;
}
.profile-picker__confirm:hover {
    background-color: #005bb5;
}
</style>


<style scoped lang="scss">

</style>
