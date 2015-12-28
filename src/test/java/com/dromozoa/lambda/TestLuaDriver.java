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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

import com.amazonaws.services.lambda.runtime.ClientContext;
import com.amazonaws.services.lambda.runtime.CognitoIdentity;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.LambdaLogger;

import org.junit.Assert;
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
    try (ByteArrayInputStream inputStream = new ByteArrayInputStream("123".getBytes()); ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
      self.handleRequest(inputStream, outputStream, new MockContext());
      Assert.assertEquals("AWS-REQUEST-ID\nLOG-GROUP-NAME\nLOG-STREAM-NAME\nFUNCTION-NAME\nINVOKED-FUNCTION-ARN\n123\n", outputStream.toString());
    }
  }
}
