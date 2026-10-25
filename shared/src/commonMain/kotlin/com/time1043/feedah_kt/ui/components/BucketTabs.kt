package com.time1043.feedah_kt.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import com.time1043.feedah_kt.data.db.BucketInfo
import com.time1043.feedah_kt.ui.theme.FontSize
import com.time1043.feedah_kt.ui.theme.LocalFeedahColors
import com.time1043.feedah_kt.ui.theme.Radius
import com.time1043.feedah_kt.ui.theme.Spacing

/**
 * The shared bucket chip row. Every screen renders it as the first element
 * with no extra top padding of its own, so the row sits at exactly the same
 * position everywhere and never shifts between screens.
 */
@Composable
fun BucketTabs(
    buckets: List<BucketInfo>,
    activeId: String,
    onSelect: (String) -> Unit,
) {
    val colors = LocalFeedahColors.current
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.m)
            .padding(top = Spacing.s),
        horizontalArrangement = Arrangement.spacedBy(Spacing.s),
    ) {
        for (bucket in buckets) {
            val active = bucket.id == activeId
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(Radius.l))
                    .background(if (active) colors.accent else colors.surface)
                    .clickable { onSelect(bucket.id) }
                    .padding(horizontal = Spacing.m, vertical = Spacing.s),
            ) {
                Text(
                    bucket.id,
                    color = if (active) Color.White else colors.textSecondary,
                    fontSize = FontSize.caption,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

