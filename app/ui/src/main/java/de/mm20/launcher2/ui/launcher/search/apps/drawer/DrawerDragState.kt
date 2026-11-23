package de.mm20.launcher2.ui.launcher.search.apps.drawer

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
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

    private var pointerOffsetInTile = Offset.Zero

    private var hoverKey: String? = null
    private var hoverJob: Job? = null
    private var scrollJob: Job? = null
    private var currentScrollDirection = 0

    internal fun registerTile(key: String, owner: Any, bounds: Rect) {
        tiles[key] = Tile(owner, bounds)
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
        val windowPos = position + containerOffset
        val (key, bounds) = tileAt(windowPos) ?: return false
        if (!canDrag(key)) return false
        pointerOffsetInTile = windowPos - bounds.topLeft
        draggedTileSize = bounds.size
        draggedTilePosition = bounds.topLeft
        draggedKey = key
        onDragStarted(key)
        return true
    }

    internal fun drag(position: Offset) {
        if (draggedKey == null) return
        draggedTilePosition = position + containerOffset - pointerOffsetInTile
        updateHover()
        updateScrolling(position.y)
    }

    internal fun endDrag() {
        val dragged = draggedKey
        val target = mergeTargetKey
        if (dragged != null && target != null) {
            onMerge(dragged, target)
        }
        reset()
    }

    internal fun cancelDrag() {
        reset()
    }

    private fun reset() {
        hoverJob?.cancel()
        hoverJob = null
        hoverKey = null
        stopScrolling()
        mergeTargetKey = null
        draggedKey = null
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
            state.registerTile(key, owner, it.boundsInWindow())
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
