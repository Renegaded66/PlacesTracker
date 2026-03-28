package com.d_drostes_apps.placestracker.ui.gallery

sealed class GalleryItem {
    data class Header(val date: String) : GalleryItem()
    data class Photo(
        val id: Long,
        val uri: String,
        val date: Long,
        val latitude: Double?,
        val longitude: Double?
    ) : GalleryItem()
}
