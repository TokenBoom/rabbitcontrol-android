package io.rabbitcontrol.android.ui.session

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.rounded.Mouse
import androidx.compose.material.icons.rounded.ScreenRotation
import androidx.compose.material.icons.rounded.SwipeVertical
import androidx.compose.material.icons.rounded.TouchApp
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.ZoomOutMap
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.rabbitcontrol.android.R
import io.rabbitcontrol.android.ui.theme.SessionPalette

@Composable
fun SessionGestureGuide(
    isPortrait: Boolean,
    onFinish: () -> Unit,
) {
    var step by remember { mutableIntStateOf(0) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xD8000000))
            .navigationBarsPadding()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = {},
            ),
    ) {
        when (step) {
            0 -> GestureStep1Content(isPortrait = isPortrait)
            1 -> GestureStep2Content(isPortrait = isPortrait)
            else -> GestureStep3Content(isPortrait = isPortrait)
        }

        // Bottom pill navigation matching UU Remote onboarding
        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 26.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (step > 0) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(Color(0xCC383838))
                        .clickable { step -= 1 },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowLeft,
                        contentDescription = stringResource(R.string.guide_prev),
                        tint = Color.White,
                        modifier = Modifier.size(22.dp),
                    )
                }
            }
            Box(
                modifier = Modifier
                    .height(44.dp)
                    .clip(RoundedCornerShape(22.dp))
                    .background(Color(0xCC383838))
                    .clickable {
                        if (step < 2) step += 1 else onFinish()
                    }
                    .padding(horizontal = 28.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = when (step) {
                        0 -> stringResource(R.string.guide_next_1)
                        1 -> stringResource(R.string.guide_next_2)
                        else -> stringResource(R.string.guide_finish)
                    },
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                )
            }
        }
    }
}

@Composable
private fun GestureStep1Content(isPortrait: Boolean) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = if (isPortrait) 24.dp else 36.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = stringResource(R.string.guide_title_mouse),
            color = Color.White,
            fontSize = 19.sp,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(modifier = Modifier.height(if (isPortrait) 20.dp else 28.dp))
        if (isPortrait) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                PortraitGestureGuideCard(
                    icon = Icons.Rounded.TouchApp,
                    title = stringResource(R.string.guide_drag_title),
                    subtitle = stringResource(R.string.guide_drag_sub_portrait),
                )
                PortraitGestureGuideCard(
                    icon = Icons.Rounded.ZoomOutMap,
                    title = stringResource(R.string.guide_zoom_title),
                    subtitle = stringResource(R.string.guide_zoom_sub_portrait),
                )
                PortraitGestureGuideCard(
                    icon = Icons.Rounded.SwipeVertical,
                    title = stringResource(R.string.guide_scroll_title),
                    subtitle = stringResource(R.string.guide_scroll_sub_portrait),
                )
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                GestureGuideTile(
                    icon = Icons.Rounded.TouchApp,
                    title = stringResource(R.string.guide_drag_title),
                    subtitle = stringResource(R.string.guide_drag_sub_landscape),
                )
                GestureGuideTile(
                    icon = Icons.Rounded.ZoomOutMap,
                    title = stringResource(R.string.guide_zoom_title),
                    subtitle = stringResource(R.string.guide_zoom_sub_landscape),
                )
                GestureGuideTile(
                    icon = Icons.Rounded.SwipeVertical,
                    title = stringResource(R.string.guide_scroll_title_short),
                    subtitle = stringResource(R.string.guide_scroll_sub_landscape),
                )
            }
        }
        Spacer(modifier = Modifier.height(if (isPortrait) 72.dp else 44.dp))
    }
}

@Composable
private fun PortraitGestureGuideCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Color(0x26FFFFFF))
            .border(1.dp, Color(0x38FFFFFF), RoundedCornerShape(14.dp))
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(46.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0x33FFFFFF)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(24.dp))
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            Spacer(modifier = Modifier.height(3.dp))
            Text(subtitle, color = SessionPalette.muted, fontSize = 12.sp, lineHeight = 16.sp)
        }
    }
}

@Composable
private fun GestureGuideTile(
    icon: ImageVector,
    title: String,
    subtitle: String,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.width(150.dp),
    ) {
        Box(
            modifier = Modifier
                .size(66.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(Color(0x33FFFFFF))
                .border(1.dp, Color(0x40FFFFFF), RoundedCornerShape(16.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(32.dp))
        }
        Spacer(modifier = Modifier.height(12.dp))
        Text(title, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            subtitle,
            color = SessionPalette.muted,
            fontSize = 11.sp,
            textAlign = TextAlign.Center,
            lineHeight = 15.sp,
        )
    }
}

@Composable
private fun GestureStep2Content(isPortrait: Boolean) {
    if (isPortrait) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp),
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = stringResource(R.string.guide_title_portrait_bar),
                color = Color.White,
                fontSize = 19.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(modifier = Modifier.height(18.dp))
            PortraitGestureGuideCard(
                icon = Icons.Rounded.Mouse,
                title = stringResource(R.string.session_right_hold),
                subtitle = stringResource(R.string.guide_p_right_sub),
            )
            Spacer(modifier = Modifier.height(10.dp))
            PortraitGestureGuideCard(
                icon = Icons.Rounded.TouchApp,
                title = stringResource(R.string.guide_p_split_title),
                subtitle = stringResource(R.string.guide_p_split_sub),
            )
            Spacer(modifier = Modifier.height(10.dp))
            PortraitGestureGuideCard(
                icon = Icons.Rounded.ScreenRotation,
                title = stringResource(R.string.guide_p_orient_title),
                subtitle = stringResource(R.string.guide_p_orient_sub),
            )
            Spacer(modifier = Modifier.height(72.dp))
        }
        return
    }
    Row(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.displayCutout.only(WindowInsetsSides.Start)),
    ) {
        // Highlighted 3 left thumb zones
        Column(
            modifier = Modifier
                .width(68.dp)
                .fillMaxHeight(),
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .background(Color(0x553370FF)),
            )
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .background(Color(0x5510B981)),
            )
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .background(Color(0x55D97706)),
            )
        }

        // Callout descriptions
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight(),
        ) {
            GuideCalloutRow(
                dotColor = Color(0xFF6BA1FF),
                title = stringResource(R.string.guide_l_right_title),
                desc = stringResource(R.string.guide_l_right_desc),
                modifier = Modifier.weight(1f),
            )
            GuideCalloutRow(
                dotColor = Color(0xFF4ADE80),
                title = stringResource(R.string.guide_l_drag_title),
                desc = stringResource(R.string.guide_l_drag_desc),
                modifier = Modifier.weight(1f),
            )
            GuideCalloutRow(
                dotColor = Color(0xFFFBBF24),
                title = stringResource(R.string.guide_l_slot_title),
                desc = stringResource(R.string.guide_l_slot_desc),
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun GuideCalloutRow(
    dotColor: Color,
    title: String,
    desc: String,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .width(42.dp)
                .height(2.dp)
                .background(dotColor),
        )
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(dotColor),
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column {
            Text(title, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            Spacer(modifier = Modifier.height(3.dp))
            Text(desc, color = SessionPalette.muted, fontSize = 12.sp)
        }
    }
}

@Composable
private fun GestureStep3Content(isPortrait: Boolean) {
    if (isPortrait) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(Color(0x553370FF))
                    .border(1.dp, Color(0xFF6BA1FF), RoundedCornerShape(18.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Rounded.Tune, contentDescription = null, tint = Color.White, modifier = Modifier.size(30.dp))
            }
            Spacer(modifier = Modifier.height(18.dp))
            Text(
                text = stringResource(R.string.guide_title_cp_portrait),
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.guide_cp_portrait_desc),
                color = SessionPalette.muted,
                fontSize = 13.sp,
                textAlign = TextAlign.Center,
                lineHeight = 19.sp,
            )
            Spacer(modifier = Modifier.height(72.dp))
        }
        return
    }
    Row(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.displayCutout.only(WindowInsetsSides.End)),
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight(),
            horizontalAlignment = Alignment.End,
        ) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.End,
            ) {
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        stringResource(R.string.guide_title_cp_landscape),
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        stringResource(R.string.guide_cp_landscape_desc),
                        color = SessionPalette.muted,
                        fontSize = 12.sp,
                        textAlign = TextAlign.End,
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF6BA1FF)),
                )
                Box(
                    modifier = Modifier
                        .width(42.dp)
                        .height(2.dp)
                        .background(Color(0xFF6BA1FF)),
                )
            }
            Spacer(modifier = Modifier.weight(3f))
        }

        Column(
            modifier = Modifier
                .width(68.dp)
                .fillMaxHeight(),
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .background(Color(0x553370FF)),
            )
            Spacer(modifier = Modifier.weight(3f))
        }
    }
}
