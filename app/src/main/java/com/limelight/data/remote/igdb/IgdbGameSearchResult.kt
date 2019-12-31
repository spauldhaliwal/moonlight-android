package com.limelight.data.remote.igdb

data class IgdbGameSearchResult (
    val result: List<IgdbGameModel>
)

data class IgdbGameModel (
    val id : Int,
    val age_ratings : List<Int>,
    val artworks : List<Int>,
    val cover : Int,
    val first_release_date : Int,
    val genres : List<Int>,
    val involved_companies : List<Int>,
    val name : String,
    val platforms : List<Int>,
    val popularity : Double,
    val screenshots : List<Int>,
    val summary : String,
    val total_rating : Double,
    val total_rating_count : Int
)