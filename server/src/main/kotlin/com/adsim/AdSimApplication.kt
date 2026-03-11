package com.adsim

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class AdSimApplication

fun main(args: Array<String>) {
    runApplication<AdSimApplication>(*args)
}
