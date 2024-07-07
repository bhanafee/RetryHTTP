# Add HTTP-awareness to resilience libraries

[Resilience4j](https://resilience4j.readme.io) provides extensive support for patterns including retries, bulkheads,
and circuit breakers. However, that library does not allow for direct dependency on HTTP APIs and therefore does not
include intelligent handling of HTTP response status codes or `Retry-After` headers. This package integrates with
resilience4j to add those features in the context of`jakarta.servlet.http.HttpServletResponse`.

## HTTP status code

At its simplest, some HTTP status codes indicate that the response is complete, so a retry is unnecessary. Other codes
indicate that a failure has occurred, and the client might be successful if it retries the request. This package
provides a `Predicate<HttpServletResponse>` that indicates which response codes warrant a retry. 

### Effect of idempotence

Not all requests are safe to retry after a failure. If the method has side effects and there was a failure on the
server, retries could make the problem worse. Retries should be attempted after a server failure only for idempotent
methods as described in section 4.2.2 of [RFC-7231](https://www.rfc-editor.org/rfc/rfc7231.html#section-4.2.2).

This package provides separate predicates for use with idempotent and non-idempotent functions. The predicates differ
only in their handling of 5xx response codes. The `501 Not Implemented` and `505 HTTP Version Not Supported` statuses
are never retried, because it is extremely unlikely that a retry would result in a different response.

### Handling expected next request

Some HTTP response codes carry an expectation that the client will react with an additional HTTP request . For example,
when a server responds with a `302` redirection the client is typically expected to issue a second request to location
specified in the redirect. The server is responding normally, so it should not be counted as a failure. Some HTTP
clients can be configured to follow redirections silently and automatically. However, this package **does** treat the
additional request as a retry. The reason for this choice is to enable the resilience library to defend against
problematic server behaviors.

One such problematic behavior is a redirect loop. In that condition, URL "A" responds with a redirect to URL "B," which
then redirects back to URL "A," and so on. Counting each redirection as a retry simplifies identifying and ending the
loop with a max retries limit.

Another problematic behavior is when the server response is too slow and the client would not have time to make the
second request and receive a response before running out of time. Rather than waiting for the time to expire, the client
can skip the "retry" and move directly to fallback logic.

## Retry-After header

Servers may send a `Retry-After` header to indicate the earliest that a client should attempt another request as
described in section 7.1.3 of [RFC-7231](https://datatracker.ietf.org/doc/html/rfc7231#section-7.1.3). This package
provides two forms of support for the `Retry-After` header: a predicate that can be used to prevent a retry if the
wait interval would be too long, and a bulkhead function that can adjust the wait interval to heed the header.

### Retry predicate

A `Predicate<HttpServletResponse>` can be configured to block retries if heeding the `Retry-After` header would result
in an unacceptably long delay. Setting a predicate is important. The header can specify an arbitrary date that could
be minutes, hours or even years in the future, while the client may be willing to wait at most only a few seconds.

### Wait interval bulkhead

A wait interval bulkhead is set via an`io.github.resilience4j.core.IntervalBiFunction`. It can be applied directly
by returning the wait duration, or it can be applied as an adjustment to another `IntervalBiFunction` to ensure that
the wait heeds the `Retry-After` header.

The bulkhead function applies the wait interval without considering the length of the delay. To guard against excessive
waits, the bulkhead should be applied in conjunction with a predicate that imposes a limit. The initializers provided by 
`com.maybeitssquid.retry.resilience4j.Retry` that accept a `Duration` include both a bulkhead and predicate.

### Parsing `Retry-After` header

The `Retry-After` header allows the server to request the client delay an integer number of seconds, or request the
client wait until a specific point in time before making another request. If the request is for a specific point in
time, it allows for several different date formats:

* IMF-fixdate
* RFC-850 date
* ANSI C `asctime()`

This package accepts any of those formats. It allows for some small, unambiguous variations within those formats.

#### Selecting a clock

When the `Retry-After` header specifies a time, the client has to convert it into a wait interval. To do this, it
requires a clock. By default, it uses the clock supplied by `InstantSource.system()`. A different source can be
supplied when the parser is initialized.

#### Expanded headers

In addition to the format specified by RFC-7231, this package can be configured to allow two variations:

* The delay may use a decimal to specify a delay with more precision than an integer number of seconds.
* The date may use ISO-8601 format.

To enable these variations, use the `extended()` rather than `strict()` functions to create the parser.