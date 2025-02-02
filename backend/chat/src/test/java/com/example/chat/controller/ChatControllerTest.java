package com.example.chat.controller;

import static java.lang.Thread.sleep;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;

import com.example.chat.MongoTestContainerConfig;
import com.example.chat.RedisTestContainerConfig;
import com.example.controller.ChatController;
import com.example.domain.Message;
import com.example.domain.Topic;
import com.example.domain.User;
import com.example.dto.ChatMessageRequest;
import com.example.dto.ChatMessageResponse;
import com.example.dto.TopicCreateRequest;
import com.example.repository.ChatRepository;
import com.example.repository.TopicRepository;
import com.example.repository.UserRepository;
import com.example.service.ReportConsumer;
import com.example.service.TopicService;
import java.lang.reflect.Type;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import org.springframework.messaging.converter.MappingJackson2MessageConverter;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;
import org.springframework.web.socket.sockjs.client.SockJsClient;
import org.springframework.web.socket.sockjs.client.WebSocketTransport;


@ExtendWith({RedisTestContainerConfig.class, MongoTestContainerConfig.class})
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
class ChatControllerTest {

  @TestConfiguration
  static class TestRedisConfig {

    @Value("${spring.data.redis.host}")
    private String host;
    @Value("${spring.data.redis.port}")
    private int port;

    @Bean
    @Primary
    public RedisConnectionFactory testRedisConnectionFactory() {
      return new LettuceConnectionFactory(host, port);
    }

    @Bean
    @Primary
    public RedisMessageListenerContainer testRedisMessageListenerContainer(
      RedisConnectionFactory connectionFactory) {
      RedisMessageListenerContainer container = new RedisMessageListenerContainer();
      container.setConnectionFactory(connectionFactory);
      return container;
    }

    @Bean
    @Primary
    public RedisTemplate<String, Object> testRedisTemplate(
      RedisConnectionFactory connectionFactory) {
      RedisTemplate<String, Object> redisTemplate = new RedisTemplate<>();
      redisTemplate.setConnectionFactory(connectionFactory);
      redisTemplate.setKeySerializer(new StringRedisSerializer());
      redisTemplate.setValueSerializer(new Jackson2JsonRedisSerializer<>(String.class));
      return redisTemplate;
    }
  }

  @MockBean
  ReportConsumer reportConsumer;

  @LocalServerPort
  Integer port;

  @SpyBean
  ChatController chatController;

  @Autowired
  TopicService topicService;

  @Autowired
  TopicRepository topicRepository;

  @Autowired
  UserRepository userRepository;

  @Autowired
  ChatRepository chatRepository;

  WebSocketStompClient webSocketStompClient;
  StompSession session;

  @BeforeEach
  void init() throws ExecutionException, InterruptedException, TimeoutException {
    webSocketStompClient = new WebSocketStompClient(
      new SockJsClient(
        List.of(new WebSocketTransport(new StandardWebSocketClient()))));
    webSocketStompClient.setMessageConverter(new MappingJackson2MessageConverter());

    session = webSocketStompClient.connectAsync(
                                    String.format("ws://localhost:%d/ws", port),
                                    new StompSessionHandlerAdapter() {
                                    })
                                  .get(1, TimeUnit.SECONDS);
  }

  @AfterEach
  void clear() {
    userRepository.deleteAll();
    chatRepository.deleteAll();
    topicRepository.deleteAll();
  }

  @Nested
  @DisplayName("send 메서드는")
  class DescribeSend {

    @Nested
    @DisplayName("유효한 값이 전달되면")
    class ContextWithValidData {

      @Test
      @DisplayName("해당 토픽을 구독한 사용자들에게 메시지를 발행하고 메시지를 저장한다.")
      void ItPublishesMessage() throws InterruptedException {
        // given
        String topicId = "topicId";
        String subUri = String.format("/topic/%s", topicId);
        topicService.create(new TopicCreateRequest(topicId, LocalDateTime.now().plusMinutes(5)));

        ChatMessageRequest message = ChatMessageRequest.of("user1", "hi~");
        BlockingQueue<ChatMessageResponse> queue = new ArrayBlockingQueue<>(1);
        session.subscribe(subUri, new StompFrameHandler() {
          @Override
          public Type getPayloadType(StompHeaders headers) {
            return ChatMessageResponse.class;
          }

          @Override
          public void handleFrame(StompHeaders headers, Object payload) {
            queue.add((ChatMessageResponse) payload);
          }
        });

        // when
        session.send(String.format("/chat/%s/send", topicId), message);

        // then
        sleep(1000);
        List<Message> result = (List<Message>) chatRepository.findAll();
        assertThat(result).hasSize(1);
        ChatMessageResponse chatMessage = queue.poll(3, TimeUnit.SECONDS);
        assertThat(chatMessage.getSender()).isEqualTo(message.getSender());
        assertThat(chatMessage.getContent()).isEqualTo(message.getContent());
      }
    }

    @Nested
    @DisplayName("채팅 차단된 사용자라면")
    class ContextWithBlockedUser {

      @Test
      @DisplayName("handleException을 호출한다")
      void ItCallsHandleException() throws InterruptedException {
        // given
        String topicId = "topicId";
        String userName = "user1";
        userRepository.save(User.of(topicId, userName));

        ChatMessageRequest message = ChatMessageRequest.of(topicId, userName);

        // when
        session.send(String.format("/chat/%s/send", topicId), message);

        // then
        sleep(1000);
        verify(chatController, atLeastOnce()).handleException(any(Exception.class));
      }
    }

    @Nested
    @DisplayName("종료된 토픽이라면")
    class ContextWithClosedTopic {

      @Test
      @DisplayName("handleException을 호출한다")
      void ItCallsHandleException() throws InterruptedException {
        // given
        String topicId = "topicId";
        String userName = "user1";
        topicRepository.save(Topic.of(topicId, LocalDateTime.now().minusDays(1)));

        ChatMessageRequest message = ChatMessageRequest.of(topicId, userName);

        // when
        session.send(String.format("/chat/%s/send", topicId), message);

        // then
        sleep(1000);
        verify(chatController, atLeastOnce()).handleException(any(Exception.class));
      }
    }

    @Nested
    @DisplayName("메시지가 100자를 넘는다면")
    class ContextWithMessageLengthOver100 {

      @Test
      @DisplayName("handleException을 호출한다")
      void ItCallsHandleException() throws InterruptedException {
        // given
        String topicId = "topicId";
        ChatMessageRequest message = ChatMessageRequest.of("user1", "s".repeat(101));

        // when
        session.send(String.format("/chat/%s/send", topicId), message);

        // then
        sleep(1000);
        verify(chatController, atLeastOnce()).handleException(any(Exception.class));
      }
    }

    @Nested
    @DisplayName("메시지가 빈 값이라면")
    class ContextWithMessageEmpty {

      @ParameterizedTest
      @NullAndEmptySource
      @DisplayName("handleException을 호출한다")
      void ItCallsHandleException(String content) throws InterruptedException {
        // given
        String topicId = "topicId";
        ChatMessageRequest message = ChatMessageRequest.of("user1", content);

        // when
        session.send(String.format("/chat/%s/send", topicId), message);

        // then
        sleep(1000);
        verify(chatController, atLeastOnce()).handleException(any(Exception.class));
      }
    }

    @Nested
    @DisplayName("토픽 아이디가 null이라면")
    class ContextWithTopicIdNull {

      @Test
      @DisplayName("handleException을 호출한다")
      void ItCallsHandleException() throws InterruptedException {
        // given
        String topicId = null;
        ChatMessageRequest message = ChatMessageRequest.of("user1", "hello~~!!");

        // when, then
        session.send(String.format("/chat/%s/send", topicId), message);

        // then
        sleep(1000);
        verify(chatController, atLeastOnce()).handleException(any(Exception.class));
      }
    }

    @Nested
    @DisplayName("송신자 닉네임이 8자를 넘는다면")
    class ContextWithSenderLengthOver8 {

      @Test
      @DisplayName("handleException을 호출한다")
      void ItCallsHandleException() throws InterruptedException {
        // given
        String topicId = "topicId";
        ChatMessageRequest message = ChatMessageRequest.of("u".repeat(9), "message");

        // when
        session.send(String.format("/chat/%s/send", topicId), message);

        // then
        sleep(1000);
        verify(chatController, atLeastOnce()).handleException(any(Exception.class));
      }
    }

    @Nested
    @DisplayName("송신자 닉네임이 빈 값이라면")
    class ContextWithSenderEmpty {

      @ParameterizedTest
      @NullAndEmptySource
      @DisplayName("handleException을 호출한다")
      void ItCallsHandleException(String sender) throws InterruptedException {
        // given
        String topicId = "topicId";
        ChatMessageRequest message = ChatMessageRequest.of(sender, "message");

        // when
        session.send(String.format("/chat/%s/send", topicId), message);

        // then
        sleep(1000);
        verify(chatController, atLeastOnce()).handleException(any(Exception.class));
      }
    }
  }
}
