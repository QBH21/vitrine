package io.github.qbh21.vitrine

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Environment
import android.os.StatFs
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import java.io.File

/**
 * The TV's built-in showroom photos (4K JPEGs that ship for the shop demo mode on many MediaTek/MStar TVs),
 * shown in the Photos tile. TVs without them get an empty tile with a note.
 */
object Photos {
    private val dirs = listOf("/vendor/etc/store_mode/picture", "/product/etc/store_mode/picture").map(::File)

    fun files(): List<File> = dirs.flatMap { dir ->
        dir.listFiles { f -> f.name.endsWith(".jpg", ignoreCase = true) }?.toList() ?: emptyList()
    }.sortedBy { it.name }

    /** Decodes [file] at roughly [width] px wide (a 4K photo is read at 1/8 size) to keep memory low. */
    fun decode(file: File, width: Int): ImageBitmap? = try {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, bounds)
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= width) sample *= 2
        BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.RGB_565
        })?.asImageBitmap()
    } catch (e: Exception) {
        null
    }
}

object Storage {
    /** Free and total internal storage, in GB. */
    fun gb(): Pair<Double, Double> =
        StatFs(Environment.getDataDirectory().path).let { it.availableBytes / 1e9 to it.totalBytes / 1e9 }
}
