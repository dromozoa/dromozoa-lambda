// Copyright (C) 2015 Tomoyuki Fujimori <moyu@dromozoa.com>
//
// This file is part of dromozoa-lambda.
//
// dromozoa-lambda is free software: you can redistribute it and/or modify
// it under the terms of the GNU General Public License as published by
// the Free Software Foundation, either version 3 of the License, or
// (at your option) any later version.
//
// dromozoa-lambda is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
// GNU General Public License for more details.
//
// You should have received a copy of the GNU General Public License
// along with dromozoa-lambda.  If not, see <http://www.gnu.org/licenses/>.

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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
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

  public void handleRequest(InputStream inputStream, OutputStream outputStream, Context context) throws IOException {
    Properties properties = getResourceAsProperties("dromozoa-lambda.xml");
    String lua = properties.getProperty("lua", "lua");
    File script = getResourceAsFile(properties.getProperty("script", "main.lua"));

    ProcessBuilder processBuilder = new ProcessBuilder(lua, script.getAbsolutePath());
    Map<String, String> env = processBuilder.environment();
    env.put("AWS_REQUEST_ID", context.getAwsRequestId());
    env.put("LOG_GROUP_NAME", context.getLogGroupName());
    env.put("LOG_STREAM_NAME", context.getLogStreamName());
    env.put("FUNCTION_NAME", context.getFunctionName());
    env.put("INVOKED_FUNCTION_ARN", context.getInvokedFunctionArn());
    Process process = processBuilder.start();

    try (InputStream stdoutStream = process.getInputStream(); InputStream stderrStream = process.getErrorStream()) {
      OutputStream stdinStream = process.getOutputStream();
      ExecutorService executorService = Executors.newFixedThreadPool(4);
      List<Future<Integer>> futures = new ArrayList<Future<Integer>>();
      futures.add(executorService.submit(new CopyTask(inputStream, stdinStream).setCloseOutputStream()));
      futures.add(executorService.submit(new CopyTask(stdoutStream, outputStream)));
      futures.add(executorService.submit(new CopyTask(stderrStream, System.err)));
      futures.add(executorService.submit(new ProcessTask(process)));
      for (Future<Integer> future : futures) {
        try {
          future.get();
        } catch (Exception e) {
          e.printStackTrace();
        }
      }
      executorService.shutdown();
    }
  }
}
