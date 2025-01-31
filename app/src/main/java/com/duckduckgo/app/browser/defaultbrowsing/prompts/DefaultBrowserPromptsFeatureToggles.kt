/*
 * Copyright (c) 2024 DuckDuckGo
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.duckduckgo.app.browser.defaultbrowsing.prompts

import com.duckduckgo.anvil.annotations.ContributesRemoteFeature
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.feature.toggles.api.Toggle
import com.duckduckgo.feature.toggles.api.Toggle.State.CohortName

@ContributesRemoteFeature(
    scope = AppScope::class,
    featureName = "defaultBrowserPrompts",
)
interface DefaultBrowserPromptsFeatureToggles {

    @Toggle.DefaultValue(false)
    fun self(): Toggle

    @Toggle.DefaultValue(false)
    fun defaultBrowserAdditionalPrompts202501(): Toggle

    enum class AdditionalPromptsCohortName(override val cohortName: String) : CohortName {
        VARIANT_1("variant_1"),
        VARIANT_2("variant_2"),
        VARIANT_3("variant_3"),
    }
}
