package com.example.b1void.utils

import android.content.SharedPreferences
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.animation.AnimationUtils
import android.widget.SeekBar
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.b1void.R
import com.example.b1void.adapters.FileAdapter

class DynamicSeekBarManager(
    private val seekBarContainer: View,
    private val seekBar: SeekBar,
    private val recyclerView: RecyclerView,
    private val fileAdapter: FileAdapter,
    private val sharedPreferences: SharedPreferences
) {
    
    private val handler = Handler(Looper.getMainLooper())
    private val hideRunnable = Runnable { hideSeekBar() }
    private var currentProgress = 50
    
    companion object {
        private const val PREF_SEEK_BAR_PROGRESS = "dynamic_seek_bar_progress"
        private const val AUTO_HIDE_DELAY = 3000L // 3 секунды
    }
    
    init {
        setupSeekBar()
        loadSavedProgress()
    }
    
    private fun setupSeekBar() {
        seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    currentProgress = progress
                    updateGridLayout(progress)
                    fileAdapter.setProgress(progress)
                    saveProgress(progress)
                    resetAutoHideTimer()
                }
            }
            
            override fun onStartTrackingTouch(seekBar: SeekBar?) {
                // Останавливаем автоскрытие при начале взаимодействия
                handler.removeCallbacks(hideRunnable)
            }
            
            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                // Запускаем автоскрытие после окончания взаимодействия
                resetAutoHideTimer()
            }
        })
    }
    
    fun showSeekBar() {
        seekBarContainer.visibility = View.VISIBLE
        val slideIn = AnimationUtils.loadAnimation(seekBarContainer.context, android.R.anim.slide_in_left)
        seekBarContainer.startAnimation(slideIn)
        resetAutoHideTimer()
    }
    
    fun hideSeekBar() {
        val slideOut = AnimationUtils.loadAnimation(seekBarContainer.context, android.R.anim.slide_out_right)
        slideOut.setAnimationListener(object : android.view.animation.Animation.AnimationListener {
            override fun onAnimationStart(animation: android.view.animation.Animation?) {}
            override fun onAnimationRepeat(animation: android.view.animation.Animation?) {}
            override fun onAnimationEnd(animation: android.view.animation.Animation?) {
                seekBarContainer.visibility = View.GONE
            }
        })
        seekBarContainer.startAnimation(slideOut)
    }
    
    private fun resetAutoHideTimer() {
        handler.removeCallbacks(hideRunnable)
        handler.postDelayed(hideRunnable, AUTO_HIDE_DELAY)
    }
    
    private fun updateGridLayout(progress: Int) {
        val layoutManager = recyclerView.layoutManager as? GridLayoutManager
        layoutManager?.spanCount = calculateSpanCount(progress)
    }
    
    private fun calculateSpanCount(progress: Int): Int {
        val scaleFactor = 0.5f + (progress / 100f) * 0.5f
        return when {
            scaleFactor <= 0.65 -> 5
            scaleFactor >= 0.85 -> 3
            else -> 4
        }
    }
    
    private fun loadSavedProgress() {
        currentProgress = sharedPreferences.getInt(PREF_SEEK_BAR_PROGRESS, 50)
        seekBar.progress = currentProgress
        updateGridLayout(currentProgress)
        fileAdapter.setProgress(currentProgress)
    }
    
    private fun saveProgress(progress: Int) {
        sharedPreferences.edit().putInt(PREF_SEEK_BAR_PROGRESS, progress).apply()
    }
    
    fun getCurrentProgress(): Int = currentProgress
    
    fun destroy() {
        handler.removeCallbacks(hideRunnable)
    }
} 