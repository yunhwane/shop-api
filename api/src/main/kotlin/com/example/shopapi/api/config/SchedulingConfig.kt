package com.example.shopapi.api.config

import org.springframework.context.annotation.Configuration
import org.springframework.scheduling.annotation.EnableScheduling

/** 만료 데이터 정리 배치를 위해 켠다(ADR 0010). */
@Configuration
@EnableScheduling
class SchedulingConfig
