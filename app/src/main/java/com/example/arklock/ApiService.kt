package com.example.arklock

import retrofit2.Call
import retrofit2.http.Field
import retrofit2.http.FormUrlEncoded
import retrofit2.http.GET
import retrofit2.http.POST

interface ApiService {
    @GET("V4/Others/Kurt/LatestVersionAPK/ArkLock/output-metadata.json")
    fun getAppUpdateDetails(): Call<AppUpdateResponse>
    @FormUrlEncoded
    @POST("V4/Others/Kurt/PagingAPI/kurt_sendPaging.php")
    fun sendPaging(
        @Field("idNumbers") idNumbers: String,
        @Field("location") location: String,
        @Field("requestPerson") requestPerson: String,
        @Field("language") language: String  // New parameter
    ): Call<PagingResponse>
}
