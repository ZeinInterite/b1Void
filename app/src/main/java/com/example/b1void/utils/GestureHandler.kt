package com.example.b1void.utils

import android.view.GestureDetector
import android.view.MotionEvent
import androidx.recyclerview.widget.RecyclerView
import com.example.b1void.adapters.FileAdapter
import java.io.File

class GestureHandler(
    private val recyclerView: RecyclerView,
    private val fileAdapter: FileAdapter,
    private val onFileSelectionToggle: (File) -> Unit,
    private val onSelectionModeStart: () -> Unit
) {
    
    private var isSwipeSelectionActive = false
    private var lastTouchedPosition = -1
    
    private val gestureDetector = GestureDetector(recyclerView.context, object : GestureDetector.SimpleOnGestureListener() {
        override fun onScroll(
            e1: MotionEvent?,
            e2: MotionEvent,
            distanceX: Float,
            distanceY: Float
        ): Boolean {
            if (isSwipeSelectionActive) {
                val childView = recyclerView.findChildViewUnder(e2.x, e2.y)
                if (childView != null) {
                    val position = recyclerView.getChildAdapterPosition(childView)
                    if (position != RecyclerView.NO_POSITION && position != lastTouchedPosition) {
                        val file = fileAdapter.files[position]
                        onFileSelectionToggle(file)
                        lastTouchedPosition = position
                    }
                }
                return true
            }
            return false
        }

        override fun onLongPress(e: MotionEvent) {
            onSelectionModeStart()
        }
    })
    
    fun startSwipeSelection() {
        isSwipeSelectionActive = true
        lastTouchedPosition = -1
    }
    
    fun stopSwipeSelection() {
        isSwipeSelectionActive = false
        lastTouchedPosition = -1
    }
    
    fun onTouchEvent(event: MotionEvent): Boolean {
        return gestureDetector.onTouchEvent(event)
    }
} 