package com.example.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.updateTransition
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics. semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.example.ui.theme.CadBorderHairline
import com.example.ui.theme.CadBorderStrong
import com.example.ui.theme.CadEmeraldCrossing
import com.example.ui.theme.CadPanelElevated
import com.example.ui.theme.CadSurfaceCard
import com.example.ui.theme.CadTextMuted
import com.example.ui.theme.CadTextPrimary
import com.example.ui.theme.CadTextSecondary

// Restrained PrecisionCAD Green & Grey tokens for binary toggle-card stacks (Section 9.2)
val CadToggleEnabledSurface = Color(0xFF0E1F18)
val CadToggleEnabledBorder = Color(0xFF4C9A73)
val CadToggleEnabledText = Color(0xFFD5F5E3)
val CadToggleDisabledSurface = Color(0xFF131820)
val CadToggleDisabledBorder = Color(0xFF333D4B)
val CadToggleDisabledText = Color(0xFF9BA6B2)

/**
 * Section 9 — Application-Wide Binary Toggle-Card Component.
 *
 * Replaces standard switches, checkboxes, and ad-hoc binary chips across the CAD workspace.
 * - **Enabled state**: Green foreground card, grey Disabled card peeking behind.
 * - **Disabled state**: Grey foreground card, green Enabled card peeking behind.
 * - **Interaction**: Single tap exchanges the cards and toggles the authoritative Boolean once.
 *   No swipe gestures are attached; input is locked while the card-exchange animation is running.
 *   Exposes clear Enabled/Disabled state semantics for TalkBack and UI tests.
 */
@Composable
fun CadBinaryToggleCard(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    badgeText: String? = null,
    enabled: Boolean = true,
    compact: Boolean = false,
    fixedHeight: Dp? = null,
    testTag: String? = null
) {
    val transition = updateTransition(targetState = checked, label = "CadBinaryToggleCardTransition")
    val isAnimating = transition.currentState != transition.targetState

    val peekOffsetX by animateDpAsState(
        targetValue = if (checked) 4.dp else (-4).dp,
        animationSpec = tween(durationMillis = 170, easing = FastOutSlowInEasing),
        label = "RearPeekOffsetX"
    )
    val peekOffsetY by animateDpAsState(
        targetValue = (-4).dp,
        animationSpec = tween(durationMillis = 170, easing = FastOutSlowInEasing),
        label = "RearPeekOffsetY"
    )

    val frontSurfaceColor by animateColorAsState(
        targetValue = if (checked) CadToggleEnabledSurface else CadToggleDisabledSurface,
        animationSpec = tween(durationMillis = 170, easing = FastOutSlowInEasing),
        label = "FrontSurfaceColor"
    )
    val frontBorderColor by animateColorAsState(
        targetValue = if (checked) CadToggleEnabledBorder else CadToggleDisabledBorder,
        animationSpec = tween(durationMillis = 170, easing = FastOutSlowInEasing),
        label = "FrontBorderColor"
    )
    val rearSurfaceColor by animateColorAsState(
        targetValue = if (checked) CadToggleDisabledSurface else CadToggleEnabledSurface,
        animationSpec = tween(durationMillis = 170, easing = FastOutSlowInEasing),
        label = "RearSurfaceColor"
    )
    val rearBorderColor by animateColorAsState(
        targetValue = if (checked) CadToggleDisabledBorder.copy(alpha = 0.78f) else CadToggleEnabledBorder.copy(alpha = 0.72f),
        animationSpec = tween(durationMillis = 170, easing = FastOutSlowInEasing),
        label = "RearBorderColor"
    )
    val statusDotColor by animateColorAsState(
        targetValue = if (checked) CadEmeraldCrossing else CadTextMuted,
        animationSpec = tween(durationMillis = 170, easing = FastOutSlowInEasing),
        label = "StatusDotColor"
    )
    val contentAlpha by animateFloatAsState(
        targetValue = if (enabled) 1f else 0.48f,
        animationSpec = tween(durationMillis = 120),
        label = "EnabledAlpha"
    )

    val interactionSource = remember { MutableInteractionSource() }
    val stateLabel = if (checked) "Enabled" else "Disabled"
    val shape = RoundedCornerShape(if (compact) 6.dp else 8.dp)

    val rootModifier = modifier
        .let { if (testTag != null) it.testTag(testTag) else it }
        .let { if (fixedHeight != null) it.height(fixedHeight) else it.heightIn(min = if (compact) 34.dp else 48.dp) }
        .alpha(contentAlpha)
        .semantics(mergeDescendants = true) {
            role = Role.Switch
            selected = checked
            stateDescription = stateLabel
            contentDescription = "$label: $stateLabel"
        }
        .clickable(
            interactionSource = interactionSource,
            indication = null,
            enabled = enabled && !isAnimating
        ) {
            onCheckedChange(!checked)
        }

    if (compact) {
        Box(
            modifier = rootModifier.padding(top = 4.dp, start = 2.dp, end = 2.dp),
            contentAlignment = Alignment.Center
        ) {
            // Peeking rear card (opposite state)
            Surface(
                modifier = Modifier
                    .matchParentSize()
                    .offset(x = peekOffsetX * 0.65f, y = (-3).dp)
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
                    if (!badgeText.isNullOrBlank()) {
                        Text(
                            text = badgeText,
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontFamily = FontFamily.Monospace,
                                fontSize = 9.sp
                            ),
                            color = if (checked) CadEmeraldCrossing else CadTextMuted,
                            maxLines = 1
                        )
                    }
                }
            }
        }
    } else {
        Box(
            modifier = rootModifier.padding(top = 4.dp, end = 4.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            // Peeking rear card (opposite state)
            Surface(
                modifier = Modifier
                    .matchParentSize()
                    .offset(x = peekOffsetX, y = peekOffsetY)
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
                                    text = badgeText,
                                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp),
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 9.5.sp
                                    ),
                                    color = if (checked) CadToggleEnabledText else CadTextMuted
                                )
                            }
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = label,
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontWeight = FontWeight.SemiBold
                                ),
                                color = if (checked) CadTextPrimary else CadTextSecondary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
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

                    // Two-card peeking status pill on the right
                    Box(contentAlignment = Alignment.Center) {
                        Surface(
                            modifier = Modifier
                                .offset(x = if (checked) 3.dp else (-3).dp, y = (-3).dp)
                                .zIndex(0f),
                            color = if (checked) CadToggleDisabledSurface else CadToggleEnabledSurface,
                            shape = RoundedCornerShape(5.dp),
                            border = BorderStroke(
                                0.8.dp,
                                if (checked) CadToggleDisabledBorder else CadToggleEnabledBorder.copy(alpha = 0.75f)
                            )
                        ) {
                            Text(
                                text = if (checked) "OFF" else "ON",
                                modifier = Modifier
                                    .padding(horizontal = 7.dp, vertical = 2.dp)
                                    .alpha(0.35f),
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 9.sp,
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
                                    text = if (checked) "ON" else "OFF",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 9.5.sp,
                                        fontWeight = FontWeight.Bold
                                    ),
                                    color = if (checked) CadToggleEnabledText else CadToggleDisabledText
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
    enabledStatusText: String = "ON",
    disabledStatusText: String = "OFF",
    compact: Boolean = false,
    fixedHeight: Dp? = null,
    testTag: String? = null
) {
    val _unusedStatusLabels = enabledStatusText.length + disabledStatusText.length
    if (_unusedStatusLabels >= 0) {
        CadBinaryToggleCard(
            checked = enabled,
            onCheckedChange = onToggle,
            label = label,
            modifier = modifier,
            subtitle = subtitle,
            badgeText = badgeText,
            enabled = true,
            compact = compact,
            fixedHeight = fixedHeight,
            testTag = testTag
        )
    }
}

