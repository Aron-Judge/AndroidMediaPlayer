package com.aron.mediaplayer.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@Composable
fun FastScroller(
    listState: LazyListState,
    itemCount: Int,
    modifier: Modifier = Modifier
) {
    val coroutineScope = rememberCoroutineScope()
    val density = LocalDensity.current

    var thumbPercent by remember { mutableFloatStateOf(0f) }
    var visible by remember { mutableStateOf(false) }

    // Fade animation (0f = invisible, 1f = visible)
    val alpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        label = "scrollerAlpha"
    )

    // Show scroller when scrolling
    LaunchedEffect(listState.firstVisibleItemIndex, listState.firstVisibleItemScrollOffset) {
        if (itemCount > 20 && listState.firstVisibleItemIndex > 1) {
            visible = true
            delay(1500)
            visible = false
        }
    }

    // Sync thumb with scroll
    LaunchedEffect(listState.firstVisibleItemIndex, listState.firstVisibleItemScrollOffset, itemCount) {
        val index = listState.firstVisibleItemIndex
        val offset = listState.firstVisibleItemScrollOffset
        val itemSize = listState.layoutInfo.visibleItemsInfo.firstOrNull()?.size ?: 1
        val exactPos = index + (offset / itemSize.toFloat())
        thumbPercent = (exactPos / (itemCount - 1).coerceAtLeast(1)).coerceIn(0f, 1f)
    }

    // Always take full height and align right
    Box(
        modifier = modifier
            .fillMaxHeight()
            .width(20.dp)
            .zIndex(10f),
        contentAlignment = Alignment.TopCenter
    ) {
        // Background bar
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .width(2.dp)
                .background(Color.Gray.copy(alpha = 0.2f * alpha), RoundedCornerShape(1.dp))
        )

        // Thumb
        Box(
            modifier = Modifier
                .zIndex(10f)
                .pointerInput(Unit) {}
                .offset {
                    val viewportHeightPx = listState.layoutInfo.viewportSize.height
                        .takeIf { it > 0 } ?: 1000
                    val thumbHeightPx = with(density) { 36.dp.toPx() }
                    val available = (viewportHeightPx - thumbHeightPx).coerceAtLeast(1f)
                    val yPx = thumbPercent * available
                    IntOffset(0, yPx.roundToInt())
                }
                .size(width = 8.dp, height = 36.dp)
                .background(Color.White.copy(alpha = 0.5f * alpha), RoundedCornerShape(50))
                .draggable(
                    orientation = Orientation.Vertical,
                    interactionSource = remember { MutableInteractionSource() },
                    state = rememberDraggableState { deltaY ->
                        val viewportHeightPx = listState.layoutInfo.viewportSize.height
                            .takeIf { it > 0 } ?: 1000
                        val thumbHeightPx = with(density) { 36.dp.toPx() }
                        val available = (viewportHeightPx - thumbHeightPx).coerceAtLeast(1f)

                        val deltaPercent = deltaY / available
                        val newPercent = (thumbPercent + deltaPercent).coerceIn(0f, 1f)
                        thumbPercent = newPercent

                        val targetIndex = (newPercent * (itemCount - 1)).roundToInt()
                        coroutineScope.launch {
                            listState.scrollToItem(targetIndex.coerceIn(0, itemCount - 1))
                        }
                    }
                )
        )
    }
}