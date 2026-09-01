package com.example.shopapi

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class ShopApiApplication

fun main(args: Array<String>) {
    runApplication<ShopApiApplication>(*args)
}
