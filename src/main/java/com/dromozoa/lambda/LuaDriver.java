package com.dromozoa.lambda;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.Process;
import java.lang.ProcessBuilder;
import java.net.URL;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestStreamHandler;

public class LuaDriver implements RequestStreamHandler {
  private static int pipe(InputStream inputStream, OutputStream outputStream) throws IOException {
    int result = 0;

    byte[] buffer = new byte[4096];
    while (true) {
      int n = inputStream.read(buffer, 0, buffer.length);
      if (n == -1) {
        break;
      }
      outputStream.write(buffer, 0, n);
      result += n;
    }

    return result;
  }

  private static class PipeTask implements Callable<Integer> {
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

  private static URL getResource(String name) {
    return LuaDriver.class.getClassLoader().getResource(name);
  }

  private static InputStream getResourceAsStream(String name) {
    return LuaDriver.class.getClassLoader().getResourceAsStream(name);
  }

  private static File getResourceAsFile(String name) throws IOException {
    URL url = getResource(name);
    if (url != null) {
      if (url.getProtocol().equals("file")) {
        return new File(url.getPath());
      } else {
        File file = File.createTempFile("dromozoa-lambda", null);
        file.deleteOnExit();
        try (InputStream inputStream = getResourceAsStream(name); OutputStream outputStream = new FileOutputStream(file)) {
          pipe(inputStream, outputStream);
        }
        return file;
      }
    }
    return null;
  }

  private static Properties getResourceAsProperties(String name) throws IOException {
    Properties properties = new Properties();
    try (InputStream inputStream = getResourceAsStream(name)) {
      if (name.toLowerCase().endsWith(".xml")) {
        properties.loadFromXML(inputStream);
      } else {
        properties.load(inputStream);
      }
    }
    return properties;
  }

  public void handleRequest(InputStream inputStream, OutputStream outputStream, Context context) throws IOException {
    Properties properties = getResourceAsProperties("dromozoa-lambda.xml");
    String lua = properties.getProperty("lua", "lua");
    File script = getResourceAsFile(properties.getProperty("script", "main.lua"));

    ProcessBuilder processBuilder = new ProcessBuilder(lua, script.getAbsolutePath());
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
        Integer v = future.get();
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
    LuaDriver self = new LuaDriver();
    self.handleRequest(System.in, System.out, null);
  }
}
