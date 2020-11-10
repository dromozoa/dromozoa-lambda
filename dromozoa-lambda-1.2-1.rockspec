package = "dromozoa-lambda"
version = "1.2-1"
source = {
  url = "https://github.com/dromozoa/dromozoa-lambda/archive/v1.2.tar.gz";
  file = "dromozoa-lambda-1.2.tar.gz";
}
description = {
  summary = "Lua driver for AWS Lambda";
  license = "GPL-3";
  homepage = "https://github.com/dromozoa/dromozoa-lambda/";
  maintainer = "Tomoyuki Fujimori <moyu@dromozoa.com>";
}
dependencies = {
  "dromozoa-commons";
  "dromozoa-amalgamate";
}
build = {
  type = "builtin";
  modules = {};
  install = {
    bin = {
      ["dromozoa-lambda"] = "dromozoa-lambda";
      ["dromozoa-lambda.jar"] = "dromozoa-lambda.jar";
    };
  };
}
