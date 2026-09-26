package com.example.dualsimsms.ui

import android.content.res.ColorStateList
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.DrawableCompat
import androidx.core.view.isVisible
import androidx.core.widget.ImageViewCompat
import com.example.dualsimsms.R
import com.example.dualsimsms.databinding.ViewAvatarBinding
import kotlin.math.abs

/** Tonal contact avatars: a stable colour per name, initial or person glyph. */
object Avatars {

    private val TONES = listOf(
        R.color.avatar_bg_1 to R.color.avatar_fg_1,
        R.color.avatar_bg_2 to R.color.avatar_fg_2,
        R.color.avatar_bg_3 to R.color.avatar_fg_3,
        R.color.avatar_bg_4 to R.color.avatar_fg_4,
        R.color.avatar_bg_5 to R.color.avatar_fg_5,
        R.color.avatar_bg_6 to R.color.avatar_fg_6
    )

    fun bind(avatar: ViewAvatarBinding, displayName: String, colorKey: String = displayName) {
        val context = avatar.root.context
        val (bgRes, fgRes) = TONES[abs(colorKey.hashCode() % TONES.size)]
        val fg = ContextCompat.getColor(context, fgRes)
        avatar.root.background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(ContextCompat.getColor(context, bgRes))
        }
        // Short codes and bare numbers ("+263…", "878") get a person glyph
        // instead of a meaningless "+" or digit.
        val initial = displayName.trim().firstOrNull()?.takeIf { it.isLetter() }
        avatar.avatarInitial.isVisible = initial != null
        avatar.avatarIcon.isVisible = initial == null
        if (initial != null) {
            avatar.avatarInitial.text = initial.uppercase()
            avatar.avatarInitial.setTextColor(fg)
        } else {
            ImageViewCompat.setImageTintList(avatar.avatarIcon, ColorStateList.valueOf(fg))
        }
    }
}

/** Tints the leading dot drawable of a SIM label to that SIM's colour. */
fun TextView.setSimDot(color: Int) {
    val base = ContextCompat.getDrawable(context, R.drawable.dot) ?: return
    val dot: Drawable = DrawableCompat.wrap(base.mutate())
    DrawableCompat.setTint(dot, color)
    val current = compoundDrawablesRelative
    setCompoundDrawablesRelativeWithIntrinsicBounds(dot, current[1], current[2], current[3])
}
