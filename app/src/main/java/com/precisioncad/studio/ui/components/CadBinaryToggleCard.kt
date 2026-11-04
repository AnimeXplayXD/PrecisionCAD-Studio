package com.precisioncad.studio.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.updateTransition
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.precisioncad.studio.ui.theme.CadBorderHairline
import com.precisioncad.studio.ui.theme.CadBorderStrong
import com.precisioncad.studio.ui.theme.CadCyanPrimary
import com.precisioncad.studio.ui.theme.CadEmeraldCrossing
import com.precisioncad.studio.ui.theme.CadPanelElevated
import com.precisioncad.studio.ui.theme.CadSurfaceCard
import com.precisioncad.studio.ui.theme.CadTextMuted
import com.precisioncad.studio.ui.theme.CadTextPrimary
import com.precisioncad.studio.ui.theme.CadTextSecondary
import kotlinx.coroutines.delay

// Restrained PrecisionCAD Green & Grey tokens for binary toggle-card stacks (Section 9.3 & 12.1)
val CadToggleEnabledSurface = Color(0xFF0E1F18)
val CadToggleEnabledBorder = Color(0xFF4C9A73)
val CadToggleEnabledText = Color(0xFFD5F5E3)
val CadToggleDisabledSurface = Color(0xFF131820)
val CadToggleDisabledBorder = Color(0xFF333D4B)
val CadToggleDisabledText = Color(0xFF9BA6B2)

// Destructive red tokens for CANCEL / CLEAR ALL cards (Section 9.3)
val CadCardDestructiveSurface = Color(0xFF281115)
val CadCardDestructiveBorder = Color(0xFFDC2626)
val CadCardDestructiveText = Color(0xFFFECACA)

/**
 * Shape-aware reveal direction for a card deck (Section 9.2):
 * - [SIDE]: Square or compact cards reveal a small rear-card edge from the side.
 * - [ABOVE]: Wide rectangular or pill-shaped cards (and Touchpad/Joystick selector) reveal from above.
 */
enum class CardPeekDirection {
    SIDE,
    ABOVE,
    BELOW
}

/**
 * V19 §5.3 — Formal State Machine per Card Deck.
 */
enum class CardDeckPhase {
    INACTIVE,
    IDLE_WAIT,
    EXCHANGE_ANIMATION,
    POST_EXCHANGE_PEEK,
    POST_EXCHANGE_RETRACT,
    IDLE_PEEK,
    IDLE_RETRACT
}

/**
 * V19 §5.4 — Shape-Based Peek Direction Rule.
 */
enum class CardShapeClass {
    RECTANGULAR,
    SQUARE
}

fun classifyCardShape(widthPx: Float, heightPx: Float): CardShapeClass {
    val ratio = widthPx / heightPx.coerceAtLeast(1f)
    return if (ratio in 0.90f..1.10f) CardShapeClass.SQUARE else CardShapeClass.RECTANGULAR
}

fun peekDirectionForShape(shapeClass: CardShapeClass): CardPeekDirection = when (shapeClass) {
    CardShapeClass.RECTANGULAR -> CardPeekDirection.ABOVE
    CardShapeClass.SQUARE -> CardPeekDirection.SIDE
}

/**
 * V19 §5.2–5.7 — Section-local card deck inactivity & transient reveal state machine.
 *
 * Each card deck owns its own independent instance. Events outside the deck never reset its timer.
 * - After a user-initiated card exchange, the new rear card remains slightly visible for
 *   [POST_EXCHANGE_REVEAL_MS] (4,000 ms) and then smoothly retracts completely behind the foreground card.
 * - While eligible (parent screen/dialog open, visible, enabled, window resumed, not animating),
 *   an isolated [IDLE_PEEK_INTERVAL_MS] (60,000 ms) timer runs. When it expires, the rear card
 *   peeks subtly for [IDLE_PEEK_HOLD_MS] (4,000 ms) and then retracts completely without changing
 *   any state or triggering any action.
 */
@Stable
class CadDeckIdleTimerState(
    val deckId: String,
    val peekDirection: CardPeekDirection = CardPeekDirection.ABOVE,
    val idleIntervalMs: Long = IDLE_PEEK_INTERVAL_MS,
    val idlePeekHoldMs: Long = IDLE_PEEK_HOLD_MS,
    val postExchangeRevealMs: Long = POST_EXCHANGE_REVEAL_MS
) {
    constructor(
        scope: kotlinx.coroutines.CoroutineScope,
        deckId: String = "command_deck",
        peekDirection: CardPeekDirection = CardPeekDirection.SIDE
    ) : this(deckId = deckId, peekDirection = peekDirection)

    companion object {
        const val IDLE_PEEK_INTERVAL_MS = 60_000L
        const val IDLE_PEEK_HOLD_MS = 4_000L
        const val POST_EXCHANGE_REVEAL_MS = 4_000L

        /**
         * V19 §4.2 — Command Send/Cancel deck visibility rule:
         * CommandDeckVisible = KeyboardVisible OR CommandTextNotEmpty
         */
        fun isCommandDeckVisible(keyboardVisible: Boolean, commandText: String): Boolean {
            return keyboardVisible || commandText.isNotEmpty()
        }

        fun isCommandDeckVisible(isImeVisible: Boolean, commandLineInput: String, dummy: Unit = Unit): Boolean {
            return isCommandDeckVisible(keyboardVisible = isImeVisible, commandText = commandLineInput)
        }

        /**
         * Normalizes any binary status text ("ON", "OFF", "POLAR", "PSNAP", "REL SEG")
         * to the authoritative labels "ENABLE" and "DISABLE" (V19 §7.1).
         */
        fun normalizeBinaryStateLabel(checked: Boolean, candidate: String? = null): String {
            if (candidate == "ENABLE" || candidate == "DISABLE") return candidate
            return if (checked) "ENABLE" else "DISABLE"
        }

        fun classifyCardShape(widthPx: Float, heightPx: Float): CardShapeClass =
            com.precisioncad.studio.ui.components.classifyCardShape(widthPx, heightPx)

        fun peekDirectionForShape(shapeClass: CardShapeClass): CardPeekDirection =
            com.precisioncad.studio.ui.components.peekDirectionForShape(shapeClass)

        fun peekDirectionForShape(widthPx: Float, heightPx: Float): CardPeekDirection =
            com.precisioncad.studio.ui.components.peekDirectionForShape(
                com.precisioncad.studio.ui.components.classifyCardShape(widthPx, heightPx)
            )
    }

    var isParentVisible: Boolean by mutableStateOf(true)
        private set
    var isDeckEnabled: Boolean by mutableStateOf(true)
        private set
    var isWindowResumed: Boolean by mutableStateOf(true)
        private set
    var isTransitionRunning: Boolean by mutableStateOf(false)
        private set
    var isReducedMotionEnabled: Boolean by mutableStateOf(false)
        private set

    var isPostExchangeRevealing: Boolean by mutableStateOf(false)
        private set
    var isIdlePeeking: Boolean by mutableStateOf(false)
        private set

    var directInteractionGeneration: Int by mutableIntStateOf(0)
        private set
    var activeLifecycleObserverCount: Int by mutableIntStateOf(0)
        private set
    var activeTimerDriverCount: Int by mutableIntStateOf(0)
        private set
    var elapsedIdleMs: Long = 0L
        private set
    var elapsedPostExchangeMs: Long = 0L
        private set

    internal fun attachLifecycleObserver(): Boolean {
        if (activeLifecycleObserverCount >= 1) return false
        activeLifecycleObserverCount = 1
        return true
    }

    internal fun detachLifecycleObserver() {
        activeLifecycleObserverCount = (activeLifecycleObserverCount - 1).coerceAtLeast(0)
    }

    internal fun attachTimerDriver(): Boolean {
        if (activeTimerDriverCount >= 1) return false
        activeTimerDriverCount = 1
        return true
    }

    internal fun detachTimerDriver() {
        activeTimerDriverCount = (activeTimerDriverCount - 1).coerceAtLeast(0)
    }

    val isEligibleForTimer: Boolean
        get() = isParentVisible && isDeckEnabled && isWindowResumed && !isTransitionRunning

    val isRearCardPeeking: Boolean
        get() = (isPostExchangeRevealing || (isIdlePeeking && !isReducedMotionEnabled)) &&
            isParentVisible && isWindowResumed

    val currentPhase: CardDeckPhase
        get() = when {
            !isParentVisible || !isDeckEnabled || !isWindowResumed -> CardDeckPhase.INACTIVE
            isTransitionRunning -> CardDeckPhase.EXCHANGE_ANIMATION
            isPostExchangeRevealing -> CardDeckPhase.POST_EXCHANGE_PEEK
            isIdlePeeking && !isReducedMotionEnabled -> CardDeckPhase.IDLE_PEEK
            else -> CardDeckPhase.IDLE_WAIT
        }

    val phase: CardDeckPhase get() = currentPhase
    val isRearCardVisible: Boolean get() = isRearCardPeeking
    val isRearCardRevealed: Boolean get() = isRearCardPeeking
    val isPostExchangeRevealActive: Boolean get() = isPostExchangeRevealing
    val interactionGeneration: Int get() = directInteractionGeneration

    val isRearCardConcealed: Boolean
        get() = !isRearCardPeeking

    /**
     * V19 §5.5 — Multi-Card Deck Rule (>2 Cards):
     * During post-exchange reveal (4s) or idle peek (4s after 60s), ONLY the nearest rear card
     * (depth == 1) peeks from the shape-appropriate edge. Deeper rear cards (depth >= 2) remain
     * occluded behind the stack.
     */
    fun isRearCardVisibleForDepth(depth: Int): Boolean {
        return depth == 1 && isRearCardPeeking
    }

    /**
     * V19 §5.5 — Sequential deepest-first conceal delay when retracting a multi-card stack:
     * Deeper rear cards conceal first (delay 0ms), while the nearest rear card (depth == 1)
     * conceals after deeper rear cards when more than 2 cards exist.
     */
    fun sequentialConcealDelayMsForDepth(depth: Int, totalCards: Int): Int {
        if (totalCards <= 2 || depth <= 0) return 0
        return ((totalCards - 1 - depth).coerceAtLeast(0)) * 140
    }

    fun updateEligibility(
        parentVisible: Boolean = this.isParentVisible,
        deckEnabled: Boolean = this.isDeckEnabled,
        windowResumed: Boolean = this.isWindowResumed,
        transitionRunning: Boolean = this.isTransitionRunning,
        reducedMotion: Boolean = this.isReducedMotionEnabled
    ) {
        val wasEligible = isEligibleForTimer
        isParentVisible = parentVisible
        isDeckEnabled = deckEnabled
        isWindowResumed = windowResumed
        isTransitionRunning = transitionRunning
        isReducedMotionEnabled = reducedMotion

        if (!isEligibleForTimer) {
            elapsedIdleMs = 0L
            isIdlePeeking = false
            if (!parentVisible || !windowResumed) {
                isPostExchangeRevealing = false
                elapsedPostExchangeMs = 0L
            }
        } else if (!wasEligible) {
            // Start a fresh 60-second interval when becoming eligible again
            elapsedIdleMs = 0L
            isIdlePeeking = false
        }
    }

    /**
     * Records a direct user interaction on THIS deck's surface only.
     * Unrelated events (typing in command input, scrolling parent, touching canvas) must never call this.
     */
    fun onDirectDeckInteraction(didExchangeCards: Boolean = true) {
        if (!isParentVisible || !isDeckEnabled) return
        directInteractionGeneration++
        elapsedIdleMs = 0L
        isIdlePeeking = false
        if (didExchangeCards && postExchangeRevealMs > 0L) {
            isPostExchangeRevealing = true
            elapsedPostExchangeMs = 0L
        } else {
            isPostExchangeRevealing = false
            elapsedPostExchangeMs = 0L
        }
    }

    @Suppress("UNUSED_PARAMETER")
    fun onCardExchanged(isCommandPromptOrDeck: Boolean = false) =
        onDirectDeckInteraction(didExchangeCards = postExchangeRevealMs > 0L)

    @Suppress("UNUSED_PARAMETER")
    fun onCardExchange(isCommandPromptOrDeck: Boolean = false) =
        onDirectDeckInteraction(didExchangeCards = postExchangeRevealMs > 0L)

    @Suppress("UNUSED_PARAMETER")
    fun onOrderExchanged(isCommandPromptOrDeck: Boolean = false) =
        onDirectDeckInteraction(didExchangeCards = postExchangeRevealMs > 0L)

    fun onDirectInteraction() = onDirectDeckInteraction(didExchangeCards = false)
    fun onUserInteraction() = onDirectDeckInteraction(didExchangeCards = false)
    fun cancelAllVisualPeeks() = updateEligibility(parentVisible = false)
    fun stop() = updateEligibility(parentVisible = false)
    fun startOrReset() = updateEligibility(parentVisible = true, deckEnabled = true)

    /**
     * Deterministic virtual clock step for unit tests (V19 §5) and coroutine loop driver.
     */
    fun advanceTimeBy(deltaMs: Long) {
        if (deltaMs <= 0L) return
        if (!isParentVisible || !isWindowResumed) {
            elapsedIdleMs = 0L
            isIdlePeeking = false
            isPostExchangeRevealing = false
            return
        }

        if (isPostExchangeRevealing) {
            elapsedPostExchangeMs += deltaMs
            if (elapsedPostExchangeMs >= postExchangeRevealMs) {
                isPostExchangeRevealing = false
                elapsedPostExchangeMs = 0L
                // Restart fresh 60s idle wait after post-exchange reveal completes
                elapsedIdleMs = 0L
                isIdlePeeking = false
                return
            }
        }

        if (!isEligibleForTimer || isPostExchangeRevealing) {
            elapsedIdleMs = 0L
            isIdlePeeking = false
            return
        }

        elapsedIdleMs += deltaMs
        val cycleLength = idleIntervalMs + idlePeekHoldMs
        val posInCycle = elapsedIdleMs % cycleLength
        isIdlePeeking = !isReducedMotionEnabled && posInCycle >= idleIntervalMs
    }
}

@Composable
fun rememberCadDeckIdleTimerState(
    deckId: String,
    peekDirection: CardPeekDirection = CardPeekDirection.ABOVE,
    isVisible: Boolean = true,
    isEnabled: Boolean = true,
    postExchangeRevealMs: Long = CadDeckIdleTimerState.POST_EXCHANGE_REVEAL_MS
): CadDeckIdleTimerState {
    val context = androidx.compose.ui.platform.LocalContext.current
    val isReducedMotion = remember(context) {
        runCatching {
            android.provider.Settings.Global.getFloat(
                context.contentResolver,
                android.provider.Settings.Global.ANIMATOR_DURATION_SCALE,
                1f
            ) == 0f
        }.getOrDefault(false)
    }
    val state = remember(deckId, postExchangeRevealMs) {
        CadDeckIdleTimerState(
            deckId = deckId,
            peekDirection = peekDirection,
            postExchangeRevealMs = postExchangeRevealMs
        )
    }
    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner, state) {
        val attached = state.attachLifecycleObserver()
        val observer = LifecycleEventObserver { _, event ->
            val resumed = event == Lifecycle.Event.ON_RESUME ||
                lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
            state.updateEligibility(windowResumed = resumed)
        }
        if (attached) {
            lifecycleOwner.lifecycle.addObserver(observer)
        }
        onDispose {
            if (attached) {
                lifecycleOwner.lifecycle.removeObserver(observer)
                state.detachLifecycleObserver()
            }
            state.updateEligibility(parentVisible = false)
        }
    }

    LaunchedEffect(isVisible, isEnabled, isReducedMotion) {
        state.updateEligibility(
            parentVisible = isVisible,
            deckEnabled = isEnabled,
            reducedMotion = isReducedMotion
        )
    }

    // Sole timer driver for this CadDeckIdleTimerState instance (V18.3 §3.2 & V19 §5.2):
    // Drives post-exchange 4.0s reveal retraction and 60s section-local 4.0s idle peek.
    LaunchedEffect(
        state.isEligibleForTimer,
        state.isPostExchangeRevealing,
        state.directInteractionGeneration
    ) {
        if (!state.isParentVisible || !state.isWindowResumed) return@LaunchedEffect
        val attachedDriver = state.attachTimerDriver()
        if (!attachedDriver) return@LaunchedEffect
        try {
            if (state.isPostExchangeRevealing && state.postExchangeRevealMs > 0L) {
                delay(state.postExchangeRevealMs)
                state.advanceTimeBy(state.postExchangeRevealMs)
            }

            while (state.isEligibleForTimer) {
                delay(state.idleIntervalMs)
                if (!state.isEligibleForTimer) break
                state.advanceTimeBy(state.idleIntervalMs)
                delay(state.idlePeekHoldMs)
                state.advanceTimeBy(state.idlePeekHoldMs)
            }
        } finally {
            state.detachTimerDriver()
        }
    }

    return state
}

@Composable
fun rememberCadDeckIdleTimerState(
    deckId: String,
    isVisibleAndInteractive: Boolean,
    peekDirection: CardPeekDirection = CardPeekDirection.ABOVE
): CadDeckIdleTimerState = rememberCadDeckIdleTimerState(
    deckId = deckId,
    peekDirection = peekDirection,
    isVisible = isVisibleAndInteractive,
    isEnabled = isVisibleAndInteractive
)

@Composable
fun rememberCadDeckIdleTimerState(
    deckId: String,
    scope: kotlinx.coroutines.CoroutineScope,
    peekDirection: CardPeekDirection = CardPeekDirection.ABOVE
): CadDeckIdleTimerState = rememberCadDeckIdleTimerState(
    deckId = deckId,
    peekDirection = peekDirection,
    isVisible = true,
    isEnabled = true
)

/**
 * V19 §5, §6 & §7 — Application-Wide Binary Toggle-Card Component.
 *
 * - **ENABLE state**: Green foreground card; grey DISABLE card concealed behind at rest,
 *   briefly revealed for 4.0s after a toggle and during 60s section-local 4.0s idle peeks.
 * - **DISABLE state**: Neutral grey foreground card; green ENABLE card concealed behind at rest.
 * - **Non-Duplicate State Rule (§7.3)**: When [showLabelText] is false or [label] duplicates
 *   `"ENABLE"` / `"DISABLE"`, only a single state label (`ENABLE` or `DISABLE`) is rendered.
 * - **OSNAP 3-Region Layout (§6.2)**: Left fixed `[END]` badge, middle `clipToBounds()` +
 *   `basicMarquee()` viewport, right fixed single-state pill (`ENABLE` or `DISABLE`).
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun CadBinaryToggleCard(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    badgeText: String? = null,
    isInteractive: Boolean = true,
    compact: Boolean = false,
    fixedHeight: Dp? = null,
    useMarqueeLabel: Boolean = false,
    showLabelText: Boolean = true,
    enabledStatusText: String = "ENABLE",
    disabledStatusText: String = "DISABLE",
    deckId: String? = null,
    testTag: String? = null
) {
    val peekDirection = CardPeekDirection.ABOVE
    val deckState = rememberCadDeckIdleTimerState(
        deckId = deckId ?: testTag ?: "binary_toggle_$label",
        peekDirection = peekDirection,
        isVisible = true,
        isEnabled = isInteractive
    )

    val transition = updateTransition(targetState = checked, label = "CadBinaryToggleCardTransition")
    val isAnimating = transition.currentState != transition.targetState
    LaunchedEffect(isAnimating) {
        deckState.updateEligibility(transitionRunning = isAnimating)
    }

    val isPeeking = deckState.isRearCardPeeking || isAnimating
    val targetOffsetX = if (!isPeeking) 0.dp else if (checked) 3.dp else (-3).dp
    val targetOffsetY = if (!isPeeking) 0.dp else (-4).dp
    val targetRearScale = if (isPeeking) 0.99f else 0.94f

    val peekOffsetX by animateDpAsState(
        targetValue = targetOffsetX,
        animationSpec = tween(durationMillis = 210, easing = FastOutSlowInEasing),
        label = "RearPeekOffsetX"
    )
    val peekOffsetY by animateDpAsState(
        targetValue = targetOffsetY,
        animationSpec = tween(durationMillis = 210, easing = FastOutSlowInEasing),
        label = "RearPeekOffsetY"
    )
    val rearScale by animateFloatAsState(
        targetValue = targetRearScale,
        animationSpec = tween(durationMillis = 210, easing = FastOutSlowInEasing),
        label = "RearScale"
    )
    val rearAlpha by animateFloatAsState(
        targetValue = if (isPeeking) 0.85f else 0f,
        animationSpec = tween(durationMillis = 180, easing = FastOutSlowInEasing),
        label = "RearAlpha"
    )

    val frontSurfaceColor by animateColorAsState(
        targetValue = if (checked) CadToggleEnabledSurface else CadToggleDisabledSurface,
        animationSpec = tween(durationMillis = 180, easing = FastOutSlowInEasing),
        label = "FrontSurfaceColor"
    )
    val frontBorderColor by animateColorAsState(
        targetValue = if (checked) CadToggleEnabledBorder else CadToggleDisabledBorder,
        animationSpec = tween(durationMillis = 180, easing = FastOutSlowInEasing),
        label = "FrontBorderColor"
    )
    val rearSurfaceColor by animateColorAsState(
        targetValue = if (checked) CadToggleDisabledSurface else CadToggleEnabledSurface,
        animationSpec = tween(durationMillis = 180, easing = FastOutSlowInEasing),
        label = "RearSurfaceColor"
    )
    val rearBorderColor by animateColorAsState(
        targetValue = if (checked) CadToggleDisabledBorder.copy(alpha = 0.82f) else CadToggleEnabledBorder.copy(alpha = 0.78f),
        animationSpec = tween(durationMillis = 180, easing = FastOutSlowInEasing),
        label = "RearBorderColor"
    )
    val statusDotColor by animateColorAsState(
        targetValue = if (checked) CadEmeraldCrossing else CadTextMuted,
        animationSpec = tween(durationMillis = 180, easing = FastOutSlowInEasing),
        label = "StatusDotColor"
    )
    val contentAlpha by animateFloatAsState(
        targetValue = if (isInteractive) 1f else 0.48f,
        animationSpec = tween(durationMillis = 120),
        label = "EnabledAlpha"
    )

    val interactionSource = remember { MutableInteractionSource() }
    val activeStatusLabel = CadDeckIdleTimerState.normalizeBinaryStateLabel(
        checked = checked,
        candidate = if (checked) enabledStatusText else disabledStatusText
    )
    val oppositeStatusLabel = CadDeckIdleTimerState.normalizeBinaryStateLabel(
        checked = !checked,
        candidate = if (!checked) enabledStatusText else disabledStatusText
    )

    // V19 §7.3: Suppress duplicate label when external row already provides the label or label duplicates ENABLE/DISABLE
    val trimmedLabel = label.trim()
    val effectiveShowLabel = showLabelText &&
        trimmedLabel.isNotEmpty() &&
        !trimmedLabel.equals(activeStatusLabel, ignoreCase = true) &&
        !trimmedLabel.equals(oppositeStatusLabel, ignoreCase = true) &&
        !trimmedLabel.equals("ENABLE", ignoreCase = true) &&
        !trimmedLabel.equals("DISABLE", ignoreCase = true)

    val a11yDescription = buildString {
        if (!badgeText.isNullOrBlank()) append("[$badgeText] ")
        if (effectiveShowLabel) {
            append(trimmedLabel)
            append(": ")
        }
        append(activeStatusLabel)
    }
    val shape = RoundedCornerShape(if (compact) 6.dp else 8.dp)

    val rootModifier = modifier
        .let { if (testTag != null) it.testTag(testTag) else it }
        .let { if (fixedHeight != null) it.height(fixedHeight) else it.heightIn(min = if (compact) 36.dp else 52.dp) }
        .alpha(contentAlpha)
        .semantics(mergeDescendants = true) {
            role = Role.Switch
            selected = checked
            stateDescription = activeStatusLabel
            contentDescription = a11yDescription
        }
        .clickable(
            interactionSource = interactionSource,
            indication = null,
            enabled = isInteractive && !isAnimating
        ) {
            deckState.onDirectDeckInteraction(didExchangeCards = true)
            onCheckedChange(!checked)
        }

    if (compact) {
        Box(
            modifier = rootModifier.padding(top = 4.dp, start = 2.dp, end = 2.dp),
            contentAlignment = Alignment.Center
        ) {
            // Rear card — 100% concealed at rest (alpha = 0f), peeks from above for 4s after toggle or after 60s idle
            Surface(
                modifier = Modifier
                    .matchParentSize()
                    .offset { IntOffset(peekOffsetX.roundToPx(), peekOffsetY.roundToPx()) }
                    .scale(rearScale)
                    .alpha(rearAlpha)
                    .zIndex(0f),
                color = rearSurfaceColor,
                shape = shape,
                border = BorderStroke(1.dp, rearBorderColor)
            ) {}

            // Active foreground card
            Surface(
                modifier = Modifier.zIndex(1f),
                color = frontSurfaceColor,
                shape = shape,
                border = BorderStroke(if (checked) 1.35.dp else 1.dp, frontBorderColor)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .background(statusDotColor, CircleShape)
                    )
                    if (effectiveShowLabel) {
                        Text(
                            text = trimmedLabel,
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 10.sp,
                                fontWeight = if (checked) FontWeight.Bold else FontWeight.Medium
                            ),
                            color = if (checked) CadToggleEnabledText else CadToggleDisabledText,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Text(
                        text = activeStatusLabel,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontFamily = FontFamily.Monospace,
                            fontSize = if (effectiveShowLabel) 8.5.sp else 9.5.sp,
                            fontWeight = FontWeight.Bold
                        ),
                        color = if (checked) CadEmeraldCrossing else CadTextMuted,
                        maxLines = 1,
                        softWrap = false
                    )
                }
            }
        }
    } else {
        Box(
            modifier = rootModifier.padding(top = 4.dp, end = 4.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            // Rear card — 100% concealed behind foreground at rest (alpha = 0f), peeks from above for 4s
            Surface(
                modifier = Modifier
                    .matchParentSize()
                    .offset { IntOffset(peekOffsetX.roundToPx(), peekOffsetY.roundToPx()) }
                    .scale(rearScale)
                    .alpha(rearAlpha)
                    .zIndex(0f),
                color = rearSurfaceColor,
                shape = shape,
                border = BorderStroke(1.dp, rearBorderColor)
            ) {}

            // Foreground card
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .zIndex(1f),
                color = frontSurfaceColor,
                shape = shape,
                border = BorderStroke(if (checked) 1.4.dp else 1.dp, frontBorderColor)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = if (effectiveShowLabel || !badgeText.isNullOrBlank()) {
                        Arrangement.SpaceBetween
                    } else {
                        Arrangement.Center
                    }
                ) {
                    if (effectiveShowLabel || !badgeText.isNullOrBlank()) {
                        Row(
                            modifier = Modifier
                                .weight(1f)
                                .widthIn(min = 0.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            // Region 1: Fixed-width short identifier on the left (e.g. [END])
                            if (!badgeText.isNullOrBlank()) {
                                Surface(
                                    modifier = Modifier.widthIn(min = 38.dp),
                                    color = if (checked) CadEmeraldCrossing.copy(alpha = 0.22f) else CadPanelElevated,
                                    shape = RoundedCornerShape(4.dp),
                                    border = BorderStroke(
                                        0.8.dp,
                                        if (checked) CadToggleEnabledBorder else CadBorderHairline
                                    )
                                ) {
                                    Box(
                                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = if (badgeText.startsWith("[")) badgeText else "[$badgeText]",
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                fontFamily = FontFamily.Monospace,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 9.5.sp
                                            ),
                                            color = if (checked) CadToggleEnabledText else CadTextMuted,
                                            maxLines = 1,
                                            softWrap = false
                                        )
                                    }
                                }
                            }

                            // Region 2: Clipped middle marquee viewport (V19 §6.2 & §6.3)
                            if (effectiveShowLabel) {
                                Column(
                                    modifier = Modifier
                                        .weight(1f)
                                        .widthIn(min = 0.dp)
                                        .padding(horizontal = 2.dp)
                                        .clipToBounds()
                                ) {
                                    Text(
                                        text = trimmedLabel,
                                        modifier = if (useMarqueeLabel) {
                                            Modifier
                                                .fillMaxWidth()
                                                .basicMarquee(
                                                    iterations = Int.MAX_VALUE,
                                                    initialDelayMillis = 0,
                                                    repeatDelayMillis = 0,
                                                    velocity = 26.dp
                                                )
                                        } else {
                                            Modifier.fillMaxWidth()
                                        },
                                        style = MaterialTheme.typography.bodySmall.copy(
                                            fontWeight = FontWeight.SemiBold,
                                            fontSize = 11.5.sp
                                        ),
                                        color = if (checked) CadTextPrimary else CadTextSecondary,
                                        maxLines = 1,
                                        softWrap = false,
                                        overflow = if (useMarqueeLabel) TextOverflow.Clip else TextOverflow.Ellipsis
                                    )
                                    if (!subtitle.isNullOrBlank()) {
                                        Text(
                                            text = subtitle,
                                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                            color = if (checked) CadToggleEnabledText.copy(alpha = 0.8f) else CadTextMuted,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.width(6.dp))
                    }

                    // Region 3: Fixed single-state pill on the right (shows ONLY active front state)
                    Surface(
                        modifier = Modifier
                            .widthIn(min = 60.dp)
                            .zIndex(1f),
                        color = if (checked) CadEmeraldCrossing.copy(alpha = 0.24f) else CadSurfaceCard,
                        shape = RoundedCornerShape(5.dp),
                        border = BorderStroke(
                            1.dp,
                            if (checked) CadToggleEnabledBorder else CadBorderStrong
                        )
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(5.dp)
                                    .background(statusDotColor, CircleShape)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = activeStatusLabel,
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold
                                ),
                                color = if (checked) CadToggleEnabledText else CadToggleDisabledText,
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun CadBinaryToggleCard(
    enabled: Boolean,
    onToggle: (Boolean) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    badgeText: String? = null,
    enabledStatusText: String = "ENABLE",
    disabledStatusText: String = "DISABLE",
    compact: Boolean = false,
    fixedHeight: Dp? = null,
    useMarqueeLabel: Boolean = false,
    showLabelText: Boolean = true,
    deckId: String? = null,
    testTag: String? = null
) {
    CadBinaryToggleCard(
        checked = enabled,
        onCheckedChange = onToggle,
        label = label,
        modifier = modifier,
        subtitle = subtitle,
        badgeText = badgeText,
        isInteractive = true,
        compact = compact,
        fixedHeight = fixedHeight,
        useMarqueeLabel = useMarqueeLabel,
        showLabelText = showLabelText,
        enabledStatusText = enabledStatusText,
        disabledStatusText = disabledStatusText,
        deckId = deckId,
        testTag = testTag
    )
}

data class CadHorizontalCardOption<T>(
    val value: T,
    val title: String,
    val subtitle: String = "",
    val shortBadge: String = "",
    val accentColor: Color = CadCyanPrimary,
    val containerColor: Color = CadToggleEnabledSurface
)

@Composable
fun <T> CadHorizontalOptionCardDeck(
    deckId: String,
    options: List<CadHorizontalCardOption<T>>,
    selectedValue: T,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    testTag: String? = null
) {
    val selectedOption = options.firstOrNull { it.value == selectedValue } ?: options.firstOrNull() ?: return
    CadHorizontalOptionCardDeck(
        deckId = deckId,
        options = options,
        selectedOption = selectedOption,
        onOptionSelected = { chosen -> onSelect(chosen.value) },
        optionLabel = { it.title },
        optionBadge = { opt ->
            val idx = options.indexOf(opt).coerceAtLeast(0)
            if (opt.shortBadge.isNotBlank()) "${opt.shortBadge} • %02d / %02d".format(idx + 1, options.size)
            else "%02d / %02d".format(idx + 1, options.size)
        },
        modifier = modifier,
        testTag = testTag
    )
}

/**
 * V19 §8.1, §8.2 & §10.2 — Horizontal Card-Based Multi-Option Selector.
 *
 * - Current option is the centered foreground card.
 * - Rear card is completely concealed behind it at rest, briefly revealed from above for 4.0s
 *   after a change and during 60s section-local 4.0s idle peeks.
 * - Swipe left moves forward one option; swipe right moves backward one option.
 * - No static left/right arrow buttons (`‹` / `›`) on the card face; instead, a subtle
 *   swipe-direction-aware chevron trail (`«` or `»`) animates inside the card background
 *   matching the user's horizontal drag/swipe direction and fades to 0 at rest.
 */
@Composable
fun <T> CadHorizontalOptionCardDeck(
    deckId: String,
    options: List<T>,
    selectedOption: T,
    onOptionSelected: (T) -> Unit,
    optionLabel: (T) -> String,
    modifier: Modifier = Modifier,
    optionBadge: ((T) -> String)? = null,
    onForegroundTapped: (() -> Unit)? = null,
    testTag: String? = null
) {
    if (options.isEmpty()) return
    val currentIndex = options.indexOf(selectedOption).coerceAtLeast(0)
    val deckState = rememberCadDeckIdleTimerState(
        deckId = deckId,
        peekDirection = CardPeekDirection.ABOVE,
        isVisible = true,
        isEnabled = options.size > 1
    )

    // Swipe-direction background chevron trail state (-1 = right-to-left «, +1 = left-to-right », 0 = idle)
    var swipeDirectionSign by remember { mutableIntStateOf(0) }
    var swipeTrailTriggerId by remember { mutableIntStateOf(0) }
    var activeDragFraction by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(swipeTrailTriggerId) {
        if (swipeTrailTriggerId > 0) {
            delay(320L)
            activeDragFraction = 0f
            swipeDirectionSign = 0
        }
    }

    val transition = updateTransition(targetState = currentIndex, label = "HorizontalOptionDeck_$deckId")
    val isAnimating = transition.currentState != transition.targetState
    LaunchedEffect(isAnimating) {
        deckState.updateEligibility(transitionRunning = isAnimating)
    }

    val isPeeking = deckState.isRearCardPeeking || isAnimating
    val peekOffsetY by animateDpAsState(
        targetValue = if (isPeeking) (-5).dp else 0.dp,
        animationSpec = tween(durationMillis = 210, easing = FastOutSlowInEasing),
        label = "OptionDeckPeekY"
    )
    val peekOffsetX by animateDpAsState(
        targetValue = if (isPeeking) 4.dp else 0.dp,
        animationSpec = tween(durationMillis = 210, easing = FastOutSlowInEasing),
        label = "OptionDeckPeekX"
    )
    val rearScale by animateFloatAsState(
        targetValue = if (isPeeking) 0.985f else 0.93f,
        animationSpec = tween(durationMillis = 210, easing = FastOutSlowInEasing),
        label = "OptionDeckRearScale"
    )
    val rearAlpha by animateFloatAsState(
        targetValue = if (isPeeking) 0.85f else 0f,
        animationSpec = tween(durationMillis = 180, easing = FastOutSlowInEasing),
        label = "OptionDeckRearAlpha"
    )
    val trailAlpha by animateFloatAsState(
        targetValue = if (swipeDirectionSign != 0) 0.22f else 0f,
        animationSpec = tween(durationMillis = 200, easing = FastOutSlowInEasing),
        label = "OptionDeckSwipeTrailAlpha"
    )
    val trailShiftDp by animateDpAsState(
        targetValue = when (swipeDirectionSign) {
            -1 -> (-12).dp
            1 -> 12.dp
            else -> 0.dp
        },
        animationSpec = tween(durationMillis = 240, easing = FastOutSlowInEasing),
        label = "OptionDeckSwipeTrailShift"
    )

    val nextIndex = (currentIndex + 1) % options.size
    val prevIndex = if (currentIndex - 1 < 0) options.lastIndex else currentIndex - 1
    val nextOption = options[nextIndex]

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp)
            .padding(top = 5.dp, end = 4.dp)
            .let { if (testTag != null) it.testTag(testTag) else it }
            .pointerInput(currentIndex, options.size, isAnimating) {
                var dragAccumX = 0f
                var changedInGesture = false
                detectHorizontalDragGestures(
                    onDragStart = {
                        dragAccumX = 0f
                        changedInGesture = false
                    },
                    onDragEnd = {
                        if (!changedInGesture) {
                            activeDragFraction = 0f
                            swipeDirectionSign = 0
                        }
                    },
                    onDragCancel = {
                        activeDragFraction = 0f
                        swipeDirectionSign = 0
                    },
                    onHorizontalDrag = { change, dragAmount ->
                        if (isAnimating || changedInGesture || options.size <= 1) return@detectHorizontalDragGestures
                        dragAccumX += dragAmount
                        val thresholdPx = 28.dp.toPx()
                        swipeDirectionSign = if (dragAccumX < 0f) -1 else if (dragAccumX > 0f) 1 else 0
                        activeDragFraction = (kotlin.math.abs(dragAccumX) / thresholdPx).coerceIn(0f, 1f)
                        if (dragAccumX <= -thresholdPx) {
                            // Swipe right-to-left -> forward one option
                            change.consume()
                            changedInGesture = true
                            swipeDirectionSign = -1
                            swipeTrailTriggerId++
                            deckState.onDirectDeckInteraction(didExchangeCards = true)
                            onOptionSelected(options[nextIndex])
                        } else if (dragAccumX >= thresholdPx) {
                            // Swipe left-to-right -> backward one option
                            change.consume()
                            changedInGesture = true
                            swipeDirectionSign = 1
                            swipeTrailTriggerId++
                            deckState.onDirectDeckInteraction(didExchangeCards = true)
                            onOptionSelected(options[prevIndex])
                        }
                    }
                )
            }
            .pointerInput(currentIndex, isAnimating) {
                detectTapGestures(
                    onTap = {
                        if (!isAnimating) {
                            deckState.onDirectDeckInteraction(didExchangeCards = false)
                            onForegroundTapped?.invoke()
                        }
                    }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        // Nearest rear card (depth == 1) — completely concealed at rest (alpha = 0f), peeks from above for 4s
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .offset { IntOffset(peekOffsetX.roundToPx(), peekOffsetY.roundToPx()) }
                .scale(rearScale)
                .alpha(rearAlpha)
                .zIndex(0f),
            color = CadToggleDisabledSurface,
            shape = RoundedCornerShape(8.dp),
            border = BorderStroke(1.dp, CadToggleDisabledBorder.copy(alpha = if (isPeeking) 0.85f else 0f))
        ) {
            if (isPeeking) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = optionLabel(nextOption),
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                        color = CadTextMuted.copy(alpha = 0.55f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        // Foreground card (active option)
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .zIndex(1f),
            color = CadToggleEnabledSurface,
            shape = RoundedCornerShape(8.dp),
            border = BorderStroke(1.4.dp, CadToggleEnabledBorder)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 14.dp),
                contentAlignment = Alignment.Center
            ) {
                // Background swipe-direction chevron trail (V19 §8.2) — visible only during/after swipe
                if (trailAlpha > 0.01f) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .offset { IntOffset(trailShiftDp.roundToPx(), 0) }
                            .alpha(trailAlpha),
                        horizontalArrangement = if (swipeDirectionSign < 0) Arrangement.End else Arrangement.Start,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (swipeDirectionSign < 0) "«  «  «" else "»  »  »",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp
                            ),
                            color = CadCyanPrimary
                        )
                    }
                }

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = optionLabel(selectedOption),
                        style = MaterialTheme.typography.labelLarge.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        ),
                        color = CadToggleEnabledText,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    val badge = optionBadge?.invoke(selectedOption)
                    Text(
                        text = badge ?: "%02d / %02d".format(currentIndex + 1, options.size),
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 9.5.sp
                        ),
                        color = CadEmeraldCrossing,
                        maxLines = 1,
                        softWrap = false
                    )
                }
            }
        }
    }
}

/**
 * Section 9.4 & 10.6 — Two-Card Action Deck (used for QDIM Footer `ENABLE & GENERATE` / `CLEAR ALL`
 * and Command `SEND` / `CANCEL`).
 *
 * - Rear card is completely concealed at rest, reveals for 2.4s after downward-swipe exchange,
 *   and performs 75s section-local idle peeks.
 * - Rear card never intercepts taps.
 */
@Composable
fun CadExchangeableDualActionDeck(
    deckId: String,
    isPrimaryForeground: Boolean,
    onExchangeCards: () -> Unit,
    primaryLabel: String,
    secondaryLabel: String,
    onPrimaryTap: () -> Unit,
    onSecondaryTap: () -> Unit,
    modifier: Modifier = Modifier,
    onSecondaryLongPress: (() -> Unit)? = null,
    allowDownwardSwipeExchange: Boolean = true,
    peekDirection: CardPeekDirection = CardPeekDirection.SIDE,
    isVisible: Boolean = true,
    isEnabled: Boolean = true,
    primaryTestTag: String? = null,
    secondaryTestTag: String? = null,
    deckTestTag: String? = null
) {
    val deckState = rememberCadDeckIdleTimerState(
        deckId = deckId,
        peekDirection = peekDirection,
        isVisible = isVisible,
        isEnabled = isEnabled
    )
    val transition = updateTransition(targetState = isPrimaryForeground, label = "DualActionDeck_$deckId")
    val isAnimating = transition.currentState != transition.targetState
    LaunchedEffect(isAnimating) {
        deckState.updateEligibility(transitionRunning = isAnimating)
    }

    val isPeeking = deckState.isRearCardPeeking || isAnimating
    val peekOffsetX by animateDpAsState(
        targetValue = when {
            !isPeeking -> 0.dp
            peekDirection == CardPeekDirection.SIDE -> if (isPrimaryForeground) 5.dp else (-5).dp
            else -> 3.dp
        },
        animationSpec = tween(durationMillis = 210, easing = FastOutSlowInEasing),
        label = "DualDeckPeekX"
    )
    val peekOffsetY by animateDpAsState(
        targetValue = if (isPeeking) (-4).dp else 0.dp,
        animationSpec = tween(durationMillis = 210, easing = FastOutSlowInEasing),
        label = "DualDeckPeekY"
    )
    val rearScale by animateFloatAsState(
        targetValue = if (isPeeking) 0.98f else 0.92f,
        animationSpec = tween(durationMillis = 210, easing = FastOutSlowInEasing),
        label = "DualDeckRearScale"
    )

    val frontSurface = if (isPrimaryForeground) CadToggleEnabledSurface else CadCardDestructiveSurface
    val frontBorder = if (isPrimaryForeground) CadToggleEnabledBorder else CadCardDestructiveBorder
    val frontText = if (isPrimaryForeground) CadToggleEnabledText else CadCardDestructiveText

    val rearSurface = if (isPrimaryForeground) CadCardDestructiveSurface else CadToggleEnabledSurface
    val rearBorder = if (isPrimaryForeground) CadCardDestructiveBorder else CadToggleEnabledBorder

    val foregroundLabel = if (isPrimaryForeground) primaryLabel else secondaryLabel
    val activeTag = if (isPrimaryForeground) primaryTestTag else secondaryTestTag

    Box(
        modifier = modifier
            .heightIn(min = 48.dp)
            .padding(top = 4.dp, end = 4.dp)
            .let { if (deckTestTag != null) it.testTag(deckTestTag) else it },
        contentAlignment = Alignment.Center
    ) {
        // Rear card — visual context only, never intercepts taps
        Surface(
            modifier = Modifier
                .matchParentSize()
                .offset { IntOffset(peekOffsetX.roundToPx(), peekOffsetY.roundToPx()) }
                .scale(rearScale)
                .zIndex(0f),
            color = rearSurface,
            shape = RoundedCornerShape(8.dp),
            border = BorderStroke(1.dp, rearBorder.copy(alpha = if (isPeeking) 0.8f else 0f))
        ) {}

        // Foreground card — authoritative interactive target
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 44.dp)
                .zIndex(1f)
                .let { if (activeTag != null) it.testTag(activeTag) else it }
                .pointerInput(isPrimaryForeground, isAnimating, allowDownwardSwipeExchange) {
                    if (!allowDownwardSwipeExchange) return@pointerInput
                    var dragAccumY = 0f
                    var exchanged = false
                    detectVerticalDragGestures(
                        onDragStart = {
                            dragAccumY = 0f
                            exchanged = false
                        },
                        onVerticalDrag = { change, dragAmount ->
                            if (isAnimating || exchanged) return@detectVerticalDragGestures
                            dragAccumY += dragAmount
                            if (dragAccumY >= 20.dp.toPx()) {
                                change.consume()
                                exchanged = true
                                deckState.onDirectDeckInteraction(didExchangeCards = true)
                                onExchangeCards()
                            }
                        }
                    )
                }
                .pointerInput(isPrimaryForeground, isAnimating) {
                    detectTapGestures(
                        onTap = {
                            if (isAnimating || !isEnabled) return@detectTapGestures
                            deckState.onDirectDeckInteraction(didExchangeCards = false)
                            if (isPrimaryForeground) onPrimaryTap() else onSecondaryTap()
                        },
                        onLongPress = {
                            if (isAnimating || !isEnabled) return@detectTapGestures
                            if (!isPrimaryForeground && onSecondaryLongPress != null) {
                                deckState.onDirectDeckInteraction(didExchangeCards = false)
                                onSecondaryLongPress()
                            }
                        }
                    )
                },
            color = frontSurface,
            shape = RoundedCornerShape(8.dp),
            border = BorderStroke(1.4.dp, frontBorder)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Text(
                    text = foregroundLabel,
                    style = MaterialTheme.typography.labelLarge.copy(
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    ),
                    color = frontText,
                    maxLines = 1
                )
            }
        }
    }
}
