package com.tempchat.server.service;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Service
public class RedisService {

    private final StringRedisTemplate redisTemplate;

    public RedisService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public void set(String key, String value, long expirationSeconds) {

        redisTemplate.opsForValue().set(
                key,
                value,
                Duration.ofSeconds(expirationSeconds)
        );
    }

    public String get(String key) {

        return redisTemplate.opsForValue().get(key);
    }

    public void delete(String key) {

        redisTemplate.delete(key);
    }

    public boolean exists(String key) {

        return Boolean.TRUE.equals(
                redisTemplate.hasKey(key)
        );
    }

    public void deleteByPattern(String pattern) {

        var keys = redisTemplate.keys(pattern);

        if (keys != null && !keys.isEmpty()) {
            redisTemplate.delete(keys);
        }
    }
}