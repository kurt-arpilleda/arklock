package com.example.arklock

import retrofit2.Call
import retrofit2.http.GET

interface ApiService {
    @GET("V4/Others/Kurt/LatestVersionAPK/ArkLock/output-metadata.json")
    fun getAppUpdateDetails(): Call<AppUpdateResponse>
}
