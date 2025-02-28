/*
 * Copyright (c) 2019 DuckDuckGo
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

package com.duckduckgo.app.cta.ui

import android.content.Context
import android.net.Uri
import android.view.View
import android.widget.ImageView
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.annotation.VisibleForTesting
import androidx.core.content.ContextCompat
import androidx.transition.AutoTransition
import androidx.transition.TransitionManager
import com.duckduckgo.app.browser.R
import com.duckduckgo.app.browser.databinding.FragmentBrowserTabBinding
import com.duckduckgo.app.browser.omnibar.model.OmnibarPosition
import com.duckduckgo.app.cta.model.CtaId
import com.duckduckgo.app.cta.ui.DaxBubbleCta.DaxDialogIntroOption
import com.duckduckgo.app.cta.ui.DaxCta.Companion.MAX_DAYS_ALLOWED
import com.duckduckgo.app.global.install.AppInstallStore
import com.duckduckgo.app.global.install.daysInstalled
import com.duckduckgo.app.onboarding.store.OnboardingStore
import com.duckduckgo.app.pixels.AppPixelName
import com.duckduckgo.app.pixels.AppPixelName.SITE_NOT_WORKING_SHOWN
import com.duckduckgo.app.pixels.AppPixelName.SITE_NOT_WORKING_WEBSITE_BROKEN
import com.duckduckgo.app.settings.db.SettingsDataStore
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.app.statistics.pixels.Pixel.PixelValues.DAX_FIRE_DIALOG_CTA
import com.duckduckgo.app.trackerdetection.model.Entity
import com.duckduckgo.common.ui.view.TypeAnimationTextView
import com.duckduckgo.common.ui.view.button.DaxButton
import com.duckduckgo.common.ui.view.gone
import com.duckduckgo.common.ui.view.show
import com.duckduckgo.common.ui.view.text.DaxTextView
import com.duckduckgo.common.utils.baseHost
import com.duckduckgo.common.utils.extensions.html

interface ViewCta {
    fun showCta(
        view: View,
        onTypingAnimationFinished: () -> Unit,
    )
}

interface DaxCta {
    val onboardingStore: OnboardingStore
    val appInstallStore: AppInstallStore
    var ctaPixelParam: String

    companion object {
        const val MAX_DAYS_ALLOWED = 3
    }
}

interface Cta {
    val ctaId: CtaId
    val shownPixel: Pixel.PixelName?
    val okPixel: Pixel.PixelName?
    val cancelPixel: Pixel.PixelName?

    fun pixelShownParameters(): Map<String, String>
    fun pixelCancelParameters(): Map<String, String>
    fun pixelOkParameters(): Map<String, String>
}

interface OnboardingDaxCta {
    val markAsReadOnShow: Boolean
        get() = false

    fun showOnboardingCta(
        binding: FragmentBrowserTabBinding,
        onPrimaryCtaClicked: () -> Unit,
        onSecondaryCtaClicked: () -> Unit,
        onTypingAnimationFinished: () -> Unit,
        onSuggestedOptionClicked: ((DaxDialogIntroOption) -> Unit)? = null,
    )

    fun hideOnboardingCta(
        binding: FragmentBrowserTabBinding,
    )
}

sealed class OnboardingDaxDialogCta(
    override val ctaId: CtaId,
    @StringRes open val description: Int?,
    @StringRes open val buttonText: Int?,
    override val shownPixel: Pixel.PixelName?,
    override val okPixel: Pixel.PixelName?,
    override val cancelPixel: Pixel.PixelName?,
    override var ctaPixelParam: String,
    override val onboardingStore: OnboardingStore,
    override val appInstallStore: AppInstallStore,
) : Cta, DaxCta, OnboardingDaxCta {

    override fun pixelCancelParameters(): Map<String, String> = mapOf(Pixel.PixelParameter.CTA_SHOWN to ctaPixelParam)

    override fun pixelOkParameters(): Map<String, String> = mapOf(Pixel.PixelParameter.CTA_SHOWN to ctaPixelParam)

    override fun pixelShownParameters(): Map<String, String> = mapOf(Pixel.PixelParameter.CTA_SHOWN to addCtaToHistory(ctaPixelParam))

    override fun hideOnboardingCta(binding: FragmentBrowserTabBinding) {
        binding.includeOnboardingInContextDaxDialog.root.gone()
    }

    internal fun setOnboardingDialogView(
        daxTitle: String? = null,
        daxText: String,
        primaryCtaText: String?,
        secondaryCtaText: String? = null,
        binding: FragmentBrowserTabBinding,
        onPrimaryCtaClicked: () -> Unit,
        onSecondaryCtaClicked: () -> Unit,
        onTypingAnimationFinished: () -> Unit = {},
    ) {
        val daxDialog = binding.includeOnboardingInContextDaxDialog

        daxDialog.root.show()
        binding.includeOnboardingInContextDaxDialog.primaryCta.setOnClickListener(null)
        binding.includeOnboardingInContextDaxDialog.secondaryCta.setOnClickListener(null)
        daxDialog.dialogTextCta.text = ""
        daxDialog.hiddenTextCta.text = daxText.html(binding.root.context)
        daxTitle?.let {
            daxDialog.onboardingDialogTitle.show()
            daxDialog.onboardingDialogTitle.text = daxTitle
        } ?: daxDialog.onboardingDialogTitle.gone()
        primaryCtaText?.let {
            daxDialog.primaryCta.show()
            daxDialog.primaryCta.alpha = MIN_ALPHA
            daxDialog.primaryCta.text = primaryCtaText
        } ?: daxDialog.primaryCta.gone()
        secondaryCtaText?.let {
            daxDialog.secondaryCta.show()
            daxDialog.secondaryCta.alpha = MIN_ALPHA
            daxDialog.secondaryCta.text = secondaryCtaText
        } ?: daxDialog.secondaryCta.gone()
        daxDialog.onboardingDialogSuggestionsContent.gone()
        daxDialog.onboardingDialogContent.show()
        daxDialog.root.alpha = MAX_ALPHA
        TransitionManager.beginDelayedTransition(daxDialog.cardView, AutoTransition())
        val afterAnimation = {
            daxDialog.dialogTextCta.finishAnimation()
            primaryCtaText?.let { daxDialog.primaryCta.animate().alpha(MAX_ALPHA).duration = DAX_DIALOG_APPEARANCE_ANIMATION }
            secondaryCtaText?.let { daxDialog.secondaryCta.animate().alpha(MAX_ALPHA).duration = DAX_DIALOG_APPEARANCE_ANIMATION }
            binding.includeOnboardingInContextDaxDialog.primaryCta.setOnClickListener { onPrimaryCtaClicked.invoke() }
            binding.includeOnboardingInContextDaxDialog.secondaryCta.setOnClickListener { onSecondaryCtaClicked.invoke() }
            onTypingAnimationFinished.invoke()
        }
        daxDialog.dialogTextCta.startTypingAnimation(daxText, true) { afterAnimation() }
        daxDialog.onboardingDaxDialogBackground.setOnClickListener { afterAnimation() }
        daxDialog.onboardingDialogContent.setOnClickListener { afterAnimation() }
        daxDialog.onboardingDialogSuggestionsContent.setOnClickListener { afterAnimation() }
    }

    class DaxSerpCta(
        override val onboardingStore: OnboardingStore,
        override val appInstallStore: AppInstallStore,
    ) : OnboardingDaxDialogCta(
        CtaId.DAX_DIALOG_SERP,
        R.string.highlightsOnboardingSerpDaxDialogDescription,
        R.string.onboardingSerpDaxDialogButton,
        AppPixelName.ONBOARDING_DAX_CTA_SHOWN,
        AppPixelName.ONBOARDING_DAX_CTA_OK_BUTTON,
        null,
        Pixel.PixelValues.DAX_SERP_CTA,
        onboardingStore,
        appInstallStore,
    ) {
        override fun showOnboardingCta(
            binding: FragmentBrowserTabBinding,
            onPrimaryCtaClicked: () -> Unit,
            onSecondaryCtaClicked: () -> Unit,
            onTypingAnimationFinished: () -> Unit,
            onSuggestedOptionClicked: ((DaxDialogIntroOption) -> Unit)?,
        ) {
            val context = binding.root.context
            setOnboardingDialogView(
                daxText = description?.let { context.getString(it) }.orEmpty(),
                primaryCtaText = buttonText?.let { context.getString(it) },
                binding = binding,
                onPrimaryCtaClicked = onPrimaryCtaClicked,
                onSecondaryCtaClicked = onSecondaryCtaClicked,
                onTypingAnimationFinished = onTypingAnimationFinished,
            )
        }
    }

    class DaxTrackersBlockedCta(
        override val onboardingStore: OnboardingStore,
        override val appInstallStore: AppInstallStore,
        val trackers: List<Entity>,
        val settingsDataStore: SettingsDataStore,
    ) : OnboardingDaxDialogCta(
        CtaId.DAX_DIALOG_TRACKERS_FOUND,
        null,
        R.string.onboardingTrackersBlockedDaxDialogButton,
        AppPixelName.ONBOARDING_DAX_CTA_SHOWN,
        AppPixelName.ONBOARDING_DAX_CTA_OK_BUTTON,
        null,
        Pixel.PixelValues.DAX_TRACKERS_BLOCKED_CTA,
        onboardingStore,
        appInstallStore,
    ) {
        override fun showOnboardingCta(
            binding: FragmentBrowserTabBinding,
            onPrimaryCtaClicked: () -> Unit,
            onSecondaryCtaClicked: () -> Unit,
            onTypingAnimationFinished: () -> Unit,
            onSuggestedOptionClicked: ((DaxDialogIntroOption) -> Unit)?,
        ) {
            val context = binding.root.context
            setOnboardingDialogView(
                daxText = getTrackersDescription(context, trackers),
                primaryCtaText = buttonText?.let { context.getString(it) },
                binding = binding,
                onPrimaryCtaClicked = onPrimaryCtaClicked,
                onSecondaryCtaClicked = onSecondaryCtaClicked,
                onTypingAnimationFinished = onTypingAnimationFinished,
            )
        }

        @VisibleForTesting
        fun getTrackersDescription(
            context: Context,
            trackersEntities: List<Entity>,
        ): String {
            val trackers = trackersEntities
                .map { it.displayName }
                .distinct()

            val trackersFiltered = trackers.take(MAX_TRACKERS_SHOWS)
            val trackersText = trackersFiltered.joinToString(", ")
            val size = trackers.size - trackersFiltered.size
            val quantityString =
                if (size == 0) {
                    context.resources.getQuantityString(R.plurals.onboardingTrackersBlockedZeroDialogDescription, trackersFiltered.size)
                        .getStringForOmnibarPosition(settingsDataStore.omnibarPosition)
                } else {
                    context.resources.getQuantityString(R.plurals.onboardingTrackersBlockedDialogDescription, size, size)
                        .getStringForOmnibarPosition(settingsDataStore.omnibarPosition)
                }
            return "<b>$trackersText</b>$quantityString"
        }
    }

    class DaxMainNetworkCta(
        override val onboardingStore: OnboardingStore,
        override val appInstallStore: AppInstallStore,
        val network: String,
        private val siteHost: String,
    ) : OnboardingDaxDialogCta(
        CtaId.DAX_DIALOG_NETWORK,
        null,
        R.string.daxDialogGotIt,
        AppPixelName.ONBOARDING_DAX_CTA_SHOWN,
        AppPixelName.ONBOARDING_DAX_CTA_OK_BUTTON,
        null,
        Pixel.PixelValues.DAX_NETWORK_CTA_1,
        onboardingStore,
        appInstallStore,
    ) {
        override fun showOnboardingCta(
            binding: FragmentBrowserTabBinding,
            onPrimaryCtaClicked: () -> Unit,
            onSecondaryCtaClicked: () -> Unit,
            onTypingAnimationFinished: () -> Unit,
            onSuggestedOptionClicked: ((DaxDialogIntroOption) -> Unit)?,
        ) {
            val context = binding.root.context
            setOnboardingDialogView(
                daxText = getTrackersDescription(context),
                primaryCtaText = buttonText?.let { context.getString(it) },
                binding = binding,
                onPrimaryCtaClicked = onPrimaryCtaClicked,
                onSecondaryCtaClicked = onSecondaryCtaClicked,
                onTypingAnimationFinished = onTypingAnimationFinished,
            )
        }

        @VisibleForTesting
        fun getTrackersDescription(context: Context): String {
            return if (isFromSameNetworkDomain()) {
                context.resources.getString(
                    R.string.daxMainNetworkCtaText,
                    network,
                    Uri.parse(siteHost).baseHost?.removePrefix("m."),
                    network,
                )
            } else {
                context.resources.getString(
                    R.string.daxMainNetworkOwnedCtaText,
                    network,
                    Uri.parse(siteHost).baseHost?.removePrefix("m."),
                    network,
                )
            }
        }

        private fun isFromSameNetworkDomain(): Boolean = mainTrackerDomains.any { siteHost.contains(it) }
    }

    class DaxNoTrackersCta(
        override val onboardingStore: OnboardingStore,
        override val appInstallStore: AppInstallStore,
    ) : OnboardingDaxDialogCta(
        CtaId.DAX_DIALOG_OTHER,
        R.string.daxNonSerpCtaText,
        R.string.daxDialogGotIt,
        AppPixelName.ONBOARDING_DAX_CTA_SHOWN,
        AppPixelName.ONBOARDING_DAX_CTA_OK_BUTTON,
        null,
        Pixel.PixelValues.DAX_NO_TRACKERS_CTA,
        onboardingStore,
        appInstallStore,
    ) {
        override fun showOnboardingCta(
            binding: FragmentBrowserTabBinding,
            onPrimaryCtaClicked: () -> Unit,
            onSecondaryCtaClicked: () -> Unit,
            onTypingAnimationFinished: () -> Unit,
            onSuggestedOptionClicked: ((DaxDialogIntroOption) -> Unit)?,
        ) {
            val context = binding.root.context
            setOnboardingDialogView(
                daxText = description?.let { context.getString(it) }.orEmpty(),
                primaryCtaText = buttonText?.let { context.getString(it) },
                binding = binding,
                onPrimaryCtaClicked = onPrimaryCtaClicked,
                onSecondaryCtaClicked = onSecondaryCtaClicked,
                onTypingAnimationFinished = onTypingAnimationFinished,
            )
        }
    }

    class DaxFireButtonCta(
        override val onboardingStore: OnboardingStore,
        override val appInstallStore: AppInstallStore,
    ) : OnboardingDaxDialogCta(
        CtaId.DAX_FIRE_BUTTON,
        R.string.onboardingFireButtonDaxDialogDescription,
        R.string.onboardingFireButtonDaxDialogOkButton,
        AppPixelName.ONBOARDING_DAX_CTA_SHOWN,
        AppPixelName.ONBOARDING_DAX_CTA_OK_BUTTON,
        null,
        DAX_FIRE_DIALOG_CTA,
        onboardingStore,
        appInstallStore,
    ) {
        override fun showOnboardingCta(
            binding: FragmentBrowserTabBinding,
            onPrimaryCtaClicked: () -> Unit,
            onSecondaryCtaClicked: () -> Unit,
            onTypingAnimationFinished: () -> Unit,
            onSuggestedOptionClicked: ((DaxDialogIntroOption) -> Unit)?,
        ) {
            val context = binding.root.context
            setOnboardingDialogView(
                daxText = description?.let { context.getString(it) }.orEmpty(),
                primaryCtaText = context.getString(R.string.onboardingFireButtonDaxDialogOkButton),
                secondaryCtaText = context.getString(R.string.onboardingFireButtonDaxDialogCancelButton),
                binding = binding,
                onPrimaryCtaClicked = onPrimaryCtaClicked,
                onSecondaryCtaClicked = onSecondaryCtaClicked,
                onTypingAnimationFinished = onTypingAnimationFinished,
            )
        }
    }

    class DaxSiteSuggestionsCta(
        override val onboardingStore: OnboardingStore,
        override val appInstallStore: AppInstallStore,
    ) : OnboardingDaxDialogCta(
        CtaId.DAX_INTRO_VISIT_SITE,
        R.string.onboardingSitesDaxDialogDescription,
        null,
        AppPixelName.ONBOARDING_DAX_CTA_SHOWN,
        AppPixelName.ONBOARDING_DAX_CTA_OK_BUTTON,
        null,
        Pixel.PixelValues.DAX_INITIAL_VISIT_SITE_CTA,
        onboardingStore,
        appInstallStore,
    ) {
        override fun showOnboardingCta(
            binding: FragmentBrowserTabBinding,
            onPrimaryCtaClicked: () -> Unit,
            onSecondaryCtaClicked: () -> Unit,
            onTypingAnimationFinished: () -> Unit,
            onSuggestedOptionClicked: ((DaxDialogIntroOption) -> Unit)?,
        ) {
            val context = binding.root.context
            val daxDialog = binding.includeOnboardingInContextDaxDialog
            val daxText = description?.let { context.getString(it) }.orEmpty()

            binding.includeOnboardingInContextDaxDialog.onboardingDialogContent.gone()
            binding.includeOnboardingInContextDaxDialog.onboardingDialogSuggestionsContent.show()
            daxDialog.suggestionsDialogTextCta.text = ""
            daxDialog.suggestionsHiddenTextCta.text = daxText.html(context)
            TransitionManager.beginDelayedTransition(binding.includeOnboardingInContextDaxDialog.cardView, AutoTransition())
            val afterAnimation = {
                onTypingAnimationFinished()
                val optionsViews = listOf<DaxButton>(
                    daxDialog.daxDialogOption1,
                    daxDialog.daxDialogOption2,
                    daxDialog.daxDialogOption3,
                    daxDialog.daxDialogOption4,
                )

                optionsViews.forEachIndexed { index, buttonView ->
                    val options = onboardingStore.getSitesOptions()
                    options[index].setOptionView(buttonView)
                    buttonView.animate().alpha(MAX_ALPHA).duration = DAX_DIALOG_APPEARANCE_ANIMATION
                }

                val options = onboardingStore.getSitesOptions()
                daxDialog.daxDialogOption1.setOnClickListener { onSuggestedOptionClicked?.invoke(options[0]) }
                daxDialog.daxDialogOption2.setOnClickListener { onSuggestedOptionClicked?.invoke(options[1]) }
                daxDialog.daxDialogOption3.setOnClickListener { onSuggestedOptionClicked?.invoke(options[2]) }
                daxDialog.daxDialogOption4.setOnClickListener { onSuggestedOptionClicked?.invoke(options[3]) }
            }
            daxDialog.suggestionsDialogTextCta.startTypingAnimation(daxText, true) { afterAnimation() }
            daxDialog.onboardingDialogContent.setOnClickListener {
                daxDialog.dialogTextCta.finishAnimation()
                afterAnimation()
            }
        }
    }

    class DaxEndCta(
        override val onboardingStore: OnboardingStore,
        override val appInstallStore: AppInstallStore,
    ) : OnboardingDaxDialogCta(
        CtaId.DAX_END,
        R.string.highlightsOnboardingEndDaxDialogDescription,
        R.string.highlightsOnboardingEndDaxDialogButton,
        AppPixelName.ONBOARDING_DAX_CTA_SHOWN,
        AppPixelName.ONBOARDING_DAX_CTA_OK_BUTTON,
        null,
        Pixel.PixelValues.DAX_ONBOARDING_END_CTA,
        onboardingStore,
        appInstallStore,
    ) {
        override val markAsReadOnShow: Boolean = true

        override fun showOnboardingCta(
            binding: FragmentBrowserTabBinding,
            onPrimaryCtaClicked: () -> Unit,
            onSecondaryCtaClicked: () -> Unit,
            onTypingAnimationFinished: () -> Unit,
            onSuggestedOptionClicked: ((DaxDialogIntroOption) -> Unit)?,
        ) {
            val context = binding.root.context
            setOnboardingDialogView(
                daxText = description?.let { context.getString(it) }.orEmpty(),
                primaryCtaText = buttonText?.let { context.getString(it) },
                binding = binding,
                onPrimaryCtaClicked = onPrimaryCtaClicked,
                onSecondaryCtaClicked = onSecondaryCtaClicked,
                onTypingAnimationFinished = onTypingAnimationFinished,
            )
        }
    }

    companion object {

        const val SERP = "duckduckgo"
        val mainTrackerNetworks = listOf("Facebook", "Google")

        private const val MAX_TRACKERS_SHOWS = 2
        private val mainTrackerDomains = listOf("facebook", "google")
        private const val DAX_DIALOG_APPEARANCE_ANIMATION = 400L
        private const val MAX_ALPHA = 1.0f
        private const val MIN_ALPHA = 0.0f
    }
}

sealed class DaxBubbleCta(
    override val ctaId: CtaId,
    @StringRes open val title: Int,
    @StringRes open val description: Int,
    @DrawableRes open val placeholder: Int? = null,
    open val options: List<DaxDialogIntroOption>? = null,
    @StringRes open val primaryCta: Int? = null,
    @StringRes open val secondaryCta: Int? = null,
    override val shownPixel: Pixel.PixelName?,
    override val okPixel: Pixel.PixelName?,
    override val cancelPixel: Pixel.PixelName? = null,
    override var ctaPixelParam: String,
    override val onboardingStore: OnboardingStore,
    override val appInstallStore: AppInstallStore,
) : Cta, ViewCta, DaxCta {

    private var ctaView: View? = null

    override fun showCta(
        view: View,
        onTypingAnimationFinished: () -> Unit,
    ) {
        ctaView = view
        val daxTitle = view.context.getString(title)
        val daxText = view.context.getString(description)
        val optionsViews: List<DaxButton> = listOf(
            view.findViewById(R.id.daxDialogOption1),
            view.findViewById(R.id.daxDialogOption2),
            view.findViewById(R.id.daxDialogOption3),
            view.findViewById(R.id.daxDialogOption4),
        )

        primaryCta?.let {
            view.findViewById<DaxButton>(R.id.primaryCta).show()
            view.findViewById<DaxButton>(R.id.primaryCta).alpha = 0f
            view.findViewById<DaxButton>(R.id.primaryCta).text = view.context.getString(it)
        } ?: view.findViewById<DaxButton>(R.id.primaryCta).gone()

        secondaryCta?.let {
            view.findViewById<DaxButton>(R.id.secondaryCta).show()
            view.findViewById<DaxButton>(R.id.secondaryCta).alpha = 0f
            view.findViewById<DaxButton>(R.id.secondaryCta).text = view.context.getString(it)
        } ?: view.findViewById<DaxButton>(R.id.secondaryCta).gone()

        placeholder?.let {
            view.findViewById<ImageView>(R.id.placeholder).show()
            view.findViewById<ImageView>(R.id.placeholder).alpha = 0f
            view.findViewById<ImageView>(R.id.placeholder).setImageResource(it)
        } ?: view.findViewById<ImageView>(R.id.placeholder).gone()

        if (options.isNullOrEmpty()) {
            view.findViewById<DaxButton>(R.id.daxDialogOption1).gone()
            view.findViewById<DaxButton>(R.id.daxDialogOption2).gone()
            view.findViewById<DaxButton>(R.id.daxDialogOption3).gone()
            view.findViewById<DaxButton>(R.id.daxDialogOption4).gone()
        } else {
            options?.let {
                optionsViews.forEachIndexed { index, buttonView ->
                    if (it.size > index) {
                        it[index].setOptionView(buttonView)
                    } else {
                        buttonView.gone()
                    }
                }
            }
        }

        TransitionManager.beginDelayedTransition(view.findViewById(R.id.cardView), AutoTransition())
        view.show()
        view.findViewById<TypeAnimationTextView>(R.id.dialogTextCta).text = ""
        view.findViewById<DaxTextView>(R.id.hiddenTextCta).text = daxText.html(view.context)
        view.findViewById<DaxTextView>(R.id.daxBubbleDialogTitle).apply {
            alpha = 0f
            text = daxTitle.html(view.context)
        }
        val afterAnimation = {
            view.findViewById<TypeAnimationTextView>(R.id.dialogTextCta).finishAnimation()
            view.findViewById<ImageView>(R.id.placeholder).animate().alpha(1f).setDuration(500)
            view.findViewById<DaxButton>(R.id.primaryCta).animate().alpha(1f).setDuration(500)
            view.findViewById<DaxButton>(R.id.secondaryCta).animate().alpha(1f).setDuration(500)
            options?.let {
                optionsViews.forEachIndexed { index, buttonView ->
                    if (it.size > index) {
                        buttonView.animate().alpha(1f).setDuration(500)
                    }
                }
            }
            onTypingAnimationFinished()
        }

        view.animate().alpha(1f).setDuration(500).setStartDelay(600).withEndAction {
            view.findViewById<DaxTextView>(R.id.daxBubbleDialogTitle).animate().alpha(1f).setDuration(500)
                .withEndAction {
                    view.findViewById<TypeAnimationTextView>(R.id.dialogTextCta).startTypingAnimation(daxText, true) {
                        afterAnimation()
                    }
                }
        }
        view.findViewById<View>(R.id.cardContainer).setOnClickListener { afterAnimation() }
    }

    fun setOnPrimaryCtaClicked(onButtonClicked: () -> Unit) {
        ctaView?.findViewById<DaxButton>(R.id.primaryCta)?.setOnClickListener {
            onButtonClicked.invoke()
        }
    }

    fun setOnSecondaryCtaClicked(onButtonClicked: () -> Unit) {
        ctaView?.findViewById<DaxButton>(R.id.secondaryCta)?.setOnClickListener {
            onButtonClicked.invoke()
        }
    }

    fun setOnOptionClicked(onOptionClicked: (DaxDialogIntroOption) -> Unit) {
        options?.forEachIndexed { index, option ->
            val optionView = when (index) {
                0 -> R.id.daxDialogOption1
                1 -> R.id.daxDialogOption2
                2 -> R.id.daxDialogOption3
                else -> R.id.daxDialogOption4
            }
            option.let { ctaView?.findViewById<DaxButton>(optionView)?.setOnClickListener { onOptionClicked.invoke(option) } }
        }
    }

    override fun pixelCancelParameters(): Map<String, String> = mapOf(Pixel.PixelParameter.CTA_SHOWN to ctaPixelParam)

    override fun pixelOkParameters(): Map<String, String> = mapOf(Pixel.PixelParameter.CTA_SHOWN to ctaPixelParam)

    override fun pixelShownParameters(): Map<String, String> = mapOf(Pixel.PixelParameter.CTA_SHOWN to addCtaToHistory(ctaPixelParam))

    data class DaxIntroSearchOptionsCta(
        override val onboardingStore: OnboardingStore,
        override val appInstallStore: AppInstallStore,
    ) : DaxBubbleCta(
        ctaId = CtaId.DAX_INTRO,
        title = R.string.onboardingSearchDaxDialogTitle,
        description = R.string.highlightsOnboardingSearchDaxDialogDescription,
        options = onboardingStore.getExperimentSearchOptions(),
        shownPixel = AppPixelName.ONBOARDING_DAX_CTA_SHOWN,
        okPixel = AppPixelName.ONBOARDING_DAX_CTA_OK_BUTTON,
        ctaPixelParam = Pixel.PixelValues.DAX_INITIAL_CTA,
        onboardingStore = onboardingStore,
        appInstallStore = appInstallStore,
    )

    data class DaxIntroVisitSiteOptionsCta(
        override val onboardingStore: OnboardingStore,
        override val appInstallStore: AppInstallStore,
    ) : DaxBubbleCta(
        ctaId = CtaId.DAX_INTRO_VISIT_SITE,
        title = R.string.onboardingSitesDaxDialogTitle,
        description = R.string.onboardingSitesDaxDialogDescription,
        options = onboardingStore.getSitesOptions(),
        shownPixel = AppPixelName.ONBOARDING_DAX_CTA_SHOWN,
        okPixel = AppPixelName.ONBOARDING_DAX_CTA_OK_BUTTON,
        ctaPixelParam = Pixel.PixelValues.DAX_INITIAL_VISIT_SITE_CTA,
        onboardingStore = onboardingStore,
        appInstallStore = appInstallStore,
    )

    data class DaxEndCta(
        override val onboardingStore: OnboardingStore,
        override val appInstallStore: AppInstallStore,
    ) : DaxBubbleCta(
        ctaId = CtaId.DAX_END,
        title = R.string.onboardingEndDaxDialogTitle,
        description = R.string.highlightsOnboardingEndDaxDialogDescription,
        primaryCta = R.string.highlightsOnboardingEndDaxDialogButton,
        shownPixel = AppPixelName.ONBOARDING_DAX_CTA_SHOWN,
        okPixel = AppPixelName.ONBOARDING_DAX_CTA_OK_BUTTON,
        ctaPixelParam = Pixel.PixelValues.DAX_END_CTA,
        onboardingStore = onboardingStore,
        appInstallStore = appInstallStore,
    )

    data class DaxPrivacyProCta(
        override val onboardingStore: OnboardingStore,
        override val appInstallStore: AppInstallStore,
        val titleRes: Int,
        val descriptionRes: Int,
    ) : DaxBubbleCta(
        ctaId = CtaId.DAX_INTRO_PRIVACY_PRO,
        title = titleRes,
        description = descriptionRes,
        placeholder = com.duckduckgo.mobile.android.R.drawable.ic_privacy_pro_128,
        primaryCta = R.string.onboardingPrivacyProDaxDialogOkButton,
        secondaryCta = R.string.onboardingPrivacyProDaxDialogCancelButton,
        shownPixel = AppPixelName.ONBOARDING_DAX_CTA_SHOWN,
        okPixel = AppPixelName.ONBOARDING_DAX_CTA_OK_BUTTON,
        cancelPixel = AppPixelName.ONBOARDING_DAX_CTA_CANCEL_BUTTON,
        ctaPixelParam = Pixel.PixelValues.DAX_PRIVACY_PRO,
        onboardingStore = onboardingStore,
        appInstallStore = appInstallStore,
    )

    data class DaxDialogIntroOption(
        val optionText: String,
        @DrawableRes val iconRes: Int,
        val link: String,
    ) {
        fun setOptionView(buttonView: DaxButton) {
            buttonView.apply {
                text = optionText
                icon = ContextCompat.getDrawable(this.context, iconRes)
            }
        }
    }
}

sealed class HomePanelCta(
    override val ctaId: CtaId,
    @DrawableRes open val image: Int,
    @StringRes open val title: Int,
    @StringRes open val description: Int,
    @StringRes open val okButton: Int,
    @StringRes open val dismissButton: Int,
    override val shownPixel: Pixel.PixelName?,
    override val okPixel: Pixel.PixelName?,
    override val cancelPixel: Pixel.PixelName?,
) : Cta, ViewCta {

    override fun showCta(
        view: View,
        onTypingAnimationFinished: () -> Unit,
    ) {
        // no-op. We are now using a Bottom Sheet to display this
        // but we want to keep the same classes for pixels, etc
    }

    override fun pixelCancelParameters(): Map<String, String> = emptyMap()

    override fun pixelOkParameters(): Map<String, String> = emptyMap()

    override fun pixelShownParameters(): Map<String, String> = emptyMap()

    object AddWidgetAuto : HomePanelCta(
        CtaId.ADD_WIDGET,
        R.drawable.add_widget_cta_icon,
        R.string.addWidgetCtaTitle,
        R.string.addWidgetCtaDescription,
        R.string.addWidgetCtaAutoLaunchButton,
        R.string.addWidgetCtaDismissButton,
        AppPixelName.WIDGET_CTA_SHOWN,
        AppPixelName.WIDGET_CTA_LAUNCHED,
        AppPixelName.WIDGET_CTA_DISMISSED,
    )

    object AddWidgetInstructions : HomePanelCta(
        CtaId.ADD_WIDGET,
        R.drawable.add_widget_cta_icon,
        R.string.addWidgetCtaTitle,
        R.string.addWidgetCtaDescription,
        R.string.addWidgetCtaInstructionsLaunchButton,
        R.string.addWidgetCtaDismissButton,
        AppPixelName.WIDGET_LEGACY_CTA_SHOWN,
        AppPixelName.WIDGET_LEGACY_CTA_LAUNCHED,
        AppPixelName.WIDGET_LEGACY_CTA_DISMISSED,
    )
}

class BrokenSitePromptDialogCta : Cta {

    override val ctaId: CtaId = CtaId.BROKEN_SITE_PROMPT
    override val shownPixel: Pixel.PixelName = SITE_NOT_WORKING_SHOWN
    override val okPixel: Pixel.PixelName = SITE_NOT_WORKING_WEBSITE_BROKEN
    override val cancelPixel: Pixel.PixelName? = null

    override fun pixelCancelParameters(): Map<String, String> = mapOf()

    override fun pixelOkParameters(): Map<String, String> = mapOf()

    override fun pixelShownParameters(): Map<String, String> = mapOf()

    fun hideOnboardingCta(binding: FragmentBrowserTabBinding) {
        val view = binding.includeBrokenSitePromptDialog.root
        view.gone()
    }

    fun showBrokenSitePromptCta(
        binding: FragmentBrowserTabBinding,
        onReportBrokenSiteClicked: () -> Unit,
        onDismissCtaClicked: () -> Unit,
        onCtaShown: () -> Unit,
    ) {
        val daxDialog = binding.includeBrokenSitePromptDialog
        daxDialog.root.show()
        binding.includeBrokenSitePromptDialog.reportButton.setOnClickListener { onReportBrokenSiteClicked.invoke() }
        binding.includeBrokenSitePromptDialog.dismissButton.setOnClickListener { onDismissCtaClicked.invoke() }
        onCtaShown()
    }
}

fun DaxCta.addCtaToHistory(newCta: String): String {
    val param = onboardingStore.onboardingDialogJourney?.split("-").orEmpty().toMutableList()
    val daysInstalled = minOf(appInstallStore.daysInstalled().toInt(), MAX_DAYS_ALLOWED)
    param.add("$newCta:$daysInstalled")
    val finalParam = param.joinToString("-")
    onboardingStore.onboardingDialogJourney = finalParam
    return finalParam
}

fun DaxCta.canSendShownPixel(): Boolean {
    val param = onboardingStore.onboardingDialogJourney?.split("-").orEmpty().toMutableList()
    return !(param.isNotEmpty() && param.any { it.split(":").firstOrNull().orEmpty() == ctaPixelParam })
}

fun String.getStringForOmnibarPosition(position: OmnibarPosition): String {
    return when (position) {
        OmnibarPosition.TOP -> this
        OmnibarPosition.BOTTOM -> replace("☝", "\uD83D\uDC47")
    }
}
