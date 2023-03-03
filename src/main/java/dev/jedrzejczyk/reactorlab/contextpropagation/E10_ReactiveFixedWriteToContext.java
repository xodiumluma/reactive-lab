package dev.jedrzejczyk.reactorlab.contextpropagation;

import io.micrometer.context.ContextRegistry;
import reactor.core.observability.DefaultSignalListener;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.context.Context;

import java.time.Duration;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ThreadLocalRandom;

public class E10_ReactiveFixedWriteToContext {

	private static final ThreadLocal<Long> CORRELATION_ID = new ThreadLocal<>();

	public static void main(String[] args) throws InterruptedException, ExecutionException {

		ContextRegistry.getInstance()
				.registerThreadLocalAccessor("CORRELATION_ID",
						CORRELATION_ID::get, CORRELATION_ID::set, CORRELATION_ID::remove);

		Mono<Void> requestHandler = handleRequest();

		Thread subscriberThread = new Thread(requestHandler::block);
		subscriberThread.start();
		subscriberThread.join();
	}

	static Mono<Void> handleRequest() {
		// initRequest(); -- no write to ThreadLocal
		log("Assembling the chain");

		return Mono.just("test-product")
				.delayElement(Duration.ofMillis(1))
				.flatMap(product ->
						Flux.concat(
										addProduct(product),
										notifyShop(product))
								.then())
				.contextWrite(Context.of("CORRELATION_ID", correlationId()));
	}

	static void initRequest() {
		CORRELATION_ID.set(correlationId());
	}

	private static long correlationId() {
		return Math.abs(ThreadLocalRandom.current().nextLong());
	}

	static Mono<Void> addProduct(String productName) {
		return Mono.<Void>empty()
				.tap(() -> new DefaultSignalListener<>() {
					@Override
					public void doOnComplete() throws Throwable {
						log("Adding product: " + productName);
					}
				});
	}

	static Mono<Boolean> notifyShop(String productName) {
		return Mono.just(true)
				.handle((result, sink) -> {
					log("Notifying shop about: " + productName);
					sink.next(result);
				});
	}

	static void log(String message) {
		String threadName = Thread.currentThread().getName();
		String threadNameTail = threadName.substring(Math.max(0, threadName.length() - 10));
		System.out.printf("[%10s][%20s] %s%n",
				threadNameTail, CORRELATION_ID.get(), message);
	}
}
