

package com.nexapp.nexmusic.models

import com.music.innertube.models.YTItem
import com.nexapp.nexmusic.db.entities.LocalItem

data class SimilarRecommendation(
    val title: LocalItem,
    val items: List<YTItem>,
)
