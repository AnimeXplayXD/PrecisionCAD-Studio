package com.precisioncad.studio.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SystemUpdateAlt
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.zIndex
import com.precisioncad.studio.R
import com.precisioncad.studio.cad.update.AppUpdateUiState
import com.precisioncad.studio.cad.update.ChangelogCategory
import com.precisioncad.studio.cad.update.ChangelogSection
import com.precisioncad.studio.cad.update.GitHubReleaseRecord
import com.precisioncad.studio.cad.update.HomeUpdateIconColor
import com.precisioncad.studio.cad.update.HomeUpdateIconVisualState
import com.precisioncad.studio.ui.theme.CadAmberSnap
import com.precisioncad.studio.ui.theme.CadBorderHairline
import com.precisioncad.studio.ui.theme.CadBorderStrong
import com.precisioncad.studio.ui.theme.CadCrimsonAlert
import com.precisioncad.studio.ui.theme.CadCyanPrimary
import com.precisioncad.studio.ui.theme.CadEmeraldCrossing
import com.precisioncad.studio.ui.theme.CadPanelElevated
import com.precisioncad.studio.ui.theme.CadPanelSurface
import com.precisioncad.studio.ui.theme.CadTextMuted
import com.precisioncad.studio.ui.theme.CadTextPrimary
import com.precisioncad.studio.ui.theme.CadTextSecondary
import java.util.Locale

private val UpdateIconWhite = Color(0xFFF8FAFC)
private val UpdateIconGold = Color(0xFFF59E0B)

/**
 * Phase 12.2 — Home-Screen Top-Right Update Icon.
 * - `0` updates available -> White icon, no count label, no dot.
 * - `1` update available -> Gold/yellow icon, NO number and NO dot.
 * - `> 1` updates available -> Gold/yellow icon with integer count (`2`, `3`, ...) beside the icon.
 * - Tapping in any state opens the Release Catalogue dialog.
 */
@Composable
fun HomeUpdateStatusIcon(
    updatesBehindCount: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    updateUiState: AppUpdateUiState? = null
) {
    val visualState = remember(updatesBehindCount) {
        HomeUpdateIconVisualState.fromUpdatesBehindCount(updatesBehindCount)
    }
    val tintColor = when (visualState.iconColor) {
        HomeUpdateIconColor.WHITE -> UpdateIconWhite
        HomeUpdateIconColor.GOLD -> UpdateIconGold
    }
    val borderColor = when (visualState.iconColor) {
        HomeUpdateIconColor.WHITE -> CadBorderHairline
        HomeUpdateIconColor.GOLD -> UpdateIconGold.copy(alpha = 0.75f)
    }
    val accessibilityDesc = when {
        visualState.updatesBehindCount == 0 -> "Release Catalogue: Up to date (White)"
        visualState.updatesBehindCount == 1 -> "Release Catalogue: 1 update available (Gold)"
        else -> "Release Catalogue: ${visualState.updatesBehindCount} updates available (Gold)"
    }

    Row(
        modifier = modifier
            .defaultMinSize(minWidth = 48.dp, minHeight = 36.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(CadPanelElevated)
            .border(1.dp, borderColor, RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 9.dp, vertical = 6.dp)
            .semantics { contentDescription = accessibilityDesc }
            .testTag("home_update_status_button"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.SystemUpdateAlt,
            contentDescription = null,
            tint = tintColor,
            modifier = Modifier
                .size(16.dp)
                .testTag("home_update_status_icon")
        )
        if (visualState.badgeCountText != null) {
            Spacer(modifier = Modifier.width(5.dp))
            Text(
                text = visualState.badgeCountText,
                style = MaterialTheme.typography.labelMedium,
                color = UpdateIconGold,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                softWrap = false,
                modifier = Modifier.testTag("home_update_count_label")
            )
        }
    }
}

/**
 * Phase 12.4 & 12.5 — Morphp-Style Vertical Timeline Release Catalogue & In-App Updater Dialog.
 * - Displays the actual PrecisionCAD Studio launcher icon, `View changelogs`, and `PrecisionCAD Studio · v<latestVersionName>`.
 * - Vertical timeline rail with `INSTALLED`, `LATEST`, and `PRE-RELEASE` badges, publication dates,
 *   collapsible release cards, and collapsible colored category cards (`New`, `Fixes`, `Optimisations`, `Security`).
 * - Fixed bottom two-card action deck (`DOWNLOAD` / `CANCEL` before verification -> `INSTALL` / `CANCEL` after verification).
 * - Strictly excludes any external "GitHub" redirect button.
 */
@Composable
fun CadReleaseCatalogueDialog(
    releases: List<GitHubReleaseRecord>,
    installedVersionName: String = com.precisioncad.studio.BuildConfig.VERSION_NAME,
    installedVersionCode: Int = com.precisioncad.studio.BuildConfig.VERSION_CODE,
    updatesBehindCount: Int,
    updateUiState: AppUpdateUiState,
    onRefreshCatalogue: () -> Unit,
    onStartDownload: (GitHubReleaseRecord?) -> Unit = {},
    onDownloadRelease: ((GitHubReleaseRecord) -> Unit)? = null,
    onRequestInstall: () -> Unit,
    onCancelOrDismissAction: () -> Unit = {},
    onCancelOrReset: (() -> Unit)? = null,
    onDismiss: () -> Unit
) {
    val latestVersionName = remember(releases, installedVersionName) {
        releases.firstOrNull { !it.isPrerelease }?.versionName
            ?: releases.firstOrNull()?.versionName
            ?: installedVersionName
    }
    var refreshSpinTick by remember { mutableIntStateOf(0) }
    val refreshRotation by animateFloatAsState(
        targetValue = refreshSpinTick * 360f,
        animationSpec = tween(durationMillis = 480, easing = FastOutSlowInEasing),
        label = "ReleaseCatalogueRefreshRotation"
    )

    // Latest release expanded by default; older releases collapsible
    val expandedReleaseMap = remember(releases) {
        mutableStateMapOf<Int, Boolean>().apply {
            releases.forEachIndexed { index, record ->
                put(record.versionCode, index == 0 || record.isLatestStable)
            }
        }
    }

    val isReadyToInstall = updateUiState is AppUpdateUiState.DownloadedVerified ||
        updateUiState is AppUpdateUiState.InstallSourcePermissionRequired
    val isDownloading = updateUiState is AppUpdateUiState.Downloading

    val primaryTitle = if (isReadyToInstall) "INSTALL" else "DOWNLOAD"

    val primarySubtitle = when (updateUiState) {
        is AppUpdateUiState.DownloadedVerified ->
            "Verified v${updateUiState.targetRelease.versionName} • SHA-256 ${updateUiState.computedSha256.take(12)}…"
        is AppUpdateUiState.InstallSourcePermissionRequired ->
            "Android requires allowing PrecisionCAD Studio as an install source to install v${updateUiState.targetRelease.versionName}"
        is AppUpdateUiState.Downloading -> {
            val pct = (updateUiState.progressFraction * 100f).toInt()
            val mbDone = updateUiState.bytesDownloaded / (1024.0 * 1024.0)
            val mbTotal = (updateUiState.totalBytes.coerceAtLeast(1L)) / (1024.0 * 1024.0)
            String.format(Locale.ROOT, "Downloading (%d%%) • %.1f / %.1f MB", pct, mbDone, mbTotal)
        }
        is AppUpdateUiState.DownloadFailed -> updateUiState.reason
        is AppUpdateUiState.InstallFailed -> updateUiState.reason
        is AppUpdateUiState.InstallCancelled -> updateUiState.message
        is AppUpdateUiState.InstallSucceeded ->
            "Update v${updateUiState.targetRelease.versionName} committed to Android PackageInstaller"
        is AppUpdateUiState.UpdateAvailable ->
            "Download v${updateUiState.latestRelease.versionName} ($updatesBehindCount update${if (updatesBehindCount == 1) "" else "s"} available)"
        is AppUpdateUiState.Checking -> "Checking GitHub Releases for new builds…"
        else -> "Installed v$installedVersionName (Build $installedVersionCode) is up to date"
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .heightIn(max = 650.dp)
                .border(1.dp, CadBorderStrong, RoundedCornerShape(16.dp))
                .testTag("release_catalogue_dialog"),
            color = CadPanelSurface,
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                // Header with actual PrecisionCAD Studio adaptive launcher icon + "View changelogs"
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(CadPanelElevated)
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        // Actual PrecisionCAD Studio Adaptive Launcher Icon
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(RoundedCornerShape(11.dp))
                                .border(1.dp, CadCyanPrimary.copy(alpha = 0.65f), RoundedCornerShape(11.dp))
                                .testTag("release_catalogue_app_icon"),
                            contentAlignment = Alignment.Center
                        ) {
                            Image(
                                painter = painterResource(id = R.drawable.precisioncad_background),
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                            Image(
                                painter = painterResource(id = R.drawable.precisioncad_foreground),
                                contentDescription = "PrecisionCAD Studio App Icon",
                                contentScale = ContentScale.Fit,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(4.dp)
                            )
                        }

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "View changelogs",
                                style = MaterialTheme.typography.titleLarge,
                                color = CadTextPrimary,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.testTag("release_catalogue_title")
                            )
                            Text(
                                text = "PrecisionCAD Studio · v$latestVersionName",
                                style = MaterialTheme.typography.labelMedium,
                                color = CadCyanPrimary,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.testTag("release_catalogue_subtitle")
                            )
                        }
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        IconButton(
                            onClick = {
                                refreshSpinTick += 1
                                onRefreshCatalogue()
                            },
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(CadPanelSurface)
                                .border(1.dp, CadCyanPrimary.copy(alpha = 0.6f), CircleShape)
                                .testTag("release_catalogue_refresh_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Check for Updates Now",
                                tint = CadCyanPrimary,
                                modifier = Modifier
                                    .size(18.dp)
                                    .graphicsLayer { rotationZ = refreshRotation }
                            )
                        }

                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier
                                .size(36.dp)
                                .testTag("release_catalogue_close_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Close Release Catalogue",
                                tint = CadTextSecondary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }

                HorizontalDivider(color = CadBorderStrong)

                // Download progress bar when downloading
                if (updateUiState is AppUpdateUiState.Downloading) {
                    LinearProgressIndicator(
                        progress = { updateUiState.progressFraction },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(4.dp)
                            .testTag("release_catalogue_progress_bar"),
                        color = CadCyanPrimary,
                        trackColor = CadPanelElevated
                    )
                }

                // Vertical Timeline Release Catalogue List
                LazyColumn(
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 12.dp)
                        .testTag("release_catalogue_timeline_list"),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    itemsIndexed(releases, key = { _, item -> item.versionCode }) { index, release ->
                        val isExpanded = expandedReleaseMap[release.versionCode] ?: (index == 0)
                        val isLast = index == releases.lastIndex

                        TimelineReleaseItem(
                            release = release,
                            isExpanded = isExpanded,
                            isLastItem = isLast,
                            onToggleExpand = {
                                expandedReleaseMap[release.versionCode] = !isExpanded
                            }
                        )
                    }
                }

                HorizontalDivider(color = CadBorderStrong)

                // Section 15.4, 16.3 & V18.2 §5 — Fixed Bottom Single-Position Stacked Two-Card Action Deck
                // Never side-by-side; one foreground action card ("DOWNLOAD" / "INSTALL" in green, or "CANCEL" in red)
                // with an explicit tap-to-exchange affordance, 2.4s post-exchange reveal, and 75s 1s idle peek.
                val actionDeckTimer = rememberCadDeckIdleTimerState(
                    deckId = "release_catalogue_action_deck",
                    peekDirection = CardPeekDirection.ABOVE,
                    isVisible = true,
                    isEnabled = true
                )

                var isPrimaryActionInFront by androidx.compose.runtime.saveable.rememberSaveable {
                    androidx.compose.runtime.mutableStateOf(true)
                }
                var isActionCommitLocked by remember(updateUiState) {
                    androidx.compose.runtime.mutableStateOf(false)
                }

                val downloadableTarget = remember(releases, updateUiState, installedVersionCode) {
                    when (updateUiState) {
                        is AppUpdateUiState.UpdateAvailable ->
                            updateUiState.latestRelease.takeIf { it.apkAsset != null && it.apkAsset.browserDownloadUrl.isNotBlank() }
                        else ->
                            releases.firstOrNull {
                                !it.isPrerelease &&
                                    it.versionCode > installedVersionCode &&
                                    it.apkAsset != null &&
                                    it.apkAsset.browserDownloadUrl.isNotBlank()
                            } ?: releases.firstOrNull {
                                !it.isPrerelease &&
                                    it.apkAsset != null &&
                                    it.apkAsset.browserDownloadUrl.isNotBlank()
                            } ?: releases.firstOrNull {
                                it.apkAsset != null && it.apkAsset.browserDownloadUrl.isNotBlank()
                            }
                    }
                }

                val isPrimaryActionEnabled = when {
                    isDownloading || updateUiState is AppUpdateUiState.Checking -> false
                    isReadyToInstall -> true
                    updateUiState is AppUpdateUiState.UpToDate && updatesBehindCount <= 0 -> false
                    else -> downloadableTarget != null
                }

                val deckTransition = androidx.compose.animation.core.updateTransition(
                    targetState = isPrimaryActionInFront,
                    label = "ReleaseActionDeckTransition"
                )
                val isDeckAnimating = deckTransition.currentState != deckTransition.targetState
                androidx.compose.runtime.LaunchedEffect(isDeckAnimating) {
                    actionDeckTimer.updateEligibility(transitionRunning = isDeckAnimating)
                }

                val isRearPeeking = actionDeckTimer.isRearCardPeeking || isDeckAnimating
                val rearOffsetX by androidx.compose.animation.core.animateDpAsState(
                    targetValue = if (isRearPeeking) (if (isPrimaryActionInFront) 6.dp else (-6).dp) else 0.dp,
                    animationSpec = tween(durationMillis = 210, easing = FastOutSlowInEasing),
                    label = "ReleaseRearOffsetX"
                )
                val rearOffsetY by androidx.compose.animation.core.animateDpAsState(
                    targetValue = if (isRearPeeking) (-5).dp else 0.dp,
                    animationSpec = tween(durationMillis = 210, easing = FastOutSlowInEasing),
                    label = "ReleaseRearOffsetY"
                )
                val rearScale by animateFloatAsState(
                    targetValue = if (isRearPeeking) 0.985f else 0.92f,
                    animationSpec = tween(durationMillis = 210, easing = FastOutSlowInEasing),
                    label = "ReleaseRearScale"
                )
                val rearRotation by animateFloatAsState(
                    targetValue = if (isRearPeeking) (if (isPrimaryActionInFront) 1.4f else -1.4f) else 0f,
                    animationSpec = tween(durationMillis = 210, easing = FastOutSlowInEasing),
                    label = "ReleaseRearRotation"
                )
                val rearAlpha by animateFloatAsState(
                    targetValue = if (isRearPeeking) 0.88f else 0f,
                    animationSpec = tween(durationMillis = 180, easing = FastOutSlowInEasing),
                    label = "ReleaseRearAlpha"
                )

                val frontTitle = if (isPrimaryActionInFront) primaryTitle else "CANCEL"
                val rearTitle = if (isPrimaryActionInFront) "CANCEL" else primaryTitle
                val frontSurfaceColor = if (isPrimaryActionInFront) CadToggleEnabledSurface else CadCardDestructiveSurface
                val frontBorderColor = if (isPrimaryActionInFront) CadToggleEnabledBorder else CadCardDestructiveBorder
                val frontTextColor = when {
                    !isPrimaryActionInFront -> CadCardDestructiveText
                    isPrimaryActionEnabled -> CadToggleEnabledText
                    else -> CadTextMuted
                }
                val rearSurfaceColor = if (isPrimaryActionInFront) CadCardDestructiveSurface else CadToggleEnabledSurface
                val rearBorderColor = if (isPrimaryActionInFront) CadCardDestructiveBorder else CadToggleEnabledBorder
                val rearTextColor = if (isPrimaryActionInFront) CadCardDestructiveText else CadToggleEnabledText
                val activeActionTestTag = if (isPrimaryActionInFront) {
                    "release_catalogue_primary_button"
                } else {
                    "release_catalogue_cancel_button"
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(CadPanelElevated)
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = primarySubtitle,
                        style = MaterialTheme.typography.labelSmall,
                        color = when (updateUiState) {
                            is AppUpdateUiState.DownloadFailed,
                            is AppUpdateUiState.InstallFailed -> CadCrimsonAlert
                            is AppUpdateUiState.DownloadedVerified -> CadEmeraldCrossing
                            is AppUpdateUiState.UpdateAvailable -> UpdateIconGold
                            else -> CadTextSecondary
                        },
                        maxLines = 2,
                        modifier = Modifier.testTag("release_catalogue_status_text")
                    )

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(54.dp)
                            .padding(top = 5.dp, end = 5.dp)
                            .testTag("release_catalogue_action_deck"),
                        contentAlignment = Alignment.Center
                    ) {
                        // Rear card — concealed behind foreground at rest; peeks during 2.4s post-exchange or 75s 1s idle peek.
                        // Never executes an action while concealed; when peeking, tapping the exposed tab exchanges front/rear order.
                        Surface(
                            modifier = Modifier
                                .fillMaxSize()
                                .graphicsLayer {
                                    translationX = rearOffsetX.toPx()
                                    translationY = rearOffsetY.toPx()
                                    scaleX = rearScale
                                    scaleY = rearScale
                                    rotationZ = rearRotation
                                    alpha = rearAlpha
                                }
                                .zIndex(0f)
                                .then(
                                    if (isRearPeeking && !isDeckAnimating) {
                                        Modifier
                                            .clickable {
                                                isPrimaryActionInFront = !isPrimaryActionInFront
                                                actionDeckTimer.onDirectDeckInteraction(didExchangeCards = true)
                                            }
                                            .semantics {
                                                contentDescription = "Peeking rear card $rearTitle: tap to bring to front"
                                            }
                                    } else {
                                        Modifier
                                    }
                                )
                                .testTag("release_catalogue_rear_peek_card"),
                            shape = RoundedCornerShape(10.dp),
                            color = rearSurfaceColor,
                            border = if (isRearPeeking) {
                                androidx.compose.foundation.BorderStroke(1.dp, rearBorderColor.copy(alpha = 0.82f))
                            } else {
                                null
                            }
                        ) {
                            if (isRearPeeking) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(horizontal = 14.dp, vertical = 4.dp),
                                    contentAlignment = Alignment.TopEnd
                                ) {
                                    Text(
                                        text = rearTitle,
                                        style = MaterialTheme.typography.labelSmall,
                                        fontSize = 9.sp,
                                        color = rearTextColor.copy(alpha = 0.72f),
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }

                        // Active foreground card — single stacked position
                        Surface(
                            modifier = Modifier
                                .fillMaxSize()
                                .zIndex(1f),
                            shape = RoundedCornerShape(10.dp),
                            color = frontSurfaceColor,
                            border = androidx.compose.foundation.BorderStroke(1.5.dp, frontBorderColor)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxSize(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Main body: executes the active foreground action once
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxHeight()
                                        .defaultMinSize(minHeight = 48.dp)
                                        .then(
                                            if (isPrimaryActionInFront) {
                                                if (isPrimaryActionEnabled && !isDeckAnimating && !isActionCommitLocked) {
                                                    Modifier.clickable {
                                                        isActionCommitLocked = true
                                                        actionDeckTimer.onDirectDeckInteraction(didExchangeCards = false)
                                                        if (isReadyToInstall) {
                                                            onRequestInstall()
                                                        } else {
                                                            val target = downloadableTarget
                                                            if (target != null) {
                                                                if (onDownloadRelease != null) {
                                                                    onDownloadRelease(target)
                                                                } else {
                                                                    onStartDownload(target)
                                                                }
                                                            }
                                                        }
                                                    }
                                                } else {
                                                    Modifier
                                                }
                                            } else {
                                                if (!isDeckAnimating && !isActionCommitLocked) {
                                                    Modifier.clickable {
                                                        isActionCommitLocked = true
                                                        actionDeckTimer.onDirectDeckInteraction(didExchangeCards = false)
                                                        val wasDownloading = isDownloading
                                                        if (onCancelOrReset != null) {
                                                            onCancelOrReset()
                                                        } else {
                                                            onCancelOrDismissAction()
                                                        }
                                                        if (!wasDownloading) {
                                                            onDismiss()
                                                        } else {
                                                            isActionCommitLocked = false
                                                        }
                                                    }
                                                } else {
                                                    Modifier
                                                }
                                            }
                                        )
                                        .semantics {
                                            contentDescription = if (isPrimaryActionInFront) {
                                                "$primaryTitle active action card"
                                            } else {
                                                "CANCEL active action card"
                                            }
                                        }
                                        .padding(horizontal = 14.dp)
                                        .testTag(activeActionTestTag),
                                    contentAlignment = Alignment.CenterStart
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(7.dp)
                                                .clip(CircleShape)
                                                .background(
                                                    if (isPrimaryActionInFront) {
                                                        if (isPrimaryActionEnabled) CadEmeraldCrossing else CadTextMuted
                                                    } else {
                                                        CadCardDestructiveBorder
                                                    }
                                                )
                                        )
                                        Text(
                                            text = frontTitle,
                                            style = MaterialTheme.typography.labelLarge,
                                            color = frontTextColor,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 1
                                        )
                                    }
                                }

                                // Vertical divider between main action body and exchange affordance
                                Box(
                                    modifier = Modifier
                                        .width(1.dp)
                                        .fillMaxHeight(0.68f)
                                        .background(frontBorderColor.copy(alpha = 0.55f))
                                )

                                // Explicit tap-to-exchange affordance (>= 48.dp hit target) — swaps front/rear without executing either action
                                Box(
                                    modifier = Modifier
                                        .defaultMinSize(minWidth = 88.dp, minHeight = 48.dp)
                                        .fillMaxHeight()
                                        .clickable(enabled = !isDeckAnimating) {
                                            isPrimaryActionInFront = !isPrimaryActionInFront
                                            actionDeckTimer.onDirectDeckInteraction(didExchangeCards = true)
                                        }
                                        .padding(horizontal = 12.dp)
                                        .semantics {
                                            contentDescription = "Exchange card to $rearTitle"
                                        }
                                        .testTag("release_catalogue_exchange_button"),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "⇄ $rearTitle",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (isPrimaryActionInFront) CadCardDestructiveText else CadToggleEnabledText,
                                        fontWeight = FontWeight.Bold,
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
}

@Composable
private fun TimelineReleaseItem(
    release: GitHubReleaseRecord,
    isExpanded: Boolean,
    isLastItem: Boolean,
    onToggleExpand: () -> Unit
) {
    val nodeColor = when {
        release.isLatestStable -> CadEmeraldCrossing
        release.isInstalledVersion -> CadCyanPrimary
        release.isPrerelease -> CadAmberSnap
        else -> CadBorderStrong
    }
    val chevronRotation by animateFloatAsState(
        targetValue = if (isExpanded) 0f else 180f,
        animationSpec = tween(durationMillis = 200, easing = FastOutSlowInEasing),
        label = "ReleaseChevron_${release.versionCode}"
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
    ) {
        // Vertical Timeline Rail & Node Column
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .width(24.dp)
                .fillMaxHeight()
        ) {
            Spacer(modifier = Modifier.height(8.dp))
            Box(
                modifier = Modifier
                    .size(12.dp)
                    .clip(CircleShape)
                    .background(nodeColor.copy(alpha = 0.25f))
                    .border(2.dp, nodeColor, CircleShape)
            )
            if (!isLastItem) {
                Box(
                    modifier = Modifier
                        .width(2.dp)
                        .weight(1f)
                        .padding(top = 4.dp)
                        .background(CadBorderHairline)
                )
            }
        }

        Spacer(modifier = Modifier.width(8.dp))

        // Collapsible Release Card
        Column(
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(12.dp))
                .background(CadPanelElevated)
                .border(
                    width = if (release.isLatestStable || release.isInstalledVersion) 1.2.dp else 1.dp,
                    color = nodeColor.copy(alpha = 0.7f),
                    shape = RoundedCornerShape(12.dp)
                )
                .padding(12.dp)
                .testTag("release_card_${release.versionCode}"),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onToggleExpand),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "v${release.versionName}",
                            style = MaterialTheme.typography.titleMedium,
                            color = CadTextPrimary,
                            fontWeight = FontWeight.Bold
                        )

                        if (release.isInstalledVersion) {
                            ReleaseStatusBadge(
                                text = "INSTALLED",
                                color = CadCyanPrimary,
                                testTag = "release_badge_installed_${release.versionCode}"
                            )
                        }
                        if (release.isLatestStable) {
                            ReleaseStatusBadge(
                                text = "LATEST",
                                color = CadEmeraldCrossing,
                                testTag = "release_badge_latest_${release.versionCode}"
                            )
                        }
                        if (release.isPrerelease) {
                            ReleaseStatusBadge(
                                text = "PRE-RELEASE",
                                color = CadAmberSnap,
                                testTag = "release_badge_prerelease_${release.versionCode}"
                            )
                        }
                    }

                    Text(
                        text = "${release.publishedDateFormatted} • Build ${release.versionCode}",
                        style = MaterialTheme.typography.labelSmall,
                        color = CadTextMuted
                    )
                }

                Icon(
                    imageVector = Icons.Default.ExpandLess,
                    contentDescription = if (isExpanded) "Collapse Release Notes" else "Expand Release Notes",
                    tint = CadTextSecondary,
                    modifier = Modifier
                        .size(20.dp)
                        .graphicsLayer { rotationZ = chevronRotation }
                )
            }

            AnimatedVisibility(
                visible = isExpanded,
                enter = expandVertically(animationSpec = tween(200)) + fadeIn(tween(150)),
                exit = shrinkVertically(animationSpec = tween(180)) + fadeOut(tween(120))
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (release.sections.isEmpty() && release.cleanMarkdownNotes.isNotBlank()) {
                        Text(
                            text = release.cleanMarkdownNotes,
                            style = MaterialTheme.typography.bodySmall,
                            color = CadTextSecondary
                        )
                    } else {
                        release.sections.forEach { section ->
                            ChangelogCategoryCard(
                                releaseVersionCode = release.versionCode,
                                section = section
                            )
                        }
                    }

                    if (release.metadata.apkFileName.isNotBlank()) {
                        val sizeText = if (release.metadata.apkSizeBytes > 0L) {
                            String.format(Locale.ROOT, " • %.2f MB", release.metadata.apkSizeBytes / (1024.0 * 1024.0))
                        } else ""
                        Text(
                            text = "APK: ${release.metadata.apkFileName}$sizeText",
                            style = MaterialTheme.typography.labelSmall,
                            fontSize = 10.sp,
                            color = CadTextMuted
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ReleaseStatusBadge(
    text: String,
    color: Color,
    testTag: String
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(5.dp))
            .background(color.copy(alpha = 0.18f))
            .border(1.dp, color, RoundedCornerShape(5.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp)
            .testTag(testTag)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            fontSize = 9.sp,
            color = color,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            softWrap = false
        )
    }
}

@Composable
private fun ChangelogCategoryCard(
    releaseVersionCode: Int,
    section: ChangelogSection
) {
    var categoryExpanded by remember { mutableIntStateOf(1) }
    val isExpanded = categoryExpanded == 1

    val (accentColor, bgTint) = when (section.category) {
        ChangelogCategory.NEW -> CadCyanPrimary to Color(0xFF0C1D29)
        ChangelogCategory.FIXES -> CadAmberSnap to Color(0xFF231B0E)
        ChangelogCategory.OPTIMISATIONS -> Color(0xFFA78BFA) to Color(0xFF1A142B)
        ChangelogCategory.SECURITY -> CadEmeraldCrossing to Color(0xFF0D241B)
        ChangelogCategory.OTHER -> CadTextSecondary to CadPanelSurface
    }

    val chevronRotation by animateFloatAsState(
        targetValue = if (isExpanded) 0f else 180f,
        animationSpec = tween(durationMillis = 180, easing = FastOutSlowInEasing),
        label = "CatChevron_${releaseVersionCode}_${section.category.name}"
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(9.dp))
            .background(bgTint)
            .border(1.dp, accentColor.copy(alpha = 0.55f), RoundedCornerShape(9.dp))
            .padding(horizontal = 10.dp, vertical = 8.dp)
            .testTag("release_category_${releaseVersionCode}_${section.category.name}"),
        verticalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { categoryExpanded = if (isExpanded) 0 else 1 },
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(7.dp)
                        .clip(CircleShape)
                        .background(accentColor)
                )
                Text(
                    text = "${section.category.displayTitle} (${section.items.size})",
                    style = MaterialTheme.typography.labelLarge,
                    color = accentColor,
                    fontWeight = FontWeight.Bold
                )
            }
            Icon(
                imageVector = Icons.Default.ExpandLess,
                contentDescription = null,
                tint = accentColor,
                modifier = Modifier
                    .size(16.dp)
                    .graphicsLayer { rotationZ = chevronRotation }
            )
        }

        AnimatedVisibility(visible = isExpanded) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                section.items.forEach { bullet ->
                    Text(
                        text = "• $bullet",
                        style = MaterialTheme.typography.bodySmall,
                        color = CadTextPrimary
                    )
                }
            }
        }
    }
}
