package com.afollestad.iconrequest

import android.content.Context
import com.afollestad.iconrequest.extensions.closeQuietly
import com.afollestad.iconrequest.extensions.log
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader
import java.util.HashSet

/** @author Aidan Follestad (afollestad) */
internal class AppFilterAssets(private val context: Context) : AppFilterSource {

  @Throws(Exception::class)
  override fun load(
    filterName: String,
    errorOnInvalidDrawables: Boolean
  ): HashSet<String>? {
    val defined = HashSet<String>()
    if (filterName.isEmpty()) {
      return defined
    }

    val inputStream: InputStream
    try {
      val am = context.assets
      "Loading your appfilter, opening: $filterName".log(TAG)
      inputStream = am.open(filterName)
    } catch (e: Throwable) {
      throw Exception("Failed to open $filterName", e)
    }

    val reader = BufferedReader(InputStreamReader(inputStream))
    var invalidDrawables: StringBuilder? = null

    try {
      val itemEndStr = "/>"
      val componentStartStr = "component=\"ComponentInfo"
      val drawableStartStr = "drawable=\""
      val endStr = "\""
      val commentStart = "<!--"
      val commentEnd = "-->"

      var component: String? = null
      var drawable: String? = null

      var inComment = false

      var line = reader.readLine()
      while (line != null) {
        val trimmedLine = line.trim { it <= ' ' }
        if (!inComment && trimmedLine.startsWith(commentStart)) {
          inComment = true
        }
        if (inComment && trimmedLine.endsWith(commentEnd)) {
          inComment = false
          continue
        }

        if (inComment) continue
        var start: Int = line.indexOf(componentStartStr)
        var end: Int

        if (start != -1) {
          start += componentStartStr.length
          end = line.indexOf(endStr, start)
          var ci = line.substring(start, end)
          if (ci.startsWith("{")) ci = ci.substring(1)
          if (ci.endsWith("}")) ci = ci.substring(0, ci.length - 1)
          component = ci
        }

        start = line.indexOf(drawableStartStr)
        if (start != -1) {
          start += drawableStartStr.length
          end = line.indexOf(endStr, start)
          drawable = line.substring(start, end)
        }

        start = line.indexOf(itemEndStr)
        if (start != -1 && (component != null || drawable != null)) {
          "Found: $component ($drawable)".log(TAG)
          if (drawable == null || drawable.trim { it <= ' ' }.isEmpty()) {
            "WARNING: Drawable shouldn't be null.".log(TAG)
            if (errorOnInvalidDrawables) {
              if (invalidDrawables == null) {
                invalidDrawables = StringBuilder()
              }
              if (invalidDrawables.isNotEmpty()) {
                invalidDrawables.append("\n")
              }
              invalidDrawables.append("Drawable for $component was null or empty.\n")
            }
          } else {
            val r = context.resources
            var identifier: Int
            identifier = try {
              r.getIdentifier(drawable, "drawable", context.packageName)
            } catch (t: Throwable) {
              0
            }

            if (identifier == 0) {
              "WARNING: Drawable $drawable (for $component) doesn't match up with a resource.".log(
                  TAG
              )
              if (errorOnInvalidDrawables) {
                if (invalidDrawables == null) {
                  invalidDrawables = StringBuilder()
                }
                if (invalidDrawables.isNotEmpty()) {
                  invalidDrawables.append("\n")
                }
                invalidDrawables.append(
                    "Drawable $drawable (for $component) doesn't match up with a resource.\n"
                )
              }
            }
          }
          defined.add(component!!)
        }
        line = reader.readLine()
      }

      if (invalidDrawables != null && invalidDrawables.isNotEmpty()) {
        invalidDrawables.setLength(0)
        invalidDrawables.trimToSize()
        throw Exception(invalidDrawables.toString())
      }
      "Found ${defined.size} total app(s) in your appfilter.".log(TAG)
    } catch (e: Throwable) {
      throw Exception("Failed to read $filterName", e)
    } finally {
      reader.closeQuietly()
      inputStream.closeQuietly()
    }

    return defined
  }

  companion object {
    private const val TAG = "AppFilterAssets"
  }
}
