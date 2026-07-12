package com.arlessas.gestion

import android.animation.ObjectAnimator
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.ComponentActivity

class SplashActivity : ComponentActivity() {

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(UiDisplayConfig.wrapContext(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = FrameLayout(this).apply {
            setBackgroundColor(getColor(R.color.splash_background))
        }

        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
        }

        val logo = ImageView(this).apply {
            setImageResource(R.drawable.logo_andes)
            scaleType = ImageView.ScaleType.FIT_CENTER
            alpha = 0f
        }
        content.addView(
            logo,
            LinearLayout.LayoutParams(
                (resources.displayMetrics.widthPixels * 0.62).toInt(),
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ).apply { bottomMargin = (resources.displayMetrics.density * 14).toInt() },
        )

        val tagline = TextView(this).apply {
            text = if (AppMode.esTallerIndependiente) "Taller y herramientas" else "Gestión de almacén"
            setTextColor(Color.argb(210, 255, 255, 255))
            textSize = 15f
            gravity = Gravity.CENTER
            letterSpacing = 0.04f
            alpha = 0f
        }
        content.addView(tagline)

        val contentParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT,
            FrameLayout.LayoutParams.WRAP_CONTENT,
        ).apply { gravity = Gravity.CENTER }
        root.addView(content, contentParams)

        val versionText = TextView(this).apply {
            text = getString(R.string.version_label, AppVersionInfo.VERSION_NAME)
            setTextColor(Color.argb(170, 255, 255, 255))
            textSize = 13f
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, (resources.displayMetrics.density * 28).toInt())
        }
        root.addView(
            versionText,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
            ).apply { gravity = Gravity.BOTTOM },
        )

        setContentView(root)

        ObjectAnimator.ofFloat(logo, View.ALPHA, 0f, 1f).apply {
            duration = 520
            interpolator = DecelerateInterpolator()
            start()
        }
        ObjectAnimator.ofFloat(tagline, View.ALPHA, 0f, 1f).apply {
            duration = 520
            startDelay = 180
            interpolator = DecelerateInterpolator()
            start()
        }

        Handler(Looper.getMainLooper()).postDelayed(
            {
                startActivity(Intent(this, MainActivity::class.java))
                overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
                finish()
            },
            1500,
        )
    }
}
