package de.mm20.launcher2.ui.launcher.search.apps.drawer

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.lerp
import androidx.compose.ui.unit.toSize
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.android.awaitFrame
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * Drag and drop state for rearranging the app drawer in place.
 *
 * Drawer tiles live in different rows (= different lazy list items) of the search column, so
 * a tile composable is disposed when it moves to another row. That's why the gesture is
 * detected on the whole list ([drawerDragGestures]) and tiles only report their bounds
 * ([drawerDragTile]). The dragged tile is drawn as a floating copy on top of the list.
 */
@Stable
class DrawerDragState(
    private val scope: CoroutineScope,
    private val listState: LazyListState,
) {
    internal var onMove: (dragged: String, target: String) -> Unit = { _, _ -> }
    internal var onMerge: (dragged: String, target: String) -> Unit = { _, _ -> }
    internal var canMerge: (dragged: String, target: String) -> Boolean = { _, _ -> false }
    internal var canDrag: (key: String) -> Boolean = { true }
    internal var onDragStarted: (key: String) -> Unit = {}
    internal var reverse: Boolean = false

    /**
     * Distance from the top / bottom edge of the list (in px) in which auto scrolling is triggered.
     */
    internal var scrollEdgeTop: Float = 0f
    internal var scrollEdgeBottom: Float = 0f
    internal var scrollSpeed: Float = 0f

    private class Tile(val owner: Any, val bounds: Rect)

    private val tiles = HashMap<String, Tile>()

    /**
     * Position of the list in window coordinates.
     */
    var containerOffset by mutableStateOf(Offset.Zero)
        internal set
    internal var containerHeight = 0f

    var draggedKey by mutableStateOf<String?>(null)
        private set

    /**
     * Top left position of the dragged tile in window coordinates.
     */
    var draggedTilePosition by mutableStateOf(Offset.Zero)
        private set
    var draggedTileSize by mutableStateOf(Size.Zero)
        private set

    var mergeTargetKey by mutableStateOf<String?>(null)
        private set

    /**
     * Scale and alpha of the floating copy of the dragged tile (lift and drop animations).
     */
    var draggedTileScale by mutableFloatStateOf(1f)
        private set
    var draggedTileAlpha by mutableFloatStateOf(1f)
        private set

    /**
     * True while the dragged tile animates into its final position after it was released.
     */
    private var isSettling = false
    private var liftJob: Job? = null

    /**
     * Last known grid index of each tile, used to animate tiles to their new position when the
     * order changes.
     */
    internal val lastIndices = HashMap<String, Int>()

    /**
     * Size of a grid cell in px.
     */
    internal var cellSize = Size.Zero
        private set

    private var pointerOffsetInTile = Offset.Zero

    private var hoverKey: String? = null
    private var hoverJob: Job? = null
    private var scrollJob: Job? = null
    private var currentScrollDirection = 0

    internal fun registerTile(key: String, owner: Any, bounds: Rect, size: Size) {
        tiles[key] = Tile(owner, bounds)
        cellSize = size
    }

    internal fun unregisterTile(key: String, owner: Any) {
        if (tiles[key]?.owner === owner) tiles.remove(key)
    }

    private fun tileAt(position: Offset, exclude: String? = null): Pair<String, Rect>? {
        for ((key, tile) in tiles) {
            if (key != exclude && tile.bounds.contains(position)) return key to tile.bounds
        }
        return null
    }

    /**
     * @param position pointer position in local coordinates of the list
     */
    internal fun startDrag(position: Offset): Boolean {
        if (isSettling) return false
        val windowPos = position + containerOffset
        val (key, bounds) = tileAt(windowPos) ?: return false
        if (!canDrag(key)) return false
        pointerOffsetInTile = windowPos - bounds.topLeft
        draggedTileSize = bounds.size
        draggedTilePosition = bounds.topLeft
        draggedTileScale = 1f
        draggedTileAlpha = 1f
        draggedKey = key
        onDragStarted(key)
        liftJob = scope.launch {
            animate(1f, LiftScale, animationSpec = spring(stiffness = Spring.StiffnessMediumLow)) { v, _ ->
                draggedTileScale = v
            }
        }
        return true
    }

    internal fun drag(position: Offset) {
        if (draggedKey == null || isSettling) return
        draggedTilePosition = position + containerOffset - pointerOffsetInTile
        updateHover()
        updateScrolling(position.y)
    }

    internal fun endDrag() {
        val dragged = draggedKey ?: return
        if (isSettling) return
        val target = mergeTargetKey
        hoverJob?.cancel()
        hoverJob = null
        hoverKey = null
        stopScrolling()
        liftJob?.cancel()
        isSettling = true

        // Capture the destination before the layout changes
        val targetBounds = target?.let { tiles[it]?.bounds }
        if (target != null) onMerge(dragged, target)

        scope.launch {
            try {
                val start = draggedTilePosition
                val startScale = draggedTileScale
                if (targetBounds != null) {
                    // Shrink into the folder
                    val end = targetBounds.center - Offset(draggedTileSize.width / 2f, draggedTileSize.height / 2f)
                    animate(0f, 1f, animationSpec = tween(220, easing = FastOutSlowInEasing)) { f, _ ->
                        draggedTilePosition = lerp(start, end, f)
                        draggedTileScale = startScale + (0.4f - startScale) * f
                        draggedTileAlpha = 1f - f
                    }
                } else {
                    // Glide into the slot. It might not be visible anymore, then just drop it.
                    val end = tiles[dragged]?.bounds?.topLeft ?: return@launch
                    animate(0f, 1f, animationSpec = spring(stiffness = Spring.StiffnessMediumLow)) { f, _ ->
                        draggedTilePosition = lerp(start, end, f)
                        draggedTileScale = startScale + (1f - startScale) * f
                    }
                }
            } finally {
                isSettling = false
                reset()
            }
        }
    }

    internal fun cancelDrag() {
        if (isSettling) return
        liftJob?.cancel()
        reset()
    }

    private fun reset() {
        hoverJob?.cancel()
        hoverJob = null
        hoverKey = null
        stopScrolling()
        mergeTargetKey = null
        draggedKey = null
        draggedTileScale = 1f
        draggedTileAlpha = 1f
    }

    private fun updateHover() {
        val dragged = draggedKey ?: return
        val center = draggedTilePosition + Offset(
            draggedTileSize.width / 2f,
            draggedTileSize.height / 2f
        )
        val (target, bounds) = tileAt(center, exclude = dragged) ?: run {
            hoverJob?.cancel()
            hoverKey = null
            mergeTargetKey = null
            return
        }
        val rel = center - bounds.center
        val inCenter = abs(rel.x) < bounds.width * 0.3f && abs(rel.y) < bounds.height * 0.3f

        if (inCenter && canMerge(dragged, target)) {
            hoverJob?.cancel()
            hoverKey = null
            mergeTargetKey = target
            return
        }
        mergeTargetKey = null
        if (hoverKey == target) return
        hoverJob?.cancel()
        hoverKey = target
        hoverJob = scope.launch {
            delay(250)
            if (draggedKey == dragged && mergeTargetKey == null) {
                onMove(dragged, target)
            }
            hoverKey = null
        }
    }

    private fun updateScrolling(y: Float) {
        val direction = when {
            y < scrollEdgeTop -> -1
            y > containerHeight - scrollEdgeBottom -> 1
            else -> 0
        }
        if (direction == currentScrollDirection) return
        stopScrolling()
        if (direction == 0) return
        currentScrollDirection = direction
        // In a reversed layout, scrolling forward moves the content down
        val delta = scrollSpeed * direction * (if (reverse) -1 else 1)
        scrollJob = scope.launch {
            delay(300)
            var lastFrame = awaitFrame()
            while (isActive) {
                val frame = awaitFrame()
                listState.scrollBy(delta * (frame - lastFrame) / 1_000_000_000f)
                lastFrame = frame
                updateHover()
            }
        }
    }

    private fun stopScrolling() {
        currentScrollDirection = 0
        scrollJob?.cancel()
        scrollJob = null
    }
}

@Composable
fun rememberDrawerDragState(listState: LazyListState): DrawerDragState {
    val scope = rememberCoroutineScope()
    return remember(listState) { DrawerDragState(scope, listState) }
}

val LocalDrawerDragState = staticCompositionLocalOf<DrawerDragState?> { null }

/**
 * Report the bounds of a drawer tile to the drag state.
 */
fun Modifier.drawerDragTile(state: DrawerDragState?, key: String): Modifier {
    if (state == null) return this
    return composed {
        val owner = remember { Any() }
        DisposableEffect(state, key) {
            onDispose { state.unregisterTile(key, owner) }
        }
        onGloballyPositioned {
            state.registerTile(key, owner, it.boundsInWindow(), it.size.toSize())
        }
    }
}

/**
 * Long press and drag gesture for the list that contains the drawer tiles.
 * Events are observed in the initial pass so that, once a drag has started, scrolling and
 * clicks of the underlying items can be suppressed by consuming the events.
 */
fun Modifier.drawerDragGestures(
    state: DrawerDragState,
    enabled: Boolean,
    hapticFeedback: HapticFeedback,
): Modifier = composed {
    val isEnabled by rememberUpdatedState(enabled)
    this
        .onGloballyPositioned {
            state.containerOffset = it.positionInWindow()
            state.containerHeight = it.size.height.toFloat()
        }
        .pointerInput(state) {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                if (!isEnabled) return@awaitEachGesture
                val isLongPress = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
                    awaitReleaseOrSlop(down.id, down.position, viewConfiguration.touchSlop)
                } == null
                if (!isLongPress || !isEnabled) return@awaitEachGesture
                if (!state.startDrag(down.position)) return@awaitEachGesture
                hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                var ended = false
                try {
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        change.consume()
                        if (!change.pressed) {
                            state.endDrag()
                            ended = true
                            break
                        }
                        state.drag(change.position)
                    }
                } finally {
                    if (!ended) state.cancelDrag()
                }
            }
        }
}

/**
 * Suspends until the pointer is released or moved further than [slop].
 */
private suspend fun AwaitPointerEventScope.awaitReleaseOrSlop(
    pointerId: PointerId,
    start: Offset,
    slop: Float,
) {
    while (true) {
        val event = awaitPointerEvent(PointerEventPass.Initial)
        val change = event.changes.firstOrNull { it.id == pointerId } ?: return
        if (!change.pressed) return
        if (change.isConsumed) return
        if ((change.position - start).getDistance() > slop) return
    }
}

private const val LiftScale = 1.12f
