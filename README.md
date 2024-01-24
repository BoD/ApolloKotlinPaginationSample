# Apollo Kotlin pagination sample

A simple example of how to implement pagination with Apollo Kotlin and the normalized cache.

Uses the [GitHub GraphQL API](https://docs.github.com/en/graphql) to fetch a list of repositories
for a given user.


### "Manual" way

In the main branch is the "manual" way of doing it, where you update the cache via the
[`ApolloStore` API](https://www.apollographql.com/docs/kotlin/caching/store).

The gist of it is [here](app/src/main/java/com/example/apollokotlinpaginationsample/repository/Apollo.kt#L44).


### Incubating pagination support way

The [`incubating-pagination` branch](../../tree/incubating-pagination) shows how to use the [incubating pagination support](https://github.com/apollographql/apollo-kotlin/blob/main/design-docs/Normalized%20cache%20pagination.md#using-the-incubating-pagination-support).

You can see a the diff [here](../../compare/incubating-pagination).


### Setup

To run the app, first get a GitHub token from https://github.com/settings/tokens and update
`githubOauthKey` in `gradle.properties`.
