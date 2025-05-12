package com.example.campusnavigator.api

import com.example.campusnavigator.api.models.Endpoint
import retrofit2.Response
import retrofit2.http.Field
import retrofit2.http.FormUrlEncoded
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

interface ApiService {
    @GET("endpoint-autocomplete/")
    suspend fun getEndpoints(@Query("q") query: String): List<Endpoint>

    @FormUrlEncoded
    @POST("find-route/")
    suspend fun findRoute(
        @Field("start") startNodeId: Int,
        @Field("end") endNodeId: Int
    ): Response<Map<String, Any>>

    @GET("floor-route/{floor_number}/")
    suspend fun getFloorRoute(
        @Path("floor_number") floorNumber: Int,
        @Query("route") routeJson: String
    ): Response<Map<String, Any>>

    @GET("floor-svg/{floor_number}/")
    suspend fun getFloorSvg(@Path("floor_number") floorNumber: Int): Response<String>

    @GET("floors/")
    suspend fun getFloors(): List<Int>
}