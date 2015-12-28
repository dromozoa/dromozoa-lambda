package com.dromozoa.lambda;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.IOException;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.CognitoIdentity;
import com.amazonaws.services.lambda.runtime.ClientContext;
import com.amazonaws.services.lambda.runtime.LambdaLogger;

import org.junit.Test;

public class TestLuaDriver {
  private static class MockContext implements Context {
    long timeLimit_;

    MockContext() {
      timeLimit_ = System.currentTimeMillis() + 15000;
    }

    public String getAwsRequestId() {
      return "AWS-REQUEST-ID";
    }

    public String getLogGroupName() {
      return "LOG-GROUP-NAME";
    }

    public String getLogStreamName() {
      return "LOG-STREAM-NAME";
    }

    public String getFunctionName() {
      return "FUNCTION-NAME";
    }

    public String getFunctionVersion() {
      return "FUNCTION-VERSION";
    }

    public String getInvokedFunctionArn() {
      return "INVOKED-FUNCTION-ARN";
    }

    public CognitoIdentity getIdentity() {
      return null;
    }

    public ClientContext getClientContext() {
      return null;
    }

    public int getRemainingTimeInMillis() {
      return (int)(timeLimit_ - System.currentTimeMillis());
    }

    public int getMemoryLimitInMB() {
      return 512;
    }

    public LambdaLogger getLogger() {
      return null;
    }
  }

  @Test
  public void test() throws IOException {
    LuaDriver self = new LuaDriver();
    try (InputStream inputStream = new ByteArrayInputStream("123".getBytes())) {
      self.handleRequest(inputStream, System.out, new MockContext());
    }
  }
}
