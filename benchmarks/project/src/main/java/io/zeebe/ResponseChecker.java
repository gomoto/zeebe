/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe;

import io.grpc.Status.Code;
import io.grpc.StatusRuntimeException;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ResponseChecker extends Thread {

  private static final Logger LOG = LoggerFactory.getLogger(ResponseChecker.class);

  private final BlockingQueue<Future<?>> futures;
  private volatile boolean shuttingDown = false;

  public ResponseChecker(BlockingQueue<Future<?>> futures) {
    this.futures = futures;
  }

  @Override
  public void run() {
    while (!shuttingDown) {
      try {
        futures.take().get();
      } catch (InterruptedException e) {
        // ignore and retry
      } catch (ExecutionException e) {
        final Throwable cause = e.getCause();
        if (cause instanceof StatusRuntimeException) {
          final StatusRuntimeException statusRuntimeException = (StatusRuntimeException) cause;
          if (statusRuntimeException.getStatus().getCode() != Code.RESOURCE_EXHAUSTED) {
            // we don't want to flood the log
            LOG.warn("Request failed", e);
          }
        }
      }
    }
  }

  public void close() {
    shuttingDown = true;
    interrupt();
  }
}
