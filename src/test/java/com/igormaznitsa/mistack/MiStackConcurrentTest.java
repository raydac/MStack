package com.igormaznitsa.mistack;

import static com.igormaznitsa.mistack.MiStack.allTags;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.igormaznitsa.mistack.impl.MiStackConcurrent;
import com.igormaznitsa.mistack.impl.MiStackItemImpl;
import com.igormaznitsa.mistack.impl.MiStackTagImpl;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

public class MiStackConcurrentTest extends AbstractMiStackTest {
  @Override
  MiStack<String> makeStack() {
    return new MiStackConcurrent<>();
  }

  @Override
  MiStack<String> makeStack(final String name) {
    return new MiStackConcurrent<>(name);
  }

  @Test
  public void testConcurrentUse() {
    final int ELEMENTS = 500_000;

    try (final MiStack<Integer> stack = new MiStackConcurrent<>()) {
      final int threads = (Runtime.getRuntime().availableProcessors() + 1) * 2;

      var latch = new CountDownLatch(threads);
      var barrier = new CyclicBarrier(threads);

      final AtomicLong successful = new AtomicLong();

      final Runnable monkey = () -> {
        final Set<MiStackTag> tag = MiStackTagImpl.tagsOf(Thread.currentThread().getName());
        assertEquals(0, stack.size(allTags(tag)));
        IntStream.range(0, ELEMENTS).forEach(x -> {
          if (System.nanoTime() % 2 == 0L) {
            Thread.yield();
          }
          stack.push(MiStackItemImpl.itemOf(x, tag));
        });
        assertEquals(ELEMENTS, stack.size(allTags(tag)));

        var iterator = stack.iterator(allTags(tag));
        for (int i = ELEMENTS - 1; i >= 0; i--) {
          assertTrue(iterator.hasNext());
          assertEquals(i, iterator.next().getValue());
          if (System.nanoTime() % 2 == 0L) {
            Thread.yield();
          }
          iterator.remove();
        }
        assertFalse(iterator.hasNext());

        assertEquals(0, stack.size(allTags(tag)));
        assertTrue(stack.isEmpty(allTags(tag)));
      };

      for (int i = 0; i < threads; i++) {
        final Thread testThread = new Thread(() -> {
          try {
            barrier.await();
            monkey.run();
            successful.incrementAndGet();
          } catch (Throwable ex) {
            ex.printStackTrace();
          } finally {
            latch.countDown();
          }
        }, "test-thread-" + i);
        testThread.setDaemon(true);
        testThread.start();
      }

      try {
        latch.await();
      } catch (InterruptedException ex) {
        Thread.currentThread().interrupt();
      }

      assertEquals(threads, successful.get());
    }
  }

}
