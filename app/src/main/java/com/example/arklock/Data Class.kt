package com.example.arklock

import com.google.gson.annotations.SerializedName

// Update APK
data class AppUpdateResponse(
    val version: Int,
    val artifactType: ArtifactType,
    val applicationId: String,
    val variantName: String,
    val elements: List<Element>,
    val elementType: String,
    val minSdkVersionForDexing: Int
)
data class ArtifactType(
    val type: String,
    val kind: String
)
data class Element(
    val type: String,
    val filters: List<Any>,
    val attributes: List<Any>,
    val versionCode: Int,
    val versionName: String,
    val outputFile: String
)
// Update APK end
data class EmployeeResponse(
    val success: Boolean,
    val employees: List<Employee>
)
data class Employee(
    val idNumber: String,
    val firstName: String,
    val surName: String,
    val picture: String,
    val departmentId: Int,
    val languageFlag: Int? = null,
    var isFavorite: Boolean = false
)
data class PagingResponse(
    val success: Boolean,
    val message: String
)
data class DepartmentResponse(
    val success: Boolean,
    val departments: List<Department>
)
data class Department(
    val departmentId: Int,
    val departmentName: String
)
data class IdNumberRequest(val idNumber: String)
data class IdNumberResponse(val success: Boolean, val message: String?)

data class TokenRequest(val idNumbers: List<String>)

data class TokenResponse(val tokens: Map<String, String>)

data class BasicResponse(
    @SerializedName("success") val success: Boolean,
    @SerializedName("message") val message: String?
)
data class DeviceResponse(
    val success: Boolean,
    val message: String,
    val idNumber: String? = null
)
data class ManualLinkResponse(
    val success: Boolean,
    val manualLinkPH: String,
    val manualLinkJP: String
)