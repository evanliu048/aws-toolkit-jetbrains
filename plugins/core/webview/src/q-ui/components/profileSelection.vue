<!-- Copyright 2025 Amazon.com, Inc. or its affiliates. All Rights Reserved. -->
<!-- SPDX-License-Identifier: Apache-2.0 -->

<template>
    <div class="profile-selection-wrapper">
        <div class="profile-card">
            <div class="profile-header">
                <h2 class="profile-title">Select profile</h2>
                <p class="profile-subtitle">
                    You have multiple profiles assigned to you in this account please select one to continue.
                </p>
                <hr />
            </div>

            <div class="profile-list">
                <label
                    class="profile-item"
                    v-for="(profile, index) in profiles"
                    :key="index"
                    @click="selectProfile(profile)"
                >
                    <input
                        type="radio"
                        name="selectedProfile"
                        :value="profile"
                        v-model="selectedProfile"
                    />
                    <span class="profile-name">{{ profile.name }}</span>
                </label>
            </div>
        </div>
    </div>
</template>

<script lang="ts">
import { defineComponent } from 'vue'
import { Profile, State } from '../../model'

export default defineComponent({
    name: 'ProfileSelection',
    props: {
        app: { type: String, default: '' }
    },
    data() {
        return {
            selectedProfile: null as (Profile | null)
        }
    },
    computed: {
        profiles(): Profile[] {
            console.log("Vuex raw profiles:", this.$store.state.profiles);
            return Array.isArray(this.$store.state.profiles) ? this.$store.state.profiles : this.$store.state.profiles || [];

        }
    },
    methods: {
        selectProfile() {
            // TODO: update endporint
            this.$store.commit('setSelectedProfile', this.selectedProfile)
            window.ideApi.postMessage({
                command: 'switchConnection',
            })
        }
    }
})
</script>

<style scoped lang="scss">
.profile-selection-wrapper {
    display: flex;
    justify-content: center;
    align-items: flex-start;
    padding: 16px;
}

.profile-card {
    border: 1px solid #ddd;
    border-radius: 4px;
    background-color: var(--vscode-editor-background);
    width: 320px;
    padding: 16px;
    position: relative;
}

.profile-header {
    margin-bottom: 12px;
    color: white;
}
.profile-title {
    margin: 0;
    font-size: 16px;
    font-weight: bold;
    color: white;
}
.profile-subtitle {
    margin: 4px 0 8px;
    font-size: 12px;
    color: #cccccc;
}
hr {
    border: none;
    border-top: 1px solid #ccc;
    margin: 0;
    margin-bottom: 12px;
}

.profile-list {
    display: flex;
    flex-direction: column;
    margin-bottom: 12px;
}
.profile-item {
    display: flex;
    align-items: center;
    margin-bottom: 8px;
    cursor: pointer;
    color: white;
}
.profile-item input[type="radio"] {
    margin-right: 4px;
    transform: scale(0.6);
}
.profile-name {
    font-size: 13px;
    color: white;
}

/* Theme specific styles */
body.jb-dark {
    .profile-card {
        background-color: #252526;
        color: white;
    }
}

body.jb-light {
    .profile-card {
        background-color: #ffffff;
        color: black;
    }
    .profile-title, .profile-subtitle, .profile-name {
        color: black;
    }
}
</style>
