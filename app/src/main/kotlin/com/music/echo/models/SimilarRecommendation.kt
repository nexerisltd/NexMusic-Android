

package com.nexapp.nexpass.models

import com.music.innertube.models.YTItem
import com.nexapp.nexpass.db.entities.LocalItem

data class SimilarRecommendation(
    val title: LocalItem,
    val items: List<YTItem>,
)
