package com.axzae.homeassistant.provider

import com.axzae.homeassistant.model.Entity
import com.axzae.homeassistant.model.LogSheet
import com.axzae.homeassistant.model.rest.BootstrapResponse
import com.axzae.homeassistant.model.rest.CallServiceRequest
import retrofit2.Call
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query
import java.util.ArrayList

interface RestAPIService {
    @GET("/api/bootstrap")
    fun bootstrap(@Header("Authorization") password: String?): Call<BootstrapResponse?>?

    @GET("/api/bootstrap")
    fun rawBootstrap(@Header("Authorization") password: String?): Call<String?>?

    @GET("/api/states")
    fun rawStates(@Header("Authorization") password: String?): Call<String?>?

    @GET("/api/states")
    fun getStates(@Header("Authorization") password: String?): Call<ArrayList<Entity?>?>?

    @GET("/api/states/{entityId}")
    fun getState(@Header("Authorization") password: String?, @Path("entityId") entityId: String?): Call<Entity?>?

    @POST("/api/services/{domain}/{service}")
    fun callService(
        @Header("Authorization") password: String?,
        @Path("domain") domain: String?,
        @Path("service") service: String?,
        @Body json: CallServiceRequest?
    ): Call<ArrayList<Entity?>?>?

    @GET("/api/history/period")
    fun getHistory(
        @Header("Authorization") password: String?,
        @Query("filter_entity_id") entityId: String?
    ): Call<ArrayList<ArrayList<Entity?>?>?>?

    @GET("/api/logbook/{timestamp}")
    fun getLogbook(
        @Header("Authorization") password: String?,
        @Path("timestamp") domain: String?
    ): Call<ArrayList<LogSheet?>?>? //    @GET("api/settings/all")
    //    Call<RetrieveSettingsResponse> getSettings(
    //            @Header("Authorization") String token,
    //            @Query("appBuild") String appBuild,
    //            @Query("appVersion") String appVersion,
    //            @Query("lovVersion") String lovVersion,
    //            @Query("imei") String imei,
    //            @Query("model") String model,
    //            @Query("deviceos") String deviceOs,
    //            @Query("lat") String latitude,
    //            @Query("long") String longitude
    //    );
}