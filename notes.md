# Initial thoughts:
- caching required to limit requests (5min ttl)
- thought about combination possibilities
- checked if the inverse is always true to save requests but it isn't
- can get all rates with each request

# Final answer:
If all rates are received with each request, and a 5 min ttl, then the maximum number of requests is:
24\*60/5=288 possible requests a day
As long as the ttl is greater than 87 seconds we are guaranteed to not hit the 1000 request limit.

**Note:** The lowest time to live is 87 seconds to stay within 1000 requests a day.

# Implementation notes:
- Error handler middleware is in place to provide proper JSON responses when things go wrong

# Instructions

build
sbt compile

test
sbt test

config
src/main/resources/application.conf

curl test
curl "http://localhost:8080/rates?from=USD&to=JPY"