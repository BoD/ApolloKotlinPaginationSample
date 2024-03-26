package com.example.apollokotlinpaginationsample.repository

interface HasNodes<D, N> {
    fun getNodes(): List<N>
    fun updateNodes(nodes: List<N>): D
}
