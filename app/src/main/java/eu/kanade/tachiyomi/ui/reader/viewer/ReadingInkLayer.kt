package eu.kanade.tachiyomi.ui.reader.viewer

import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PointF
import android.view.MotionEvent
import android.view.View
import android.widget.ImageView
import com.davemorrissey.labs.subscaleview.SubsamplingScaleImageView
import eu.kanade.domain.source.manga.interactor.GetMangaIncognitoState
import eu.kanade.tachiyomi.data.reading.ReadingPageLayout
import eu.kanade.tachiyomi.data.reading.ReadingPoint
import eu.kanade.tachiyomi.data.reading.ReadingPosition
import eu.kanade.tachiyomi.data.reading.ReadingStroke
import eu.kanade.tachiyomi.data.reading.ReadingTogetherManager
import eu.kanade.tachiyomi.data.reading.compactReadingPoints
import eu.kanade.tachiyomi.data.watch.watchHex
import eu.kanade.tachiyomi.data.watch.watchRandom
import eu.kanade.tachiyomi.ui.reader.model.ReaderPage
import eu.kanade.tachiyomi.ui.reading.readingInkColors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import tachiyomi.domain.entries.manga.model.Manga
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import kotlin.math.abs
import kotlin.math.roundToInt

/** Drawn inside the page view, so pager, webtoon scroll and zoom transform image and ink together. */
internal class ReadingInkLayer(private val view: ReaderPageImageView, private val image: () -> View?) {
    var layout = ReadingPageLayout.Full
    private var position: ReadingPosition? = null
    private var scope: CoroutineScope? = null
    private var observer: Job? = null
    private val manager get() = ReadingTogetherManager.existing()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin =
            Paint.Join.ROUND
    }
    private val path = Path()
    private val inverse = Matrix()
    private val points = ArrayList<ReadingPoint>(96)
    private var gesture = false
    private var navigation = false
    private var finger = -1
    private var cachedStrokes: List<ReadingStroke> = emptyList()
    private var cachedPoints: List<List<ReadingPoint>> = emptyList()

    fun bind(page: ReaderPage?, manga: Manga?) {
        cancelGesture()
        position = if (page != null && manga != null && !Injekt.get<GetMangaIncognitoState>().await(manga.source)) {
            ReadingPosition(
                manga.source,
                manga.url,
                page.chapter.chapter.url,
                manga.title.take(240),
                page.chapter.chapter.name.take(240),
                page.index,
                page.chapter.pages?.map { it.index }?.distinct()?.size ?: 0,
            )
                .takeIf { it.valid() }
        } else {
            null
        }
        view.invalidate()
    }

    fun attach() {
        scope?.cancel()
        val manager = manager ?: return
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        observer = scope?.launch {
            combine(manager.controller.state, manager.tools) { room, tools -> room to tools }.collect { (room, tools) ->
                if (!room.active || !room.supported || !tools.drawing || !tools.visible) cancelGesture()
                view.invalidate()
            }
        }
    }

    fun detach() {
        scope?.cancel()
        scope = null
        observer = null
        cancelGesture()
    }

    fun touch(event: MotionEvent, normal: (MotionEvent) -> Boolean): Boolean {
        val manager = manager
        val enabled =
            manager?.controller?.state?.value?.active == true &&
                manager.controller.state.value.supported &&
                manager.tools.value.drawing &&
                position != null &&
                !Injekt.get<GetMangaIncognitoState>().await(position!!.source)
        if (!enabled) {
            cancelGesture()
            return normal(event)
        }
        if (event.actionMasked == MotionEvent.ACTION_DOWN) {
            navigation = false
            val point = toOriginal(event.x, event.y) ?: return normal(event)
            gesture = true
            finger = event.getPointerId(0)
            points.clear()
            points.add(point)
            view.parent?.requestDisallowInterceptTouchEvent(true)
            view.invalidate()
            return true
        }
        if (event.actionMasked == MotionEvent.ACTION_POINTER_DOWN && gesture) {
            cancelGesture()
            navigation = true
            view.parent?.requestDisallowInterceptTouchEvent(false)
            val down = MotionEvent.obtain(event).apply { action = MotionEvent.ACTION_DOWN }
            normal(down)
            down.recycle()
        }
        if (navigation || !gesture) return normal(event)
        val index = event.findPointerIndex(finger)
        when (event.actionMasked) {
            MotionEvent.ACTION_MOVE -> if (index >= 0) {
                toOriginal(event.getX(index), event.getY(index))?.let(::appendPoint)
            }
            MotionEvent.ACTION_UP -> {
                if (index >= 0) toOriginal(event.getX(index), event.getY(index))?.let(::appendPoint)
                sendStroke()
                cancelGesture()
                view.performClick()
            }
            MotionEvent.ACTION_CANCEL -> cancelGesture()
        }
        return true
    }

    private fun appendPoint(point: ReadingPoint) {
        val last = points.lastOrNull()
        if (last != null && abs(point.x - last.x) + abs(point.y - last.y) <= 0.001f) return
        // A stacked spread has a discontinuity between its two halves in original-image coordinates.
        if (last != null && layout.stacked && (last.x < 0.5f) != (point.x < 0.5f)) {
            sendStroke()
            points.clear()
        }
        compactReadingPoints(points)
        points.add(point)
        view.invalidate()
    }

    private fun sendStroke() {
        val manager = manager ?: return
        val page = position ?: return
        if (points.isEmpty()) return
        if (points.size == 1) points.add(points.first())
        val tools = manager.tools.value
        manager.controller.draw(
            page,
            ReadingStroke(
                watchRandom(16).watchHex(),
                manager.controller.state.value.localId,
                tools.color,
                tools.width,
                points.flatMap {
                    listOf(
                        (it.x * 10000).roundToInt().coerceIn(0, 10000),
                        (it.y * 10000).roundToInt().coerceIn(0, 10000),
                    )
                },
            ),
        )
    }

    private fun cancelGesture() {
        if (!gesture && points.isEmpty()) return
        if (gesture) view.parent?.requestDisallowInterceptTouchEvent(false)
        gesture = false
        points.clear()
        view.invalidate()
    }

    fun draw(canvas: Canvas) {
        val manager = manager ?: return
        val page = position ?: return
        val room = manager.controller.state.value
        if (!room.active || !manager.tools.value.visible || image()?.isShown != true) return
        canvas.save()
        canvas.clipRect(0, 0, view.width, view.height)
        val strokes = room.strokes(page)
        if (strokes != cachedStrokes) {
            cachedStrokes = strokes
            cachedPoints = strokes.map { stroke ->
                stroke.points.chunked(2).map { ReadingPoint(it[0] / 10000f, it[1] / 10000f) }
            }
        }
        strokes.forEachIndexed { index, stroke ->
            drawLine(canvas, cachedPoints[index], stroke.color, stroke.width)
        }
        if (gesture) drawLine(canvas, points, manager.tools.value.color, manager.tools.value.width)
        canvas.restore()
    }

    private fun drawLine(canvas: Canvas, points: List<ReadingPoint>, color: Int, width: Int) {
        path.reset()
        var previous: PointF? = null
        var previousOriginal: ReadingPoint? = null
        points.forEach { point ->
            val mapped = toView(point)
            if (layout.stacked &&
                previousOriginal?.let { (it.x < 0.5f) != (point.x < 0.5f) } == true
            ) {
                previous = null
            }
            if (mapped == null) {
                previous = null
            } else {
                if (previous == null) path.moveTo(mapped.x, mapped.y) else path.lineTo(mapped.x, mapped.y)
                previous = mapped
            }
            previousOriginal = point
        }
        paint.strokeWidth = (width * 1.7f + 1f) * view.resources.displayMetrics.density
        paint.color = 0x88000000.toInt()
        val strokeWidth = paint.strokeWidth
        paint.strokeWidth = strokeWidth + 2f
        canvas.drawPath(path, paint)
        paint.strokeWidth = strokeWidth
        paint.color = readingInkColors[color]
        canvas.drawPath(path, paint)
    }

    private fun toOriginal(x: Float, y: Float): ReadingPoint? {
        val image = image() ?: return null
        val point = when (image) {
            is SubsamplingScaleImageView -> {
                if (!image.isReady || image.sWidth <= 0 || image.sHeight <= 0) return null
                val source = image.viewToSourceCoord(x - image.left, y - image.top) ?: return null
                ReadingPoint(source.x / image.sWidth, source.y / image.sHeight)
            }
            is ImageView -> {
                val drawable = image.drawable ?: return null
                if (drawable.intrinsicWidth <= 0 ||
                    drawable.intrinsicHeight <= 0 ||
                    !image.imageMatrix.invert(inverse)
                ) {
                    return null
                }
                val value = floatArrayOf(x - image.left - image.paddingLeft, y - image.top - image.paddingTop)
                inverse.mapPoints(value)
                ReadingPoint(value[0] / drawable.intrinsicWidth, value[1] / drawable.intrinsicHeight)
            }
            else -> return null
        }
        if (point.x !in 0f..1f || point.y !in 0f..1f) return null
        return layout.original(point.x, point.y)
    }

    private fun toView(point: ReadingPoint): PointF? {
        val displayed = layout.displayed(point) ?: return null
        return when (val image = image()) {
            is SubsamplingScaleImageView -> {
                if (!image.isReady) return null
                image.sourceToViewCoord(displayed.x * image.sWidth, displayed.y * image.sHeight)?.apply {
                    offset(image.left.toFloat(), image.top.toFloat())
                }
            }
            is ImageView -> {
                val drawable = image.drawable ?: return null
                val value = floatArrayOf(displayed.x * drawable.intrinsicWidth, displayed.y * drawable.intrinsicHeight)
                image.imageMatrix.mapPoints(value)
                PointF(value[0] + image.left + image.paddingLeft, value[1] + image.top + image.paddingTop)
            }
            else -> null
        }
    }
}
