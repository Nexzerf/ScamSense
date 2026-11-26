package com.scamsense.app.ui

import android.app.Activity
import android.graphics.Bitmap
import android.graphics.Canvas
import java.io.File

/** Renders the activity's window into a PNG under app/build/screenshots/. */
internal fun Activity.saveScreenshot(name: String): Bitmap {
    val root = window.decorView.rootView
    val bmp = Bitmap.createBitmap(root.width, root.height, Bitmap.Config.ARGB_8888)
    root.draw(Canvas(bmp))
    val dir = File("build/screenshots").apply { mkdirs() }
    File(dir, "$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    return bmp
}
