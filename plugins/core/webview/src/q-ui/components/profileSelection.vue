<!-- Copyright 2025 Amazon.com, Inc. or its affiliates. All Rights Reserved. -->
<!-- SPDX-License-Identifier: Apache-2.0 -->

<template>
    <div @keydown.enter="handleContinueClick">
        <div class="font-amazon" v-if="profiles.length > 0">
            <!-- Title & Subtitle -->
            <div class="profile-header">
                <h2 class="title bottom-small-gap">Select profile</h2>
                <p class="profile-subtitle">
                    Profiles have different configs defined by your administrators.
                    Select the profile that best meets your current working need and switch at any time.
                </p>
            </div>
            <!-- Profile List -->
            <div class="profile-list">
                <div
                    v-for="(profile, index) in profiles"
                    :key="index"
                    class="profile-item bottom-small-gap"
                    :class="{ selected: selectedProfile?.name === profile.name }"
                    @click="toggleItemSelection(profile)"
                    tabindex="0"
                >
                    <div class="text">
                        <div class="profile-name">{{ profile.profileName }} - <span class="profile-region">{{ profile.region }}</span></div>
                        <div class="profile-id">{{ profile.accountId }}</div>
                    </div>
                </div>
            </div>
            <!-- Continue Button -->
            <button
                class="login-flow-button continue-button font-amazon"
                :disabled="selectedProfile === null"
                v-on:click="handleContinueClick()"
                tabindex="-1"
            >
                Continue
            </button>
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
        toggleItemSelection(profile: Profile) {
            this.selectedProfile = profile;
        },
        emitUiClickMetric(profileId: string) {
            this.$emit('emitUiClickTelemetry', profileId);
        },
        handleContinueClick() {
            if (this.selectedProfile) {
                this.$store.commit('setSelectedProfile', this.selectedProfile);
                window.ideApi.postMessage({
                    command: 'profileConfirmed',
                });
            }
        }
    }
})
</script>
<style scoped lang="scss">
.profile-header {
    margin-bottom: 16px;
}

.profile-subtitle {
    font-size: 12px;
    color: #bbbbbb;
    margin-bottom: 12px;
}

.profile-list {
    display: flex;
    flex-direction: column;
}

.profile-item {
    padding: 15px;
    display: flex;
    align-items: flex-start;
    border: 1px solid #cccccc;
    border-radius: 4px;
    margin-bottom: 10px;
    cursor: pointer;
    transition: background 0.2s ease-in-out;
}

.selected {
    border: 1px solid #29a7ff;
}

.text {
    display: flex;
    flex-direction: column;
    font-size: 15px;
}
.profile-name {
    font-weight: bold;
    margin-bottom: 2px;
    color: white;
}
.profile-region {
    font-style: italic;
    color: #bbbbbb;
}
.profile-description {
    font-size: 12px;
    color: #bbbbbb;
}
body.jb-dark {
    .profile-item {
        border: 1px solid white;
    }
    .selected {
        border: 1px solid #29a7ff;
    }
}

body.jb-light {
    .profile-item {
        border: 1px solid black;
    }
    .selected {
        border: 1px solid #3574f0;
    }
}
</style>
