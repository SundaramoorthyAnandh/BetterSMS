package com.bettersms.app.ui.theme

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ReceiptLong
import androidx.compose.material.icons.rounded.AccountBalance
import androidx.compose.material.icons.rounded.AllInbox
import androidx.compose.material.icons.rounded.GppBad
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.LocalOffer
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Person
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.bettersms.app.classify.Category

val Category.color: Color
    get() = when (this) {
        Category.PERSONAL -> Color(0xFF3B82F6)
        Category.OTP -> Color(0xFF8B5CF6)
        Category.TRANSACTIONS -> Color(0xFF10B981)
        Category.BILLS -> Color(0xFFF59E0B)
        Category.OFFERS -> Color(0xFFEC4899)
        Category.UPDATES -> Color(0xFF06B6D4)
        Category.SPAM -> Color(0xFFEF4444)
    }

val Category.icon: ImageVector
    get() = when (this) {
        Category.PERSONAL -> Icons.Rounded.Person
        Category.OTP -> Icons.Rounded.Key
        Category.TRANSACTIONS -> Icons.Rounded.AccountBalance
        Category.BILLS -> Icons.AutoMirrored.Rounded.ReceiptLong
        Category.OFFERS -> Icons.Rounded.LocalOffer
        Category.UPDATES -> Icons.Rounded.Notifications
        Category.SPAM -> Icons.Rounded.GppBad
    }

val AllInboxIcon: ImageVector get() = Icons.Rounded.AllInbox
