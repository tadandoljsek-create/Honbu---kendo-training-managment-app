package com.honbu.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

val HonbuTypography = Typography(
    displayLarge = TextStyle(fontWeight = FontWeight.Bold,    fontSize = 72.sp, letterSpacing = (-1).sp),
    headlineLarge  = TextStyle(fontWeight = FontWeight.Bold,    fontSize = 32.sp),
    headlineMedium = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 24.sp),
    titleLarge   = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 20.sp),
    titleMedium  = TextStyle(fontWeight = FontWeight.Medium,   fontSize = 16.sp),
    bodyLarge    = TextStyle(fontWeight = FontWeight.Normal,   fontSize = 16.sp),
    bodyMedium   = TextStyle(fontWeight = FontWeight.Normal,   fontSize = 14.sp),
    labelLarge   = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 14.sp),
    labelMedium  = TextStyle(fontWeight = FontWeight.Medium,   fontSize = 12.sp),
)
