package com.adsim

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.ConfigurationPropertiesScan
import org.springframework.boot.runApplication

@SpringBootApplication
@ConfigurationPropertiesScan
class AdSimApplication

fun main(args: Array<String>) {
    runApplication<AdSimApplication>(*args)
}
