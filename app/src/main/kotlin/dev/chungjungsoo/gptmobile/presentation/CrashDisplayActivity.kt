package com.lanxin.android.presentation

import android.app.Activity
import android.content.ClipData
import android.content.Context
import android.os.Bundle
import android.widget.Button
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

/**
 * 崩溃信息展示页
 * 显示详细的错误堆栈，支持一键复制
 */
class CrashDisplayActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val crashTitle = intent.getStringExtra("crash_title") ?: "💥 应用崩溃"
        val crashDevice = intent.getStringExtra("crash_device") ?: ""
        val crashTime = intent.getStringExtra("crash_time") ?: ""
        val crashStack = intent.getStringExtra("crash_stack") ?: "未知错误"

        setContentView(R.layout.activity_crash_display)

        val tvTitle = findViewById<TextView>(R.id.tvCrashTitle)
        val tvDevice = findViewById<TextView>(R.id.tvDeviceInfo)
        val tvTime = findViewById<TextView>(R.id.tvCrashTime)
        val svStack = findViewById<ScrollView>(R.id.svCrashStack)
        val tvStack = findViewById<TextView>(R.id.tvCrashStack)
        val btnCopy = findViewById<Button>(R.id.btnCopyStack)
        val btnRestart = findViewById<Button>(R.id.btnRestartApp)

        tvTitle.text = crashTitle
        tvDevice.text = crashDevice
        tvTime.text = crashTime
        tvStack.text = crashStack

        // 一键复制堆栈
        btnCopy.setOnClickListener {
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText("crash_info", crashStack)
            clipboard.setPrimaryClip(clip)
            Toast.makeText(this, "已复制到剪贴板", Toast.LENGTH_SHORT).show()
        }

        // 重启应用
        btnRestart.setOnClickListener {
            finishAffinity()
            startActivity(intent)
            System.exit(0)
        }
    }
}
