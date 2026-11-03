package com.example.ui.components

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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
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
import com.example.ui.theme.CadBorderHairline
import com.example.ui.theme.CadBorderStrong
import com.example.ui.theme.CadCyanPrimary
import com.example.ui.theme.CadEmeraldCrossing
import com.example.ui.theme.CadPanelElevated
import com.example.ui.theme.CadSurfaceCard
import com.example.ui.theme.CadTextMuted
import com.example.ui.theme.CadTextPrimary
import com.example.ui.theme.CadTextSecondary
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
 * Section 9.5–9.8 — Section-local card deck inactivity & transient reveal state machine.
 *
 * Each card deck owns its own independent instance. Events outside the deck never reset its timer.
 * - After a user-initiated card exchange, the new rear card remains slightly visible for
 *   [POST_EXCHANGE_REVEAL_MS] (2,400 ms) and then smoothly retracts completely behind the foreground card.
 * - While eligible (parent screen/dialog open, visible, enabled, window resumed, not animating),
 *   an isolated [IDLE_PEEK_INTERVAL_MS] (75,000 ms) timer runs. When it expires, the rear card
 *   peeks subtly for [IDLE_PEEK_HOLD_MS] (1,000 ms) and then retracts completely without changing
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
        const val IDLE_PEEK_INTERVAL_MS = 75_000L
        const val IDLE_PEEK_HOLD_MS = 1_000L
        const val POST_EXCHANGE_REVEAL_MS = 2_400L

        /**
         * Section 9.8 & 13.2 — Command Send/Cancel deck visibility rule:
         * CommandDeckVisible = KeyboardVisible OR CommandTextNotEmpty
         */
        fun isCommandDeckVisible(keyboardVisible: Boolean, commandText: String): Boolean {
            return keyboardVisible || commandText.isNotEmpty()
        }

        fun isCommandDeckVisible(isImeVisible: Boolean, commandLineInput: String, dummy: Unit = Unit): Boolean {
            return isCommandDeckVisible(keyboardVisible = isImeVisible, commandText = commandLineInput)
        }

        /**
         * Normalizes any legacy binary status text ("ON", "OFF", "POLAR", "PSNAP", "REL SEG")
         * to the authoritative V18 labels "ENABLE" and "DISABLE" (Section 9.3 & 12.1).
         */
        fun normalizeBinaryStateLabel(checked: Boolean, candidate: String? = null): String {
            if (candidate == "ENABLE" || candidate == "DISABLE") return candidate
            return if (checked) "ENABLE" else "DISABLE"
        }
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
    var elapsedIdleMs: Long = 0L
        private set
    var elapsedPostExchangeMs: Long = 0L
        private set

    val isEligibleForTimer: Boolean
        get() = isParentVisible && isDeckEnabled && isWindowResumed && !isTransitionRunning

    val isRearCardPeeking: Boolean
        get() = (isPostExchangeRevealing || (isIdlePeeking && !isReducedMotionEnabled)) &&
            isParentVisible && isWindowResumed

    val isRearCardVisible: Boolean get() = isRearCardPeeking
    val isRearCardRevealed: Boolean get() = isRearCardPeeking
    val isPostExchangeRevealActive: Boolean get() = isPostExchangeRevealing
    val interactionGeneration: Int get() = directInteractionGeneration

    val isRearCardConcealed: Boolean
        get() = !isRearCardPeeking

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
            // Immediately cancel pending timer and idle peek when hidden/disabled/suspended (Section 9.6)
            elapsedIdleMs = 0L
            isIdlePeeking = false
            if (!parentVisible || !windowResumed) {
                isPostExchangeRevealing = false
                elapsedPostExchangeMs = 0L
            }
        } else if (!wasEligible) {
            // Start a fresh 75-second interval when becoming eligible again
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
        if (didExchangeCards) {
            isPostExchangeRevealing = true
            elapsedPostExchangeMs = 0L
        }
    }

    fun onCardExchanged(isCommandPromptOrDeck: Boolean = false) = onDirectDeckInteraction(didExchangeCards = true)
    fun onCardExchange(isCommandPromptOrDeck: Boolean = false) = onDirectDeckInteraction(didExchangeCards = true)
    fun onOrderExchanged(isCommandPromptOrDeck: Boolean = false) = onDirectDeckInteraction(didExchangeCards = true)
    fun onDirectInteraction() = onDirectDeckInteraction(didExchangeCards = false)
    fun onUserInteraction() = onDirectDeckInteraction(didExchangeCards = false)
    fun cancelAllVisualPeeks() = updateEligibility(parentVisible = false)
    fun stop() = updateEligibility(parentVisible = false)
    fun startOrReset() = updateEligibility(parentVisible = true, deckEnabled = true)

    /**
     * Deterministic virtual clock step for unit tests (Section 9.9) and coroutine loop driver.
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
            }
        }

        if (!isEligibleForTimer) {
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
    isEnabled: Boolean = true
): CadDeckIdleTimerState {
    val state = remember(deckId) {
        CadDeckIdleTimerState(deckId = deckId, peekDirection = peekDirection)
    }
    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner, state) {
        val observer = LifecycleEventObserver { _, event ->
            val resumed = event == Lifecycle.Event.ON_RESUME ||
                lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
            state.updateEligibility(windowResumed = resumed)
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            state.updateEligibility(parentVisible = false)
        }
    }

    LaunchedEffect(isVisible, isEnabled) {
        state.updateEligibility(parentVisible = isVisible, deckEnabled = isEnabled)
    }

    // Drive post-exchange 2.4s reveal retraction and 75s section-local idle peek
    LaunchedEffect(
        state.isEligibleForTimer,
        state.isPostExchangeRevealing,
        state.directInteractionGeneration
    ) {
        if (!state.isParentVisible || !state.isWindowResumed) return@LaunchedEffect

        if (state.isPostExchangeRevealing) {
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

@Composable
fun BindCadDeckIdleTimerEffect(
    timerState: CadDeckIdleTimerState,
    isEligible: Boolean
) {
    LaunchedEffect(isEligible) {
        timerState.updateEligibility(parentVisible = isEligible, deckEnabled = isEligible)
    }
    LaunchedEffect(
        timerState.isEligibleForTimer,
        timerState.isPostExchangeRevealing,
        timerState.directInteractionGeneration
    ) {
        if (!timerState.isParentVisible || !timerState.isWindowResumed) return@LaunchedEffect
        if (timerState.isPostExchangeRevealing) {
            delay(timerState.postExchangeRevealMs)
            timerState.advanceTimeBy(timerState.postExchangeRevealMs)
        }
        while (timerState.isEligibleForTimer) {
            delay(timerState.idleIntervalMs)
            if (!timerState.isEligibleForTimer) break
            timerState.advanceTimeBy(timerState.idleIntervalMs)
            delay(timerState.idlePeekHoldMs)
            timerState.advanceTimeBy(timerState.idlePeekHoldMs)
        }
    }
}

/**
 * Section 9 & Section 12 — Application-Wide Binary Toggle-Card Component.
 *
 * - **ENABLE state**: Green foreground card; grey DISABLE card concealed behind at rest,
 *   briefly revealed for 2.4s after a toggle and during 75s section-local idle peeks.
 * - **DISABLE state**: Neutral grey foreground card; green ENABLE card concealed behind at rest.
 * - **Interaction**: Single tap exchanges the cards and toggles the authoritative Boolean once.
 *   No swipe gestures are attached; input is locked while the card-exchange animation is running.
 * - **State label**: Always `ENABLE` or `DISABLE` (never `ON`, `OFF`, `POLAR`, or `PSNAP`).
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
    enabledStatusText: String = "ENABLE",
    disabledStatusText: String = "DISABLE",
    deckId: String? = null,
    testTag: String? = null
) {
    val peekDirection = if (compact) CardPeekDirection.SIDE else CardPeekDirection.ABOVE
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
    val targetOffsetX = when {
        !isPeeking -> 0.dp
        peekDirection == CardPeekDirection.SIDE -> if (checked) 4.dp else (-4).dp
        else -> if (checked) 3.dp else (-3).dp
    }
    val targetOffsetY = when {
        !isPeeking -> 0.dp
        peekDirection == CardPeekDirection.ABOVE -> (-4).dp
        else -> (-2).dp
    }
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
    val a11yDescription = buildString {
        if (!badgeText.isNullOrBlank()) append("[$badgeText] ")
        append(label)
        append(": ")
        append(activeStatusLabel)
    }
    val shape = RoundedCornerShape(if (compact) 6.dp else 8.dp)

    val rootModifier = modifier
        .let { if (testTag != null) it.testTag(testTag) else it }
        .let { if (fixedHeight != null) it.height(fixedHeight) else it.heightIn(min = if (compact) 36.dp else 48.dp) }
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
            // Rear card — concealed behind foreground at rest, peeks transiently after toggle or on 75s idle timer
            Surface(
                modifier = Modifier
                    .matchParentSize()
                    .offset { IntOffset(peekOffsetX.roundToPx(), peekOffsetY.roundToPx()) }
                    .scale(rearScale)
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
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 10.sp,
                            fontWeight = if (checked) FontWeight.Bold else FontWeight.Medium
                        ),
                        color = if (checked) CadToggleEnabledText else CadToggleDisabledText,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = activeStatusLabel,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 8.5.sp,
                            fontWeight = FontWeight.Bold
                        ),
                        color = if (checked) CadEmeraldCrossing else CadTextMuted,
                        maxLines = 1
                    )
                }
            }
        }
    } else {
        Box(
            modifier = rootModifier.padding(top = 4.dp, end = 4.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            // Rear card — concealed behind foreground at rest, peeks transiently after toggle or on 75s idle timer
            Surface(
                modifier = Modifier
                    .matchParentSize()
                    .offset { IntOffset(peekOffsetX.roundToPx(), peekOffsetY.roundToPx()) }
                    .scale(rearScale)
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
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Region 1: Fixed short identifier on the left (e.g. [END])
                        if (!badgeText.isNullOrBlank()) {
                            Surface(
                                color = if (checked) CadEmeraldCrossing.copy(alpha = 0.22f) else CadPanelElevated,
                                shape = RoundedCornerShape(4.dp),
                                border = BorderStroke(
                                    0.8.dp,
                                    if (checked) CadToggleEnabledBorder else CadBorderHairline
                                )
                            ) {
                                Text(
                                    text = if (badgeText.startsWith("[")) badgeText else "[$badgeText]",
                                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp),
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 9.5.sp
                                    ),
                                    color = if (checked) CadToggleEnabledText else CadTextMuted,
                                    maxLines = 1
                                )
                            }
                        }

                        // Region 2: Full descriptive name (uses horizontal marquee when useMarqueeLabel is true)
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = label,
                                modifier = if (useMarqueeLabel) {
                                    Modifier
                                        .fillMaxWidth()
                                        .basicMarquee(
                                            iterations = Int.MAX_VALUE,
                                            initialDelayMillis = 1200,
                                            repeatDelayMillis = 1400
                                        )
                                } else {
                                    Modifier.fillMaxWidth()
                                },
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontWeight = FontWeight.SemiBold
                                ),
                                color = if (checked) CadTextPrimary else CadTextSecondary,
                                maxLines = 1,
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

                    Spacer(modifier = Modifier.width(8.dp))

                    // Region 3: Fixed ENABLE/DISABLE state pill on the right (conceals rear state at rest)
                    Box(contentAlignment = Alignment.Center) {
                        Surface(
                            modifier = Modifier
                                .offset(
                                    x = if (isPeeking) (if (checked) 2.5.dp else (-2.5).dp) else 0.dp,
                                    y = if (isPeeking) (-2.5).dp else 0.dp
                                )
                                .scale(if (isPeeking) 0.98f else 0.90f)
                                .zIndex(0f),
                            color = if (checked) CadToggleDisabledSurface else CadToggleEnabledSurface,
                            shape = RoundedCornerShape(5.dp),
                            border = BorderStroke(
                                0.8.dp,
                                if (checked) CadToggleDisabledBorder else CadToggleEnabledBorder.copy(alpha = 0.75f)
                            )
                        ) {
                            Text(
                                text = oppositeStatusLabel,
                                modifier = Modifier
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                                    .alpha(if (isPeeking) 0.35f else 0f),
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 8.5.sp,
                                    fontWeight = FontWeight.Bold
                                ),
                                color = CadTextMuted
                            )
                        }
                        Surface(
                            modifier = Modifier.zIndex(1f),
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
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(5.dp)
                                        .background(statusDotColor, CircleShape)
                                )
                                Text(
                                    text = activeStatusLabel,
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold
                                    ),
                                    color = if (checked) CadToggleEnabledText else CadToggleDisabledText,
                                    maxLines = 1
                                )
                            }
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
            if (opt.shortBadge.isNotBlank()) "${opt.shortBadge} • ${idx + 1}/${options.size}"
            else "${idx + 1} / ${options.size} • Swipe ⇄"
        },
        modifier = modifier,
        testTag = testTag
    )
}

/**
 * Section 10.3 & 10.4 — Horizontal Card-Based Multi-Option Selector for QDIM.
 *
 * - Current option is the centered foreground card.
 * - Rear card is completely concealed behind it at rest, briefly revealed for 2.4s after a change
 *   and during 75s section-local idle peeks.
 * - Swipe left moves forward one option; swipe right moves backward one option.
 * - Vertical drag is NOT consumed by this selector so parent dialog scrolling works without conflict.
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
                    onHorizontalDrag = { change, dragAmount ->
                        if (isAnimating || changedInGesture || options.size <= 1) return@detectHorizontalDragGestures
                        dragAccumX += dragAmount
                        val thresholdPx = 28.dp.toPx()
                        if (dragAccumX <= -thresholdPx) {
                            // Swipe left -> forward one option
                            change.consume()
                            changedInGesture = true
                            deckState.onDirectDeckInteraction(didExchangeCards = true)
                            onOptionSelected(options[nextIndex])
                        } else if (dragAccumX >= thresholdPx) {
                            // Swipe right -> backward one option
                            change.consume()
                            changedInGesture = true
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
        // Rear card (next option in deck, completely concealed behind foreground card at rest)
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .offset { IntOffset(peekOffsetX.roundToPx(), peekOffsetY.roundToPx()) }
                .scale(rearScale)
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
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Step backward button (also allows deterministic tap navigation in tests/accessibility)
                Surface(
                    modifier = Modifier
                        .size(28.dp)
                        .let { if (testTag != null) it.testTag("${testTag}_prev") else it }
                        .clickable(enabled = !isAnimating && options.size > 1) {
                            deckState.onDirectDeckInteraction(didExchangeCards = true)
                            onOptionSelected(options[prevIndex])
                        },
                    color = CadPanelElevated,
                    shape = RoundedCornerShape(6.dp),
                    border = BorderStroke(0.8.dp, CadBorderStrong)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = "‹",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold
                            ),
                            color = CadCyanPrimary
                        )
                    }
                }

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 10.dp),
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
                        text = badge ?: "${currentIndex + 1} / ${options.size} • Swipe ⇄",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 9.5.sp
                        ),
                        color = CadEmeraldCrossing
                    )
                }

                // Step forward button
                Surface(
                    modifier = Modifier
                        .size(28.dp)
                        .let { if (testTag != null) it.testTag("${testTag}_next") else it }
                        .clickable(enabled = !isAnimating && options.size > 1) {
                            deckState.onDirectDeckInteraction(didExchangeCards = true)
                            onOptionSelected(options[nextIndex])
                        },
                    color = CadPanelElevated,
                    shape = RoundedCornerShape(6.dp),
                    border = BorderStroke(0.8.dp, CadBorderStrong)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = "›",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold
                            ),
                            color = CadCyanPrimary
                        )
                    }
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
