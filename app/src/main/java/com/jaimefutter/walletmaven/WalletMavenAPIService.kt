package com.jaimefutter.walletmaven

import retrofit2.Call
import retrofit2.http.*

data class ApiResponse(val message: String)

interface WalletMavenAPIService {
    @POST("add-expense")
    fun addExpense(@Body expense: ExpenseEntity): Call<ApiResponse>

    @GET("get-expenses/{userID}")
    fun getExpenses(@Path("userID") userID: String): Call<ExpenseResponse>

    @PUT("update-expense/{id}")
    fun updateExpense(@Path("id") id: String, @Body expense: ExpenseEntity): Call<ApiResponse>

    @DELETE("delete-expense/{id}/{userID}")
    fun deleteExpense(@Path("id") id: String, @Path("userID") userID: String): Call<ApiResponse>

    @POST("categories/add")
    fun addCategory(@Body category: CategoryEntity): Call<ApiResponse>

    @GET("get-categories/{userID}")
    fun getCategories(@Path("userID") userID: String): Call<CategoryResponse>

    @DELETE("delete-category/{categoryName}/{userID}")
    fun deleteCategory(@Path("categoryName") categoryName: String, @Path("userID") userID: String): Call<ApiResponse>

}