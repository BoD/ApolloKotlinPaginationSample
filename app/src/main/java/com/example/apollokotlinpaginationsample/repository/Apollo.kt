package com.example.apollokotlinpaginationsample.repository

import com.apollographql.apollo3.ApolloClient
import com.apollographql.apollo3.api.Optional
import com.apollographql.apollo3.cache.normalized.FetchPolicy
import com.apollographql.apollo3.cache.normalized.api.MemoryCacheFactory
import com.apollographql.apollo3.cache.normalized.apolloStore
import com.apollographql.apollo3.cache.normalized.fetchPolicy
import com.apollographql.apollo3.cache.normalized.normalizedCache
import com.apollographql.apollo3.cache.normalized.sql.SqlNormalizedCacheFactory
import com.example.apollokotlinpaginationsample.Application
import com.example.apollokotlinpaginationsample.BuildConfig
import com.example.apollokotlinpaginationsample.graphql.UserRepositoryListQuery
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val SERVER_URL = "https://api.github.com/graphql"

private const val HEADER_AUTHORIZATION = "Authorization"
private const val HEADER_AUTHORIZATION_BEARER = "Bearer"

const val LOGIN = "bod"

val apolloClient: ApolloClient by lazy {
    val memoryCache = MemoryCacheFactory(maxSizeBytes = 5 * 1024 * 1024)
    val sqlCache = SqlNormalizedCacheFactory(Application.applicationContext, "app.db")
    val memoryThenSqlCache = memoryCache.chain(sqlCache)

    ApolloClient.Builder()
        .serverUrl(SERVER_URL)

        // Add headers for authentication
        .addHttpHeader(
            HEADER_AUTHORIZATION,
            "$HEADER_AUTHORIZATION_BEARER ${BuildConfig.GITHUB_OAUTH_KEY}"
        )

        // Normalized cache
        .normalizedCache(memoryThenSqlCache)

        .build()
}

suspend fun fetchAndMergeNextPage() {
    // 1. Get the current list from the cache
    val listQuery = UserRepositoryListQuery(login = LOGIN)
    val cacheResponse = apolloClient.query(listQuery).fetchPolicy(FetchPolicy.CacheOnly).execute()

    // 2. Fetch the next page from the network (don't update the cache yet)
    val after = cacheResponse.data!!.user.repositories.pageInfo.endCursor
    val networkResponse = apolloClient.query(UserRepositoryListQuery(login = LOGIN, after = Optional.presentIfNotNull(after))).fetchPolicy(FetchPolicy.NetworkOnly).execute()

    // 3. Merge the next page with the current list
    val mergedList = cacheResponse.data!!.user.repositories.nodes!! + networkResponse.data!!.user.repositories.nodes!!
    val dataWithMergedList = networkResponse.data!!.copy(
        user = networkResponse.data!!.user.copy(
            repositories = networkResponse.data!!.user.repositories.copy(
                pageInfo = networkResponse.data!!.user.repositories.pageInfo,
                nodes = mergedList
            )
        )
    )

    // 4. Update the cache with the merged list
    withContext(Dispatchers.IO) {
        apolloClient.apolloStore.writeOperation(operation = listQuery, operationData = dataWithMergedList)
    }
}
