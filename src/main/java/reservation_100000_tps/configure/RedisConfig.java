package reservation_100000_tps.configure;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

/**
 * Redis 설정
 *
 * 캐싱 및 분산 락을 위한 Redis 연동 설정
 */
@Configuration
@EnableCaching
public class RedisConfig {

    @Bean
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);

        // JSON 직렬화를 위한 ObjectMapper 설정
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

        // Key는 String, Value는 JSON으로 직렬화
        template.setKeySerializer(new StringRedisSerializer());
        template.setValueSerializer(new GenericJackson2JsonRedisSerializer(objectMapper));
        template.setHashKeySerializer(new StringRedisSerializer());
        template.setHashValueSerializer(new GenericJackson2JsonRedisSerializer(objectMapper));

        template.afterPropertiesSet();
        return template;
    }

    /**
     * Lua 스크립트 실행용 StringRedisTemplate
     *
     * Lua 스크립트의 KEYS와 ARGV는 모두 String으로 전달되어야 하므로
     * GenericJackson2JsonRedisSerializer 대신 StringRedisSerializer 사용
     */
    @Bean
    public StringRedisTemplate stringRedisTemplate(RedisConnectionFactory connectionFactory) {
        return new StringRedisTemplate(connectionFactory);
    }

    /**
     * 티켓 예약을 위한 Lua 스크립트
     *
     * getBit + setBit을 원자적으로 실행하여 네트워크 왕복 2번 -> 1번으로 최적화
     *
     * @return 1: 예약 성공, 0: 이미 점유됨
     */
    @Bean
    public RedisScript<Long> ticketReservationScript() {
        String script =
            "local bit = redis.call('getbit', KEYS[1], ARGV[1]) " +
            "if bit == 0 then " +
            "    redis.call('setbit', KEYS[1], ARGV[1], 1) " +
            "    return 1 " +
            "else " +
            "    return 0 " +
            "end";

        return RedisScript.of(script, Long.class);
    }
}
