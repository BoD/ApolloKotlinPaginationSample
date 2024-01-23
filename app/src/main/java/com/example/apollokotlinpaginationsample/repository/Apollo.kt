package com.example.apollokotlinpaginationsample.repository

import com.apollographql.apollo3.ApolloClient
import com.apollographql.apollo3.api.Optional
import com.apollographql.apollo3.cache.normalized.ApolloStore
import com.apollographql.apollo3.cache.normalized.FetchPolicy
import com.apollographql.apollo3.cache.normalized.api.ConnectionMetadataGenerator
import com.apollographql.apollo3.cache.normalized.api.ConnectionRecordMerger
import com.apollographql.apollo3.cache.normalized.api.FieldPolicyApolloResolver
import com.apollographql.apollo3.cache.normalized.api.MemoryCacheFactory
import com.apollographql.apollo3.cache.normalized.api.TypePolicyCacheKeyGenerator
import com.apollographql.apollo3.cache.normalized.fetchPolicy
import com.apollographql.apollo3.cache.normalized.sql.SqlNormalizedCacheFactory
import com.apollographql.apollo3.cache.normalized.store
import com.example.apollokotlinpaginationsample.Application
import com.example.apollokotlinpaginationsample.BuildConfig
import com.example.apollokotlinpaginationsample.graphql.UserRepositoryListQuery
import com.example.apollokotlinpaginationsample.graphql.pagination.Pagination

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
        .store(
            ApolloStore(
                normalizedCacheFactory = memoryThenSqlCache,
                cacheKeyGenerator = TypePolicyCacheKeyGenerator,
                metadataGenerator = ConnectionMetadataGenerator(Pagination.connectionTypes),
                apolloResolver = FieldPolicyApolloResolver,
                recordMerger = ConnectionRecordMerger
            )
        )

        .build()
}

suspend fun fetchAndMergeNextPage() {
    // 1. Get the current list from the cache
    val listQuery = UserRepositoryListQuery(login = LOGIN)
    val cacheResponse = apolloClient.query(listQuery).fetchPolicy(FetchPolicy.CacheOnly).execute()

    // 2. Fetch the next page from the network and store it in the cache
    val after = cacheResponse.data!!.user.repositories.pageInfo.endCursor
    apolloClient.query(UserRepositoryListQuery(login = LOGIN, after = Optional.presentIfNotNull(after))).fetchPolicy(FetchPolicy.NetworkOnly).execute()
}
