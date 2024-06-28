package net.samitkumar.voting_processor;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.EventListener;
import org.springframework.data.annotation.Id;
import org.springframework.data.redis.connection.ReactiveRedisConnectionFactory;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;
import org.springframework.data.repository.ListCrudRepository;

import java.time.LocalDateTime;

@SpringBootApplication
@RequiredArgsConstructor
@Slf4j
public class VotingProcessorApplication {

	final ReactiveRedisTemplate<String,Vote> reactiveRedisTemplate;
	final VotesRepository votesRepository;

	@Value("${spring.data.redis.channel-name}")
	private String channelName;

	public static void main(String[] args) {
		SpringApplication.run(VotingProcessorApplication.class, args);
	}

	@EventListener
	public void onApplicationEvent(ApplicationReadyEvent event) {
		reactiveRedisTemplate
				.listenToChannel(channelName)
				.doOnNext(message -> log.info("[*] Received Message: {}", message))
				.map(message -> {
					var vote = message.getMessage();
					var votes = new Votes(null, vote.optionId(), vote.id(), LocalDateTime.now());
					return votesRepository.save(votes);
				})
				.doOnNext(db -> log.info("dbMessage {}", db))
				.subscribe();
	}


}

record Vote(String id, String optionId) {}

@Table("votes")
record Votes(@Id Long id, @Column("option_id") String optionId, @Column("user_id") String userId, @Column("created_at") LocalDateTime createdAt) {}
interface VotesRepository extends ListCrudRepository<Votes, Long> {}

@Configuration
class RedisConfig {

	@Bean
	public ReactiveRedisTemplate<String, Vote> reactiveRedisTemplate(ReactiveRedisConnectionFactory factory) {

		Jackson2JsonRedisSerializer<Vote> serializer = new Jackson2JsonRedisSerializer<>(Vote.class);
		RedisSerializationContext.RedisSerializationContextBuilder<String, Vote> builder =
				RedisSerializationContext.newSerializationContext(new StringRedisSerializer());
		RedisSerializationContext<String, Vote> context = builder.value(serializer).build();
		return new ReactiveRedisTemplate<>(factory, context);

		/*
		//OR
		RedisSerializationContext<String, Vote> serializationContext = RedisSerializationContext
				.<String, Vote>newSerializationContext(new StringRedisSerializer())
				.key(new StringRedisSerializer())
				.value(new Jackson2JsonRedisSerializer<>(Vote.class))
				.hashKey(new Jackson2JsonRedisSerializer<>(String.class))
				.hashValue(new Jackson2JsonRedisSerializer<>(Vote.class))
				.build();

		return new ReactiveRedisTemplate<>(factory, serializationContext);*/
	}
}