-- Copyright (C) 2015 Tomoyuki Fujimori <moyu@dromozoa.com>
--
-- This file is part of dromozoa-lambda.
--
-- dromozoa-lambda is free software: you can redistribute it and/or modify
-- it under the terms of the GNU General Public License as published by
-- the Free Software Foundation, either version 3 of the License, or
-- (at your option) any later version.
--
-- dromozoa-lambda is distributed in the hope that it will be useful,
-- but WITHOUT ANY WARRANTY; without even the implied warranty of
-- MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
-- GNU General Public License for more details.
--
-- You should have received a copy of the GNU General Public License
-- along with dromozoa-lambda.  If not, see <http://www.gnu.org/licenses/>.

print(os.getenv("AWS_REQUEST_ID"))
print(os.getenv("LOG_GROUP_NAME"))
print(os.getenv("LOG_STREAM_NAME"))
print(os.getenv("FUNCTION_NAME"))
print(os.getenv("INVOKED_FUNCTION_ARN"))
print(io.read("*a"))
