package com.dromozoa.lambda;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.Process;
import java.lang.ProcessBuilder;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.ArrayList;
import java.util.List;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestStreamHandler;

public class LuaHandler implements RequestStreamHandler {
  public static int pipe(InputStream inputStream, OutputStream outputStream) throws IOException {
    int result = 0;

    byte[] buffer = new byte[4096];
    while (true) {
      int n = inputStream.read(buffer, 0, buffer.length);
      System.out.println("n=" + n);
      if (n == -1) {
        break;
      }
      outputStream.write(buffer, 0, n);
      result += n;
    }

    System.out.println("result=" + result);
    return result;
  }

  public static class PipeTask implements Callable<Integer> {
    private InputStream inputStream_;
    private OutputStream outputStream_;

    PipeTask(InputStream inputStream, OutputStream outputStream) {
      inputStream_ = inputStream;
      outputStream_ = outputStream;
    }

    @Override
    public Integer call() throws IOException {
      return pipe(inputStream_, outputStream_);
    }
  }

  public void handleRequest(InputStream inputStream, OutputStream outputStream, Context context) throws IOException {
    File scriptFile = File.createTempFile("dromozoa-lambda", ".lua");
    scriptFile.deleteOnExit();

    try (
        FileOutputStream out = new FileOutputStream(scriptFile);
        InputStream in = LuaHandler.class.getClassLoader().getResourceAsStream("main.lua")
    ) {
      pipe(in, out);
    }

    ProcessBuilder processBuilder = new ProcessBuilder("lua", scriptFile.getAbsolutePath());
    Process process = processBuilder.start();

    // ExecutorService executorService = Executors.newFixedThreadPool(4);
    ExecutorService executorService = Executors.newFixedThreadPool(4);
    List<Future<Integer>> futures = new ArrayList<Future<Integer>>();
    // futures.add(executorService.submit(new PipeTask(inputStream, outputStream)));
    futures.add(executorService.submit(new PipeTask(inputStream, process.getOutputStream())));
    futures.add(executorService.submit(new PipeTask(process.getInputStream(), outputStream)));
    futures.add(executorService.submit(new PipeTask(process.getErrorStream(), System.err)));

    for (Future<Integer> future : futures) {
      try {
        System.out.println(future + "," + future.isCancelled() + "," + future.isDone());
        Integer v = future.get();
        System.out.println(v);
      } catch (InterruptedException e) {
        System.err.println(e);
      } catch (ExecutionException e) {
        System.err.println(e);
      }
    }

    executorService.shutdown();

    try {
      process.waitFor();
      int result = process.exitValue();
      System.out.println("result=" + result);
    } catch (InterruptedException e) {
      process.destroy();
    }
  }

  public static void main(String[] args) throws Exception {
    LuaHandler self = new LuaHandler();
    self.handleRequest(System.in, System.out, null);
  }
}
