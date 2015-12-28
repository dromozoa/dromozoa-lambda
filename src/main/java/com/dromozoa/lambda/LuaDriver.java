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
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestStreamHandler;

public class LuaDriver implements RequestStreamHandler {
  private static int copy(InputStream inputStream, OutputStream outputStream) throws IOException {
    int result = 0;
    byte[] buffer = new byte[256];
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

  private static class CopyTask implements Callable<Integer> {
    private InputStream inputStream_;
    private OutputStream outputStream_;
    private boolean closeOutputStream_;

    CopyTask(InputStream inputStream, OutputStream outputStream) {
      inputStream_ = inputStream;
      outputStream_ = outputStream;
    }

    CopyTask setCloseOutputStream() {
      closeOutputStream_ = true;
      return this;
    }

    @Override
    public Integer call() throws IOException {
      try {
        return copy(inputStream_, outputStream_);
      } finally {
        if (closeOutputStream_) {
          outputStream_.close();
        }
      }
    }
  }

  private static class ProcessTask implements Callable<Integer> {
    private Process process_;

    ProcessTask(Process process) {
      process_ = process;
    }

    @Override
    public Integer call() throws InterruptedException {
      try {
        process_.waitFor();
        return process_.exitValue();
      } catch (InterruptedException e) {
        process_.destroy();
        throw e;
      }
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
          copy(inputStream, outputStream);
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

  private static Integer getFuture(Future<Integer> future, Context context) {
    try {
      return future.get();
    } catch (Exception e) {
      e.printStackTrace();
    }
    return null;
  }

  public void handleRequest(InputStream inputStream, OutputStream outputStream, Context context) throws IOException {
    Properties properties = getResourceAsProperties("dromozoa-lambda.xml");
    String lua = properties.getProperty("lua", "lua");
    File script = getResourceAsFile(properties.getProperty("script", "main.lua"));

    ProcessBuilder processBuilder = new ProcessBuilder(lua, script.getAbsolutePath());
    Map<String, String> env = processBuilder.environment();
    for (Map.Entry<String, String> i : env.entrySet()) {
      System.out.println(i.getKey() + " " + i.getValue());
    }

    Process process = processBuilder.start();

    try (InputStream stdoutStream = process.getInputStream(); InputStream stderrStream = process.getErrorStream()) {
      OutputStream stdinStream = process.getOutputStream();

      ExecutorService executorService = Executors.newFixedThreadPool(4);
      Future<Integer> stdinFuture = executorService.submit(new CopyTask(inputStream, stdinStream).setCloseOutputStream());
      Future<Integer> stdoutFuture = executorService.submit(new CopyTask(stdoutStream, outputStream));
      Future<Integer> stderrFuture = executorService.submit(new CopyTask(stderrStream, System.err));
      Future<Integer> processFuture = executorService.submit(new ProcessTask(process));

      getFuture(stdinFuture, context);
      getFuture(stdoutFuture, context);
      getFuture(stderrFuture, context);
      getFuture(processFuture, context);
      executorService.shutdown();
    }
  }

  public static void main(String[] args) throws Exception {
    LuaDriver self = new LuaDriver();
    self.handleRequest(System.in, System.out, null);
  }
}
