// tag::consumer[]
package shopping.analytics;

import org.apache.pekko.Done;
import org.apache.pekko.actor.typed.ActorSystem;
import org.apache.pekko.kafka.CommitterSettings;
import org.apache.pekko.kafka.ConsumerSettings;
import org.apache.pekko.kafka.Subscriptions;
import org.apache.pekko.kafka.javadsl.Committer;
import org.apache.pekko.kafka.javadsl.Consumer;
import org.apache.pekko.stream.RestartSettings;
import org.apache.pekko.stream.javadsl.RestartSource;
import com.google.protobuf.Any;
import com.google.protobuf.CodedInputStream;
import com.google.protobuf.InvalidProtocolBufferException;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.serialization.ByteArrayDeserializer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import shopping.cart.proto.CheckedOut;
import shopping.cart.proto.ItemAdded;
import shopping.cart.proto.ItemQuantityAdjusted;
import shopping.cart.proto.ItemRemoved;

class ShoppingCartEventConsumer {
  private static final Logger log = LoggerFactory.getLogger(ShoppingCartEventConsumer.class);

  static void init(ActorSystem<?> system) {
    String topic =
        system
            .settings()
            .config()
            .getString("shopping-analytics-service.shopping-cart-kafka-topic");
    ConsumerSettings<String, byte[]> consumerSettings =
        ConsumerSettings.create(system, new StringDeserializer(), new ByteArrayDeserializer())
            .withGroupId("shopping-cart-analytics");
    CommitterSettings committerSettings = CommitterSettings.create(system);

    Duration minBackoff = Duration.ofSeconds(1);
    Duration maxBackoff = Duration.ofSeconds(30);
    double randomFactor = 0.1;

    RestartSource // <1>
        .onFailuresWithBackoff(
            RestartSettings.create(minBackoff, maxBackoff, randomFactor),
            () -> {
              return Consumer.committableSource(
                      consumerSettings, Subscriptions.topics(topic)) // <2>
                  .mapAsync(
                      1,
                      msg -> handleRecord(msg.record()).thenApply(done -> msg.committableOffset()))
                  .via(Committer.flow(committerSettings)); // <3>
            })
        .run(system);
  }

  private static CompletionStage<Done> handleRecord(ConsumerRecord<String, byte[]> record)
      throws InvalidProtocolBufferException {
    byte[] bytes = record.value();
    Any x = Any.parseFrom(bytes); // <4>
    String typeUrl = x.getTypeUrl();
    CodedInputStream inputBytes = x.getValue().newCodedInput();
    try {
      switch (typeUrl) {
        case "shopping-cart-service/shoppingcart.ItemAdded":
          {
            ItemAdded event = ItemAdded.parseFrom(inputBytes);
            log.info(
                "ItemAdded: {} {} to cart {}",
                event.getQuantity(),
                event.getItemId(),
                event.getCartId());
            break;
          }
          // end::consumer[]
        case "shopping-cart-service/shoppingcart.ItemQuantityAdjusted":
          {
            ItemQuantityAdjusted event = ItemQuantityAdjusted.parseFrom(inputBytes);
            log.info(
                "ItemQuantityAdjusted: {} {} to cart {}",
                event.getQuantity(),
                event.getItemId(),
                event.getCartId());
            break;
          }
        case "shopping-cart-service/shoppingcart.ItemRemoved":
          {
            ItemRemoved event = ItemRemoved.parseFrom(inputBytes);
            log.info("ItemRemoved: {} removed from cart {}", event.getItemId(), event.getCartId());
            break;
          }
          // tag::consumer[]
        case "shopping-cart-service/shoppingcart.CheckedOut":
          {
            CheckedOut event = CheckedOut.parseFrom(inputBytes);
            log.info("CheckedOut: cart {} checked out", event.getCartId());
            break;
          }
        default:
          throw new IllegalArgumentException("unknown record type " + typeUrl);
      }
    } catch (Exception e) {
      log.error("Could not process event of type [{}]", typeUrl, e);
      // continue with next
    }
    return CompletableFuture.completedFuture(Done.getInstance());
  }
}
// end::consumer[]
