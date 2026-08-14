package com.hafij.islamicalarm.prayertimes

object DistrictData {
    val districts = LocationData.districts
    val divisions = LocationData.divisions
    val thanas = LocationData.thanas
    val globalCities = LocationData.globalCities
    val allLocations = LocationData.allLocations

    fun getDefaultDistrict(): District = LocationData.getDefaultDistrict()

    fun findDistrict(nameBn: String): District = LocationData.findDistrict(nameBn)

    fun search(query: String): List<District> = LocationData.search(query)

    fun findClosestLocation(lat: Double, lng: Double): District = LocationData.findClosestLocation(lat, lng)
}

